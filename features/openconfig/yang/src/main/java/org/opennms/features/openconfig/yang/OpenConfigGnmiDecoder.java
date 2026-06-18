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
package org.opennms.features.openconfig.yang;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.opennms.features.openconfig.yang.YangSchemaContext.MetricKind;
import org.opennms.features.openconfig.yang.YangSchemaContext.ResolvedMetric;
import org.opennms.features.openconfig.yang.YangSchemaContext.ResourceInstance;

/**
 * Turns gNMI telemetry updates into typed, resource-scoped metrics purely from the YANG schema — the
 * replacement for the hand-written {@code openconfig-*-telemetry.groovy} adapter scripts (NMS-19857,
 * Phase 2). Each update's path is resolved against the model set's {@link YangSchemaContext}; keyed
 * list elements become the OpenNMS resource (type + instance), and the leaf's YANG type yields the
 * metric kind (counter/gauge) and units. Non-numeric leaves are dropped (they would become string
 * attributes in the full adapter).
 *
 * <p>Deliberately decoupled from the gNMI protobuf: the caller flattens a {@code Gnmi.Notification}
 * into {@link Update}s (path string + numeric value), keeping this core free of yangtools-unrelated
 * dependencies and trivially testable.</p>
 */
public final class OpenConfigGnmiDecoder {

    /** A single telemetry sample: a gNMI path and its numeric value. */
    public record Update(String path, Number value) {
    }

    /** A decoded, resource-scoped numeric metric ready to persist as a CollectionSet entry. */
    public record DecodedMetric(String resourceType, String resourceInstance, String metricName,
                                Number value, MetricKind kind, String units) {

        /** Node-level (no enclosing keyed list) metrics use this resource type. */
        public static final String NODE_RESOURCE = "node";

        public boolean isNodeLevel() {
            return NODE_RESOURCE.equals(resourceType) || resourceInstance == null;
        }
    }

    private final YangSchemaContext schema;

    public OpenConfigGnmiDecoder(final YangSchemaContext schema) {
        this.schema = Objects.requireNonNull(schema);
    }

    /** Decode a batch of updates, dropping any whose path is unknown or non-numeric. */
    public List<DecodedMetric> decode(final List<Update> updates) {
        final List<DecodedMetric> metrics = new ArrayList<>();
        for (final Update update : updates) {
            decode(update).ifPresent(metrics::add);
        }
        return metrics;
    }

    /** Decode a single update; empty if the path doesn't resolve to a numeric leaf. */
    public Optional<DecodedMetric> decode(final Update update) {
        final Optional<ResolvedMetric> resolved = schema.resolve(YangPath.parse(update.path()));
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        final ResolvedMetric metric = resolved.get();
        if (metric.kind() == MetricKind.OTHER) {
            return Optional.empty();
        }

        String resourceType = DecodedMetric.NODE_RESOURCE;
        String resourceInstance = null;
        if (!metric.resourceInstances().isEmpty()) {
            // The innermost keyed list identifies the resource (e.g. interface[name=eth0]).
            final ResourceInstance innermost = metric.resourceInstances().get(metric.resourceInstances().size() - 1);
            resourceType = innermost.listName();
            resourceInstance = String.join("/", innermost.keys().values());
        }

        return Optional.of(new DecodedMetric(
                resourceType, resourceInstance, metric.leafName(), update.value(), metric.kind(), metric.units()));
    }
}
