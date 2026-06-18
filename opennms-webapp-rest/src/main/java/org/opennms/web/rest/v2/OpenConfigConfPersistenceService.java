/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.web.rest.v2;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.opennms.features.scv.api.Credentials;
import org.opennms.features.scv.api.SecureCredentialsVault;
import org.opennms.netmgt.dao.api.OpenConfigSubscriptionProfileDao;
import org.opennms.netmgt.dao.api.OpenConfigTargetDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.model.OpenConfigSubscriptionProfile;
import org.opennms.netmgt.model.OpenConfigTarget;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.web.rest.v2.model.OpenConfigProfileDto;
import org.opennms.web.rest.v2.model.OpenConfigTargetDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional CRUD for OpenConfig subscription profiles and targets (NMS-19857). Credentials are
 * written to the Secure Credentials Vault (only the alias is stored in the database). After any
 * mutation a {@code reloadDaemonConfig} event is emitted for telemetryd, which re-materializes the
 * database-backed OpenConfig configuration on restart.
 */
@Service
@Transactional
public class OpenConfigConfPersistenceService {

    private static final Logger LOG = LoggerFactory.getLogger(OpenConfigConfPersistenceService.class);

    private static final String TELEMETRYD_DAEMON_NAME = "Telemetryd";

    @Autowired
    private OpenConfigSubscriptionProfileDao profileDao;

    @Autowired
    private OpenConfigTargetDao targetDao;

    @Autowired
    private SecureCredentialsVault secureCredentialsVault;

    @Autowired
    private EventForwarder eventForwarder;

    // ---- Profiles ----

    @Transactional(readOnly = true)
    public List<OpenConfigProfileDto> listProfiles() {
        return profileDao.findAll().stream().map(this::toProfileDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OpenConfigProfileDto getProfile(final Integer id) {
        final OpenConfigSubscriptionProfile profile = profileDao.get(id);
        return profile == null ? null : toProfileDto(profile);
    }

    public Integer createProfile(final OpenConfigProfileDto dto, final String username) {
        validateProfile(dto, null);
        final Date now = new Date();
        final OpenConfigSubscriptionProfile profile = new OpenConfigSubscriptionProfile();
        applyProfile(dto, profile);
        profile.setCreatedTime(now);
        profile.setLastModified(now);
        profile.setUploadedBy(username);
        final Integer id = (Integer) profileDao.save(profile);
        LOG.info("Created OpenConfig profile '{}' (id={}).", profile.getName(), id);
        triggerReload();
        return id != null ? id : profile.getId();
    }

    public void updateProfile(final Integer id, final OpenConfigProfileDto dto, final String username) {
        final OpenConfigSubscriptionProfile profile = requireProfile(id);
        validateProfile(dto, id);
        applyProfile(dto, profile);
        profile.setLastModified(new Date());
        profileDao.saveOrUpdate(profile);
        LOG.info("Updated OpenConfig profile '{}' (id={}).", profile.getName(), id);
        triggerReload();
    }

    public void deleteProfiles(final List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No profile ids provided.");
        }
        for (final Integer id : ids) {
            final OpenConfigSubscriptionProfile profile = profileDao.get(id);
            if (profile != null) {
                // Remove any vault aliases owned by this profile's targets.
                for (final OpenConfigTarget target : targetDao.findAllByProfile(id)) {
                    deleteCredentialAliases(target);
                }
                profileDao.delete(profile);
            }
        }
        LOG.info("Deleted OpenConfig profiles {}.", ids);
        triggerReload();
    }

    public void setProfilesEnabled(final List<Integer> ids, final boolean enabled) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No profile ids provided.");
        }
        profileDao.updateEnabledFlag(ids, enabled);
        triggerReload();
    }

    // ---- Targets ----

    @Transactional(readOnly = true)
    public List<OpenConfigTargetDto> listTargets(final Integer profileId) {
        requireProfile(profileId);
        return targetDao.findAllByProfile(profileId).stream().map(this::toTargetDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OpenConfigTargetDto getTarget(final Integer profileId, final Integer targetId) {
        final OpenConfigTarget target = targetDao.findByProfileIdAndId(profileId, targetId);
        return target == null ? null : toTargetDto(target);
    }

    public Integer createTarget(final Integer profileId, final OpenConfigTargetDto dto, final String username) {
        final OpenConfigSubscriptionProfile profile = requireProfile(profileId);
        validateTarget(dto);
        final Date now = new Date();
        final OpenConfigTarget target = new OpenConfigTarget();
        target.setProfile(profile);
        applyTarget(dto, target);
        target.setCreatedTime(now);
        target.setLastModified(now);
        target.setUploadedBy(username);
        targetDao.save(target);
        storeCredentials(target, dto);
        targetDao.saveOrUpdate(target);
        LOG.info("Created OpenConfig target '{}' (id={}) on profile {}.", target.getName(), target.getId(), profileId);
        triggerReload();
        return target.getId();
    }

    public void updateTarget(final Integer profileId, final Integer targetId, final OpenConfigTargetDto dto, final String username) {
        final OpenConfigTarget target = requireTarget(profileId, targetId);
        validateTarget(dto);
        applyTarget(dto, target);
        target.setLastModified(new Date());
        storeCredentials(target, dto);
        targetDao.saveOrUpdate(target);
        LOG.info("Updated OpenConfig target '{}' (id={}).", target.getName(), targetId);
        triggerReload();
    }

    public void deleteTargets(final Integer profileId, final List<Integer> ids) {
        requireProfile(profileId);
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No target ids provided.");
        }
        final List<OpenConfigTarget> toDelete = new ArrayList<>();
        for (final Integer id : ids) {
            final OpenConfigTarget target = targetDao.findByProfileIdAndId(profileId, id);
            if (target != null) {
                deleteCredentialAliases(target);
                toDelete.add(target);
            }
        }
        targetDao.deleteAll(toDelete);
        LOG.info("Deleted OpenConfig targets {} on profile {}.", ids, profileId);
        triggerReload();
    }

    public void setTargetsEnabled(final Integer profileId, final List<Integer> ids, final boolean enabled) {
        requireProfile(profileId);
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No target ids provided.");
        }
        targetDao.updateTargetEnabledFlag(profileId, ids, enabled);
        triggerReload();
    }

    // ---- Helpers ----

    private void triggerReload() {
        try {
            final EventBuilder eb = new EventBuilder(EventConstants.RELOAD_DAEMON_CONFIG_UEI, "ReST");
            eb.addParam(EventConstants.PARM_DAEMON_NAME, TELEMETRYD_DAEMON_NAME);
            eventForwarder.sendNow(eb.getEvent());
        } catch (final Exception e) {
            LOG.warn("Failed to emit reloadDaemonConfig event for {}.", TELEMETRYD_DAEMON_NAME, e);
        }
    }

    private void storeCredentials(final OpenConfigTarget target, final OpenConfigTargetDto dto) {
        if (dto.getUsername() == null && dto.getPassword() == null) {
            return; // nothing to change
        }
        final String alias = "openconfig-target-" + target.getId();
        secureCredentialsVault.setCredentials(alias,
                new Credentials(dto.getUsername(), dto.getPassword()));
        target.setCredentialRef(alias);
        LOG.debug("Stored OpenConfig credentials for target {} under vault alias '{}'.", target.getId(), alias);
    }

    private void deleteCredentialAliases(final OpenConfigTarget target) {
        if (target.getCredentialRef() != null && !target.getCredentialRef().isBlank()) {
            try {
                secureCredentialsVault.deleteCredentials(target.getCredentialRef());
            } catch (final Exception e) {
                LOG.warn("Failed to delete vault alias '{}' for target {}.", target.getCredentialRef(), target.getId(), e);
            }
        }
    }

    private OpenConfigSubscriptionProfile requireProfile(final Integer id) {
        final OpenConfigSubscriptionProfile profile = id == null ? null : profileDao.get(id);
        if (profile == null) {
            throw new IllegalArgumentException("OpenConfig profile not found: " + id);
        }
        return profile;
    }

    private OpenConfigTarget requireTarget(final Integer profileId, final Integer targetId) {
        final OpenConfigTarget target = targetDao.findByProfileIdAndId(profileId, targetId);
        if (target == null) {
            throw new IllegalArgumentException("OpenConfig target not found: " + targetId + " on profile " + profileId);
        }
        return target;
    }

    private void validateProfile(final OpenConfigProfileDto dto, final Integer existingId) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Profile name is required.");
        }
        final OpenConfigSubscriptionProfile byName = profileDao.findByName(dto.getName().trim());
        if (byName != null && !byName.getId().equals(existingId)) {
            throw new IllegalArgumentException("A profile named '" + dto.getName() + "' already exists.");
        }
    }

    private void validateTarget(final OpenConfigTargetDto dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Target name is required.");
        }
        if (dto.getMatchType() != null
                && !"FILTER".equalsIgnoreCase(dto.getMatchType())
                && !"NODES".equalsIgnoreCase(dto.getMatchType())) {
            throw new IllegalArgumentException("matchType must be FILTER or NODES.");
        }
    }

    private void applyProfile(final OpenConfigProfileDto dto, final OpenConfigSubscriptionProfile profile) {
        profile.setName(dto.getName().trim());
        profile.setDescription(dto.getDescription());
        if (dto.getTransport() != null) {
            profile.setTransport(dto.getTransport());
        }
        if (dto.getEncoding() != null) {
            profile.setEncoding(dto.getEncoding());
        }
        profile.setSampleInterval(dto.getSampleInterval());
        profile.setPaths(dto.getPaths());
        if (dto.getOrigin() != null) {
            profile.setOrigin(dto.getOrigin());
        }
        if (dto.getRrdStep() != null) {
            profile.setRrdStep(dto.getRrdStep());
        }
        profile.setRrdRras(dto.getRrdRras());
        profile.setModelSet(dto.getModelSet());
        if (dto.getEnabled() != null) {
            profile.setEnabled(dto.getEnabled());
        }
    }

    private void applyTarget(final OpenConfigTargetDto dto, final OpenConfigTarget target) {
        target.setName(dto.getName().trim());
        if (dto.getMatchType() != null) {
            target.setMatchType(dto.getMatchType().toUpperCase());
        }
        target.setFilterRule(dto.getFilterRule());
        target.setNodeIds(dto.getNodeIds());
        target.setPort(dto.getPort());
        if (dto.getTlsRef() != null) {
            target.setTlsRef(dto.getTlsRef());
        }
        if (dto.getEnabled() != null) {
            target.setEnabled(dto.getEnabled());
        }
    }

    private OpenConfigProfileDto toProfileDto(final OpenConfigSubscriptionProfile profile) {
        final OpenConfigProfileDto dto = new OpenConfigProfileDto();
        dto.setId(profile.getId());
        dto.setName(profile.getName());
        dto.setDescription(profile.getDescription());
        dto.setTransport(profile.getTransport());
        dto.setEncoding(profile.getEncoding());
        dto.setSampleInterval(profile.getSampleInterval());
        dto.setPaths(profile.getPaths());
        dto.setOrigin(profile.getOrigin());
        dto.setRrdStep(profile.getRrdStep());
        dto.setRrdRras(profile.getRrdRras());
        dto.setModelSet(profile.getModelSet());
        dto.setEnabled(profile.getEnabled());
        dto.setUploadedBy(profile.getUploadedBy());
        return dto;
    }

    private OpenConfigTargetDto toTargetDto(final OpenConfigTarget target) {
        final OpenConfigTargetDto dto = new OpenConfigTargetDto();
        dto.setId(target.getId());
        dto.setProfileId(target.getProfile() != null ? target.getProfile().getId() : null);
        dto.setName(target.getName());
        dto.setMatchType(target.getMatchType());
        dto.setFilterRule(target.getFilterRule());
        dto.setNodeIds(target.getNodeIds());
        dto.setPort(target.getPort());
        dto.setCredentialRef(target.getCredentialRef());
        dto.setTlsRef(target.getTlsRef());
        dto.setEnabled(target.getEnabled());
        // username/password intentionally omitted (write-only).
        return dto;
    }
}
