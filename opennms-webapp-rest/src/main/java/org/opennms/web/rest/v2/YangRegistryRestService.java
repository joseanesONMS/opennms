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

import org.opennms.web.rest.v2.api.YangRegistryRestApi;
import org.opennms.web.rest.v2.model.YangModelSetDto;
import org.opennms.web.rest.v2.model.YangModuleDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.List;

@Component
public class YangRegistryRestService implements YangRegistryRestApi {

    private static final Logger LOG = LoggerFactory.getLogger(YangRegistryRestService.class);

    @Autowired
    private YangRegistryPersistenceService persistenceService;

    @Override
    public Response listModules(final SecurityContext securityContext) {
        return Response.ok(persistenceService.listModules()).build();
    }

    @Override
    public Response getModule(final Integer id, final SecurityContext securityContext) {
        final YangModuleDto dto = persistenceService.getModule(id);
        return dto == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(dto).build();
    }

    @Override
    public Response createModule(final YangModuleDto module, final SecurityContext securityContext) {
        try {
            final Integer id = persistenceService.createModule(module, getUsername(securityContext));
            return Response.status(Response.Status.CREATED).entity(id).build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response deleteModules(final List<Integer> ids, final SecurityContext securityContext) {
        try {
            persistenceService.deleteModules(ids);
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response listModelSets(final SecurityContext securityContext) {
        return Response.ok(persistenceService.listModelSets()).build();
    }

    @Override
    public Response getModelSet(final Integer id, final SecurityContext securityContext) {
        final YangModelSetDto dto = persistenceService.getModelSet(id);
        return dto == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(dto).build();
    }

    @Override
    public Response createModelSet(final YangModelSetDto modelSet, final SecurityContext securityContext) {
        try {
            final Integer id = persistenceService.createModelSet(modelSet, getUsername(securityContext));
            return Response.status(Response.Status.CREATED).entity(id).build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response updateModelSet(final Integer id, final YangModelSetDto modelSet, final SecurityContext securityContext) {
        try {
            persistenceService.updateModelSet(id, modelSet, getUsername(securityContext));
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @Override
    public Response deleteModelSets(final List<Integer> ids, final SecurityContext securityContext) {
        try {
            persistenceService.deleteModelSets(ids);
            return Response.ok().build();
        } catch (final IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    private static Response badRequest(final Exception e) {
        LOG.debug("YANG registry request rejected: {}", e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
    }

    private static String getUsername(final SecurityContext securityContext) {
        final Principal principal = securityContext != null ? securityContext.getUserPrincipal() : null;
        return principal != null ? principal.getName() : "admin";
    }
}
