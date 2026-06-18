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
import org.opennms.web.rest.v2.model.OpenConfigProfileDto;
import org.opennms.web.rest.v2.model.OpenConfigTargetDto;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.List;

/**
 * Database-backed OpenConfig streaming-telemetry configuration (NMS-19857). Exposed under
 * {@code /api/v2/openconfigconf}.
 */
@Path("openconfigconf")
@Tag(name = "OpenConfigConf", description = "OpenConfig streaming-telemetry configuration API")
public interface OpenConfigConfRestApi {

    @GET
    @Path("/profiles")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List OpenConfig subscription profiles", operationId = "listOpenConfigProfiles")
    Response listProfiles(@Context SecurityContext securityContext);

    @GET
    @Path("/profiles/{profileId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get an OpenConfig subscription profile", operationId = "getOpenConfigProfile")
    Response getProfile(@PathParam("profileId") Integer profileId, @Context SecurityContext securityContext);

    @POST
    @Path("/profiles")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create an OpenConfig subscription profile", operationId = "createOpenConfigProfile")
    Response createProfile(OpenConfigProfileDto profile, @Context SecurityContext securityContext);

    @PUT
    @Path("/profiles/{profileId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update an OpenConfig subscription profile", operationId = "updateOpenConfigProfile")
    Response updateProfile(@PathParam("profileId") Integer profileId, OpenConfigProfileDto profile,
                           @Context SecurityContext securityContext);

    @DELETE
    @Path("/profiles")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Bulk-delete OpenConfig subscription profiles", operationId = "deleteOpenConfigProfiles")
    Response deleteProfiles(List<Integer> ids, @Context SecurityContext securityContext);

    @PUT
    @Path("/profiles/enable")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Bulk enable/disable OpenConfig subscription profiles", operationId = "enableOpenConfigProfiles")
    Response enableProfiles(@QueryParam("enabled") boolean enabled, List<Integer> ids,
                            @Context SecurityContext securityContext);

    @GET
    @Path("/profiles/{profileId}/targets")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "List targets for a profile", operationId = "listOpenConfigTargets")
    Response listTargets(@PathParam("profileId") Integer profileId, @Context SecurityContext securityContext);

    @GET
    @Path("/profiles/{profileId}/targets/{targetId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Get a target", operationId = "getOpenConfigTarget")
    Response getTarget(@PathParam("profileId") Integer profileId, @PathParam("targetId") Integer targetId,
                       @Context SecurityContext securityContext);

    @POST
    @Path("/profiles/{profileId}/targets")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a target on a profile", operationId = "createOpenConfigTarget")
    Response createTarget(@PathParam("profileId") Integer profileId, OpenConfigTargetDto target,
                          @Context SecurityContext securityContext);

    @PUT
    @Path("/profiles/{profileId}/targets/{targetId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a target", operationId = "updateOpenConfigTarget")
    Response updateTarget(@PathParam("profileId") Integer profileId, @PathParam("targetId") Integer targetId,
                          OpenConfigTargetDto target, @Context SecurityContext securityContext);

    @DELETE
    @Path("/profiles/{profileId}/targets")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Bulk-delete targets on a profile", operationId = "deleteOpenConfigTargets")
    Response deleteTargets(@PathParam("profileId") Integer profileId, List<Integer> ids,
                           @Context SecurityContext securityContext);

    @PUT
    @Path("/profiles/{profileId}/targets/enable")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Bulk enable/disable targets on a profile", operationId = "enableOpenConfigTargets")
    Response enableTargets(@PathParam("profileId") Integer profileId, @QueryParam("enabled") boolean enabled,
                           List<Integer> ids, @Context SecurityContext securityContext);
}
