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
package org.opennms.netmgt.dao.hibernate;

import org.opennms.netmgt.dao.DaoUtil;
import org.opennms.netmgt.dao.api.OpenConfigSubscriptionProfileDao;
import org.opennms.netmgt.model.OpenConfigSubscriptionProfile;
import org.opennms.netmgt.model.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class OpenConfigSubscriptionProfileDaoHibernate extends AbstractDaoHibernate<OpenConfigSubscriptionProfile, Integer> implements OpenConfigSubscriptionProfileDao {

    private static final Logger LOG = LoggerFactory.getLogger(OpenConfigSubscriptionProfileDaoHibernate.class);

    public OpenConfigSubscriptionProfileDaoHibernate() {
        super(OpenConfigSubscriptionProfile.class);
    }

    @Override
    public OpenConfigSubscriptionProfile get(Integer id) {
        return super.get(id);
    }

    @Override
    public OpenConfigSubscriptionProfile findByName(String name) {
        List<OpenConfigSubscriptionProfile> list = find("from OpenConfigSubscriptionProfile p where p.name = ?", name);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<OpenConfigSubscriptionProfile> findAllEnabled() {
        return find("from OpenConfigSubscriptionProfile p where p.enabled = true");
    }

    @Override
    public List<OpenConfigSubscriptionProfile> findByUploadedBy(String uploadedBy) {
        return find("from OpenConfigSubscriptionProfile p where p.uploadedBy = ?", uploadedBy);
    }

    @Override
    public void deleteAll(final Collection<OpenConfigSubscriptionProfile> list) {
        super.deleteAll(list);
    }

    @Override
    public PageResponse<OpenConfigSubscriptionProfile> filterProfiles(
            final String filter,
            final String sortBy,
            final String order,
            Integer totalRecords,
            Integer offset,
            Integer limit) {

        int resultCount = totalRecords != null ? totalRecords : 0;
        List<OpenConfigSubscriptionProfile> profileList = Collections.emptyList();

        try {
            List<Object> queryParams = new ArrayList<>();
            List<String> conditions = new ArrayList<>();

            if (filter != null && !filter.isBlank()) {
                String escapedFilter = "%" + DaoUtil.escapeLike(filter.trim().toLowerCase()) + "%";
                conditions.add("lower(p.name) like ? escape '\\'");
                conditions.add("lower(p.description) like ? escape '\\'");
                conditions.add("lower(p.transport) like ? escape '\\'");
                queryParams.add(escapedFilter);
                queryParams.add(escapedFilter);
                queryParams.add(escapedFilter);
            }

            String whereClause = conditions.isEmpty() ? "" : " where " + String.join(" OR ", conditions);

            if (resultCount == 0) {
                String countQuery = "select count(p.id) from OpenConfigSubscriptionProfile p" + whereClause;
                resultCount = super.queryInt(countQuery, queryParams.toArray());
            }

            if (resultCount > 0) {
                Set<String> allowedSortFields = Set.of("name", "transport", "encoding");
                String sortField = (sortBy != null && !sortBy.isBlank() && allowedSortFields.contains(sortBy)) ? sortBy : "createdTime";
                String sortOrder = "ASC".equalsIgnoreCase(order) ? "ASC" : "DESC";
                String orderBy = " order by p." + sortField + " " + sortOrder;

                String dataQuery = "from OpenConfigSubscriptionProfile p" + whereClause + orderBy;
                profileList = findWithPagination(dataQuery, queryParams.toArray(), offset, limit);
            }
        } catch (Exception e) {
            LOG.error("Error in filterProfiles while fetching records", e);
        }

        return new PageResponse<>(resultCount, profileList);
    }

    @Override
    public void updateEnabledFlag(Collection<Integer> profileIds, boolean enabled) {
        if (profileIds == null || profileIds.isEmpty()) {
            return;
        }
        getSessionFactory().getCurrentSession()
                .createQuery("update OpenConfigSubscriptionProfile p set p.enabled = :enabled where p.id in (:ids)")
                .setParameter("enabled", enabled)
                .setParameterList("ids", profileIds)
                .executeUpdate();

        getSessionFactory().getCurrentSession()
                .createQuery("update OpenConfigTarget t set t.enabled = :enabled where t.profile.id in (:ids)")
                .setParameter("enabled", enabled)
                .setParameterList("ids", profileIds)
                .executeUpdate();

        LOG.info("Set enabled={} for OpenConfig profiles {} (cascaded to targets)", enabled, profileIds);
    }
}
