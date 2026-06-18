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
package org.opennms.web.rest.v2.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opennms.web.rest.v2.model.YangModelSetDto;
import org.opennms.web.rest.v2.model.YangModuleDto;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.List;

/**
 * YANG model registry (NMS-19857, Phase 2) under {@code /api/v2/yangregistry}: manage YANG modules
 * (uploaded source) and the model sets that bundle them for schema-driven OpenConfig decoding.
 */
@Path("yangregistry")
@Tag(name = "YangRegistry", description = "YANG model registry API")
public interface YangRegistryRestApi {

    @GET
    @Path("/modules")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List YANG modules", operationId = "listYangModules")
    Response listModules(@Context SecurityContext securityContext);

    @GET
    @Path("/modules/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a YANG module (with source)", operationId = "getYangModule")
    Response getModule(@PathParam("id") Integer id, @Context SecurityContext securityContext);

    @POST
    @Path("/modules")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Add a YANG module from source (name/revision parsed from content)",
            operationId = "createYangModule")
    Response createModule(YangModuleDto module, @Context SecurityContext securityContext);

    @DELETE
    @Path("/modules")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Bulk-delete YANG modules", operationId = "deleteYangModules")
    Response deleteModules(List<Integer> ids, @Context SecurityContext securityContext);

    @GET
    @Path("/modelsets")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List YANG model sets", operationId = "listYangModelSets")
    Response listModelSets(@Context SecurityContext securityContext);

    @GET
    @Path("/modelsets/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a YANG model set", operationId = "getYangModelSet")
    Response getModelSet(@PathParam("id") Integer id, @Context SecurityContext securityContext);

    @POST
    @Path("/modelsets")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a YANG model set", operationId = "createYangModelSet")
    Response createModelSet(YangModelSetDto modelSet, @Context SecurityContext securityContext);

    @PUT
    @Path("/modelsets/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a YANG model set", operationId = "updateYangModelSet")
    Response updateModelSet(@PathParam("id") Integer id, YangModelSetDto modelSet,
                           @Context SecurityContext securityContext);

    @DELETE
    @Path("/modelsets")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Bulk-delete YANG model sets", operationId = "deleteYangModelSets")
    Response deleteModelSets(List<Integer> ids, @Context SecurityContext securityContext);
}
