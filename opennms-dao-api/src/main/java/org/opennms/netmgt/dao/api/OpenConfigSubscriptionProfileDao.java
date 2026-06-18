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
package org.opennms.netmgt.dao.api;

import org.opennms.netmgt.model.OpenConfigSubscriptionProfile;
import org.opennms.netmgt.model.PageResponse;

import java.util.Collection;
import java.util.List;

public interface OpenConfigSubscriptionProfileDao extends OnmsDao<OpenConfigSubscriptionProfile, Integer> {

    OpenConfigSubscriptionProfile get(Integer id);

    OpenConfigSubscriptionProfile findByName(String name);

    List<OpenConfigSubscriptionProfile> findAll();

    List<OpenConfigSubscriptionProfile> findAllEnabled();

    /**
     * Find all profiles whose {@code uploadedBy} matches the given marker.
     * Used by plugin/import sync paths to enumerate managed rows.
     */
    List<OpenConfigSubscriptionProfile> findByUploadedBy(String uploadedBy);

    void delete(OpenConfigSubscriptionProfile profile);

    void deleteAll(Collection<OpenConfigSubscriptionProfile> list);

    PageResponse<OpenConfigSubscriptionProfile> filterProfiles(String filter, String sortBy, String order,
                                                               Integer totalRecords, Integer offset, Integer limit);

    /** Set the enabled flag on the given profiles, cascading to their targets. */
    void updateEnabledFlag(Collection<Integer> profileIds, boolean enabled);
}
