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

import org.opennms.web.rest.v2.api.OpenConfigConfRestApi;
import org.opennms.web.rest.v2.model.OpenConfigProfileDto;
import org.opennms.web.rest.v2.model.OpenConfigTargetDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;

@Component
public class OpenConfigConfRestService implements OpenConfigConfRestApi {

    private static final Logger LOG = LoggerFactory.getLogger(OpenConfigConfRestService.class);

    @Autowired
    private OpenConfigConfPersistenceService persistenceService;

    @Override
    public Response listProfiles(final SecurityContext securityContext) {
        return Response.ok(persistenceService.listProfiles()).build();
    }

    @Override
    public Response getProfile(final Integer profileId, final SecurityContext securityContext) {
        final OpenConfigProfileDto dto = persistenceService.getProfile(profileId);
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dto).build();
    }

    @Override
    public Response createProfile(final OpenConfigProfileDto profile, final SecurityContext securityContext) {
        try {
            final Integer id = persistenceService.createProfile(profile, getUsername(securityContext));
            return Response.status(Response.Status.CREATED).entity(id).build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response updateProfile(final Integer profileId, final OpenConfigProfileDto profile, final SecurityContext securityContext) {
        try {
            persistenceService.updateProfile(profileId, profile, getUsername(securityContext));
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response deleteProfiles(final List<Integer> ids, final SecurityContext securityContext) {
        try {
            persistenceService.deleteProfiles(ids);
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response enableProfiles(final boolean enabled, final List<Integer> ids, final SecurityContext securityContext) {
        try {
            persistenceService.setProfilesEnabled(ids, enabled);
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response listTargets(final Integer profileId, final SecurityContext securityContext) {
        try {
            return Response.ok(persistenceService.listTargets(profileId)).build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response getTarget(final Integer profileId, final Integer targetId, final SecurityContext securityContext) {
        final OpenConfigTargetDto dto = persistenceService.getTarget(profileId, targetId);
        if (dto == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(dto).build();
    }

    @Override
    public Response createTarget(final Integer profileId, final OpenConfigTargetDto target, final SecurityContext securityContext) {
        try {
            final Integer id = persistenceService.createTarget(profileId, target, getUsername(securityContext));
            return Response.status(Response.Status.CREATED).entity(id).build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response updateTarget(final Integer profileId, final Integer targetId, final OpenConfigTargetDto target, final SecurityContext securityContext) {
        try {
            persistenceService.updateTarget(profileId, targetId, target, getUsername(securityContext));
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response deleteTargets(final Integer profileId, final List<Integer> ids, final SecurityContext securityContext) {
        try {
            persistenceService.deleteTargets(profileId, ids);
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response enableTargets(final Integer profileId, final boolean enabled, final List<Integer> ids, final SecurityContext securityContext) {
        try {
            persistenceService.setTargetsEnabled(profileId, ids, enabled);
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    private static Response badRequest(final Exception e) {
        LOG.debug("OpenConfig configuration request rejected: {}", e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    }

    private static String getUsername(final SecurityContext securityContext) {
        final Principal principal = securityContext != null ? securityContext.getUserPrincipal() : null;
        return principal != null ? principal.getName() : "admin";
    }
}
