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

import java.util.Date;
import java.util.List;

import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder.DecodedMetric;
import org.opennms.features.openconfig.yang.YangSchemaContext.MetricKind;
import org.opennms.netmgt.collection.api.AttributeType;
import org.opennms.netmgt.collection.api.CollectionAgent;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.support.builder.CollectionSetBuilder;
import org.opennms.netmgt.collection.support.builder.DeferredGenericTypeResource;
import org.opennms.netmgt.collection.support.builder.InterfaceLevelResource;
import org.opennms.netmgt.collection.support.builder.NodeLevelResource;
import org.opennms.netmgt.collection.support.builder.Resource;

/**
 * Builds an OpenNMS {@link CollectionSet} from schema-decoded metrics (NMS-19857, Phase 2) — the
 * Java, schema-driven replacement for the {@code openconfig-gnmi-telemetry.groovy} script's
 * CollectionSet generation. Resource scoping and counter/gauge come from the YANG-derived
 * {@link DecodedMetric}, not hardcoded path matching: node-level metrics attach to the node;
 * {@code interface} instances to an interface resource; any other keyed list to a generic resource
 * type named after the list.
 */
public final class SchemaDrivenCollectionSetBuilder {

    private static final String INTERFACE_RESOURCE_TYPE = "interface";
    private static final String GROUP_PREFIX = "openconfig-";

    private SchemaDrivenCollectionSetBuilder() {
    }

    public static CollectionSet build(final CollectionAgent agent, final List<DecodedMetric> metrics,
                                      final Date timestamp) {
        final CollectionSetBuilder builder = new CollectionSetBuilder(agent);
        if (timestamp != null) {
            builder.withTimestamp(timestamp);
        }
        final NodeLevelResource node = new NodeLevelResource(agent.getNodeId());

        for (final DecodedMetric metric : metrics) {
            final Resource resource = resourceFor(node, metric);
            final AttributeType type = (metric.kind() == MetricKind.COUNTER)
                    ? AttributeType.COUNTER : AttributeType.GAUGE;
            builder.withNumericAttribute(resource, GROUP_PREFIX + metric.resourceType(),
                    metric.metricName(), metric.value(), type);
        }
        return builder.build();
    }

    private static Resource resourceFor(final NodeLevelResource node, final DecodedMetric metric) {
        if (metric.isNodeLevel()) {
            return node;
        }
        if (INTERFACE_RESOURCE_TYPE.equals(metric.resourceType())) {
            return new InterfaceLevelResource(node, metric.resourceInstance());
        }
        return new DeferredGenericTypeResource(node, metric.resourceType(), metric.resourceInstance());
    }
}
