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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opennms.netmgt.dao.api.YangModelSetDao;
import org.opennms.netmgt.dao.api.YangModuleDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.model.YangModelSet;
import org.opennms.netmgt.model.YangModule;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.web.rest.v2.model.YangModelSetDto;
import org.opennms.web.rest.v2.model.YangModuleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional CRUD for the YANG model registry (NMS-19857, Phase 2): modules (with their source)
 * and the model sets that bundle them. Module metadata (name/revision/namespace/openconfig-version)
 * is parsed from the uploaded {@code .yang} text. After changes a {@code reloadDaemonConfig} event is
 * emitted so telemetryd rebuilds any affected schema contexts.
 */
@Service
@Transactional
public class YangRegistryPersistenceService {

    private static final Logger LOG = LoggerFactory.getLogger(YangRegistryPersistenceService.class);
    private static final String TELEMETRYD_DAEMON_NAME = "Telemetryd";

    private static final Pattern MODULE_NAME = Pattern.compile("(?m)^\\s*(?:sub)?module\\s+([A-Za-z0-9_.-]+)");
    private static final Pattern NAMESPACE = Pattern.compile("namespace\\s+\"([^\"]+)\"");
    private static final Pattern REVISION = Pattern.compile("revision\\s+\"?(\\d{4}-\\d{2}-\\d{2})\"?");
    private static final Pattern OC_VERSION = Pattern.compile("oc-ext:openconfig-version\\s+\"([^\"]+)\"");

    @Autowired
    private YangModuleDao yangModuleDao;

    @Autowired
    private YangModelSetDao yangModelSetDao;

    @Autowired
    private EventForwarder eventForwarder;

    // ---- Modules ----

    @Transactional(readOnly = true)
    public List<YangModuleDto> listModules() {
        return yangModuleDao.findAll().stream().map(m -> toModuleDto(m, false)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public YangModuleDto getModule(final Integer id) {
        final YangModule module = yangModuleDao.get(id);
        return module == null ? null : toModuleDto(module, true);
    }

    /** Create (or update-in-place by name+revision) a module from its source. */
    public Integer createModule(final YangModuleDto dto, final String username) {
        if (dto == null || dto.getContent() == null || dto.getContent().isBlank()) {
            throw new IllegalArgumentException("Module content is required.");
        }
        final String content = dto.getContent();
        final String name = firstMatch(MODULE_NAME, content);
        if (name == null) {
            throw new IllegalArgumentException("Could not find a 'module'/'submodule' declaration in the uploaded YANG.");
        }
        final String revision = firstMatch(REVISION, content);

        YangModule module = yangModuleDao.findByNameAndRevision(name, revision);
        if (module == null) {
            module = new YangModule();
            module.setName(name);
            module.setRevision(revision);
            module.setCreatedTime(new Date());
            module.setUploadedBy(username);
        }
        module.setNamespace(firstMatch(NAMESPACE, content));
        module.setOpenconfigVersion(firstMatch(OC_VERSION, content));
        module.setSourceFilename(dto.getSourceFilename());
        module.setContent(content);
        final Integer id = (Integer) yangModuleDao.save(module);
        LOG.info("Stored YANG module '{}' (revision={}).", name, revision);
        triggerReload();
        return id != null ? id : module.getId();
    }

    public void deleteModules(final List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No module ids provided.");
        }
        for (final Integer id : ids) {
            final YangModule module = yangModuleDao.get(id);
            if (module != null) {
                yangModuleDao.delete(module);
            }
        }
        triggerReload();
    }

    // ---- Model sets ----

    @Transactional(readOnly = true)
    public List<YangModelSetDto> listModelSets() {
        return yangModelSetDao.findAll().stream().map(this::toModelSetDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public YangModelSetDto getModelSet(final Integer id) {
        final YangModelSet set = yangModelSetDao.get(id);
        return set == null ? null : toModelSetDto(set);
    }

    public Integer createModelSet(final YangModelSetDto dto, final String username) {
        validateModelSet(dto, null);
        final YangModelSet set = new YangModelSet();
        set.setCreatedTime(new Date());
        applyModelSet(dto, set);
        final Integer id = (Integer) yangModelSetDao.save(set);
        LOG.info("Created YANG model set '{}' with {} module(s).", set.getName(), set.getModules().size());
        triggerReload();
        return id != null ? id : set.getId();
    }

    public void updateModelSet(final Integer id, final YangModelSetDto dto, final String username) {
        final YangModelSet set = requireModelSet(id);
        validateModelSet(dto, id);
        applyModelSet(dto, set);
        set.setLastModified(new Date());
        yangModelSetDao.saveOrUpdate(set);
        triggerReload();
    }

    public void deleteModelSets(final List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No model set ids provided.");
        }
        for (final Integer id : ids) {
            final YangModelSet set = yangModelSetDao.get(id);
            if (set != null) {
                yangModelSetDao.delete(set);
            }
        }
        triggerReload();
    }

    // ---- Helpers ----

    private void applyModelSet(final YangModelSetDto dto, final YangModelSet set) {
        set.setName(dto.getName().trim());
        set.setVersion(dto.getVersion());
        set.setDescription(dto.getDescription());
        if (dto.getEnabled() != null) {
            set.setEnabled(dto.getEnabled());
        }
        final Set<YangModule> modules = new LinkedHashSet<>();
        if (dto.getModuleIds() != null) {
            for (final Integer moduleId : dto.getModuleIds()) {
                final YangModule module = yangModuleDao.get(moduleId);
                if (module != null) {
                    modules.add(module);
                }
            }
        }
        set.setModules(modules);
    }

    private void validateModelSet(final YangModelSetDto dto, final Integer existingId) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Model set name is required.");
        }
        final YangModelSet byName = yangModelSetDao.findByName(dto.getName().trim());
        if (byName != null && !byName.getId().equals(existingId)) {
            throw new IllegalArgumentException("A model set named '" + dto.getName() + "' already exists.");
        }
    }

    private YangModelSet requireModelSet(final Integer id) {
        final YangModelSet set = id == null ? null : yangModelSetDao.get(id);
        if (set == null) {
            throw new IllegalArgumentException("YANG model set not found: " + id);
        }
        return set;
    }

    private void triggerReload() {
        try {
            final EventBuilder eb = new EventBuilder(EventConstants.RELOAD_DAEMON_CONFIG_UEI, "ReST");
            eb.addParam(EventConstants.PARM_DAEMON_NAME, TELEMETRYD_DAEMON_NAME);
            eventForwarder.sendNow(eb.getEvent());
        } catch (final Exception e) {
            LOG.warn("Failed to emit reloadDaemonConfig event for {}.", TELEMETRYD_DAEMON_NAME, e);
        }
    }

    private static String firstMatch(final Pattern pattern, final String text) {
        final Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private YangModuleDto toModuleDto(final YangModule module, final boolean includeContent) {
        final YangModuleDto dto = new YangModuleDto();
        dto.setId(module.getId());
        dto.setName(module.getName());
        dto.setRevision(module.getRevision());
        dto.setNamespace(module.getNamespace());
        dto.setOpenconfigVersion(module.getOpenconfigVersion());
        dto.setSourceFilename(module.getSourceFilename());
        if (includeContent) {
            dto.setContent(module.getContent());
        }
        return dto;
    }

    private YangModelSetDto toModelSetDto(final YangModelSet set) {
        final YangModelSetDto dto = new YangModelSetDto();
        dto.setId(set.getId());
        dto.setName(set.getName());
        dto.setVersion(set.getVersion());
        dto.setDescription(set.getDescription());
        dto.setEnabled(set.getEnabled());
        final List<Integer> moduleIds = new ArrayList<>();
        final List<YangModuleDto> modules = new ArrayList<>();
        for (final YangModule module : set.getModules()) {
            moduleIds.add(module.getId());
            modules.add(toModuleDto(module, false));
        }
        dto.setModuleIds(moduleIds);
        dto.setModules(modules);
        return dto;
    }
}
