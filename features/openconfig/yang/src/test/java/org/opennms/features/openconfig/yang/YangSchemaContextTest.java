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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.opennms.features.openconfig.yang.YangSchemaContext.MetricKind;
import org.opennms.features.openconfig.yang.YangSchemaContext.ResolvedMetric;

public class YangSchemaContextTest {

    private static final String FIXTURE = "/spike-counters.yang";

    private YangSchemaContext load() throws Exception {
        return YangSchemaContext.fromResources(getClass(), FIXTURE);
    }

    @Test
    public void parsesPathWithKeysAndQuotes() {
        final YangPath path = YangPath.parse("/interfaces/interface[name='Ethernet1/2'][ifindex=3]/state/in-pkts");
        assertEquals(4, path.elements().size());
        final YangPath.Element ifc = path.elements().get(1);
        assertEquals("interface", ifc.name());
        assertEquals("Ethernet1/2", ifc.keys().get("name"));
        assertEquals("3", ifc.keys().get("ifindex"));
    }

    @Test
    public void resolvesKeyedPathToTypedMetricWithResourceInstance() throws Exception {
        final ResolvedMetric metric = load()
                .resolve(YangPath.parse("/interfaces/interface[name=eth0]/state/in-pkts"))
                .orElseThrow();

        assertEquals("in-pkts", metric.leafName());
        assertEquals("counter64", metric.typeName());
        assertEquals("uint64", metric.baseTypeName());
        assertEquals("packets", metric.units());
        assertEquals(MetricKind.COUNTER, metric.kind());

        assertEquals(1, metric.resourceInstances().size());
        assertEquals("interface", metric.resourceInstances().get(0).listName());
        assertEquals("eth0", metric.resourceInstances().get(0).keys().get("name"));
    }

    @Test
    public void pathThatDoesNotEndAtLeafResolvesEmpty() throws Exception {
        // '.../state' is a container, not a leaf.
        assertTrue(load().resolve(YangPath.parse("/interfaces/interface[name=eth0]/state")).isEmpty());
        // unknown element.
        assertTrue(load().resolve(YangPath.parse("/interfaces/bogus")).isEmpty());
    }

    @Test
    public void fromContentsBuildsFromInMemorySource() throws Exception {
        // Simulates the database registry handing the schema cache a model set's module content.
        final String content = new String(
                getClass().getResourceAsStream(FIXTURE).readAllBytes(), StandardCharsets.UTF_8);
        final YangSchemaContext ctx = YangSchemaContext.fromContents(
                List.of(new YangSchemaContext.NamedYangSource("spike-counters", content)));

        final ResolvedMetric metric = ctx
                .resolve(YangPath.parse("/interfaces/interface[name=eth0]/state/in-pkts"))
                .orElseThrow();
        assertEquals(MetricKind.COUNTER, metric.kind());
        assertEquals("packets", metric.units());
    }

    @Test
    public void cacheBuildsOncePerModelSetAndInvalidates() throws Exception {
        final String content = new String(
                getClass().getResourceAsStream(FIXTURE).readAllBytes(), StandardCharsets.UTF_8);
        final AtomicInteger builds = new AtomicInteger();
        final YangSchemaContextCache cache = new YangSchemaContextCache(id -> {
            builds.incrementAndGet();
            return List.of(new YangSchemaContext.NamedYangSource("spike-counters", content));
        });

        assertFalse(cache.isCached("oc-2024"));
        final YangSchemaContext first = cache.get("oc-2024");
        assertTrue(cache.isCached("oc-2024"));
        final YangSchemaContext second = cache.get("oc-2024");
        assertSame("second get must hit the cache", first, second);
        assertEquals("closure compiled exactly once", 1, builds.get());

        cache.invalidate("oc-2024");
        assertFalse(cache.isCached("oc-2024"));
        cache.get("oc-2024");
        assertEquals("rebuilt after invalidation", 2, builds.get());
    }
}
