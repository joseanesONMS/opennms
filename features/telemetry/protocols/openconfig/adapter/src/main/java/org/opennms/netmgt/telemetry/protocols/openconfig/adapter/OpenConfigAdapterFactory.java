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
package org.opennms.netmgt.telemetry.protocols.openconfig.adapter;

import org.opennms.features.openconfig.registry.DbYangSourceProvider;
import org.opennms.features.openconfig.registry.YangRegistry;
import org.opennms.netmgt.dao.api.YangModelSetDao;
import org.opennms.netmgt.model.YangModelSet;
import org.opennms.netmgt.telemetry.api.adapter.Adapter;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.protocols.collection.AbstractCollectionAdapterFactory;
import org.osgi.framework.BundleContext;
import org.springframework.transaction.support.TransactionOperations;

public class OpenConfigAdapterFactory extends AbstractCollectionAdapterFactory {

    /** Optional: when wired, enables schema-driven (YANG) decoding (NMS-19857, Phase 2). */
    private YangModelSetDao yangModelSetDao;
    private volatile YangRegistry yangRegistry;

    public OpenConfigAdapterFactory() {
        super(null);
    }

    public OpenConfigAdapterFactory(BundleContext bundleContext) {
        super(bundleContext);
    }

    public void setYangModelSetDao(YangModelSetDao yangModelSetDao) {
        this.yangModelSetDao = yangModelSetDao;
    }

    /**
     * Lazily builds a {@link YangRegistry} backed by the database model-set registry. The model-set
     * lookup runs inside the adapter's transaction and forces initialization of the lazily-loaded
     * module set so the schema closure can be compiled outside the session.
     */
    private YangRegistry yangRegistry() {
        if (yangRegistry == null && yangModelSetDao != null) {
            final TransactionOperations tx = getTransactionTemplate();
            yangRegistry = new YangRegistry(new DbYangSourceProvider(name -> tx.execute(status -> {
                final YangModelSet set = yangModelSetDao.findByName(name);
                if (set != null) {
                    set.getModules().size(); // force lazy initialization within the transaction
                }
                return set;
            })));
        }
        return yangRegistry;
    }

    @Override
    public Class<? extends Adapter> getBeanClass() {
        return OpenConfigAdapter.class;
    }

    @Override
    public Adapter createBean(AdapterDefinition adapterConfig) {
        final OpenConfigAdapter adapter = new OpenConfigAdapter(adapterConfig, getTelemetryRegistry().getMetricRegistry());
        adapter.setCollectionAgentFactory(getCollectionAgentFactory());
        adapter.setInterfaceToNodeCache(getInterfaceToNodeCache());
        adapter.setNodeDao(getNodeDao());
        adapter.setTransactionTemplate(getTransactionTemplate());
        adapter.setFilterDao(getFilterDao());
        adapter.setPersisterFactory(getPersisterFactory());
        adapter.setThresholdingService(getThresholdingService());
        adapter.setBundleContext(getBundleContext());
        adapter.setYangRegistry(yangRegistry());
        return adapter;
    }
}
