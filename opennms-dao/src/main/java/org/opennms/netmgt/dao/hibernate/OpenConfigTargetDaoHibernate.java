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

import org.opennms.netmgt.dao.api.OpenConfigTargetDao;
import org.opennms.netmgt.model.OpenConfigTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public class OpenConfigTargetDaoHibernate extends AbstractDaoHibernate<OpenConfigTarget, Integer> implements OpenConfigTargetDao {

    private static final Logger LOG = LoggerFactory.getLogger(OpenConfigTargetDaoHibernate.class);

    public OpenConfigTargetDaoHibernate() {
        super(OpenConfigTarget.class);
    }

    @Override
    public OpenConfigTarget get(Integer id) {
        return super.get(id);
    }

    @Override
    public OpenConfigTarget findByNameAndProfile(String name, Integer profileId) {
        List<OpenConfigTarget> list = find(
                "from OpenConfigTarget t where t.name = ? and t.profile.id = ?", name, profileId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public OpenConfigTarget findByProfileIdAndId(Integer profileId, Integer id) {
        return findUnique("from OpenConfigTarget t where t.profile.id = ? and t.id = ?", profileId, id);
    }

    @Override
    public List<OpenConfigTarget> findAllEnabled() {
        return find("from OpenConfigTarget t where t.enabled = true");
    }

    @Override
    public List<OpenConfigTarget> findAllByProfile(Integer profileId) {
        return find("from OpenConfigTarget t where t.profile.id = ?", profileId);
    }

    @Override
    public List<OpenConfigTarget> findAllEnabledByProfile(Integer profileId) {
        return find("from OpenConfigTarget t where t.profile.id = ? and t.enabled = true", profileId);
    }

    @Override
    public void saveAll(Collection<OpenConfigTarget> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        int batchSize = 50;
        int i = 0;
        for (OpenConfigTarget target : list) {
            getHibernateTemplate().saveOrUpdate(target);
            if (++i % batchSize == 0) {
                getHibernateTemplate().flush();
                getHibernateTemplate().clear();
            }
        }
        getHibernateTemplate().flush();
        getHibernateTemplate().clear();
    }

    @Override
    public void deleteAll(final Collection<OpenConfigTarget> list) {
        super.deleteAll(list);
    }

    @Override
    public void deleteByProfileId(Integer profileId) {
        getHibernateTemplate().bulkUpdate("delete from OpenConfigTarget t where t.profile.id = ?", profileId);
    }

    @Override
    public void updateTargetEnabledFlag(Integer profileId, List<Integer> ids, boolean enabled) {
        if (ids == null || ids.isEmpty()) {
            LOG.warn("No OpenConfig target IDs provided for update. Skipping...");
            return;
        }
        int updatedCount = getSessionFactory().getCurrentSession()
                .createQuery("update OpenConfigTarget t set t.enabled = :enabled where t.profile.id = :profileId and t.id in (:ids)")
                .setParameter("enabled", enabled)
                .setParameter("profileId", profileId)
                .setParameterList("ids", ids)
                .executeUpdate();
        LOG.info("Updated {} OpenConfig targets (enabled={}) for profileId={}", updatedCount, enabled, profileId);
    }
}
