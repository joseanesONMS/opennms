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
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder.DecodedMetric;
import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder.Update;
import org.opennms.features.openconfig.yang.YangSchemaContext.MetricKind;

public class OpenConfigGnmiDecoderTest {

    @Test
    public void decodesNumericUpdatesAndDropsNonNumeric() throws Exception {
        final YangSchemaContext schema = YangSchemaContext.fromResources(getClass(), "/spike-counters.yang");
        final OpenConfigGnmiDecoder decoder = new OpenConfigGnmiDecoder(schema);

        final List<DecodedMetric> metrics = decoder.decode(List.of(
                new Update("/interfaces/interface[name=eth0]/state/in-pkts", 12345L),
                new Update("/interfaces/interface[name=eth0]/state/admin-status", 0L), // string leaf -> dropped
                new Update("/interfaces/interface[name=eth0]/state/bogus", 1L)          // unknown -> dropped
        ));

        assertEquals(1, metrics.size());
        final DecodedMetric m = metrics.get(0);
        assertEquals("interface", m.resourceType());
        assertEquals("eth0", m.resourceInstance());
        assertEquals("in-pkts", m.metricName());
        assertEquals(12345L, m.value());
        assertEquals(MetricKind.COUNTER, m.kind());
        assertEquals("packets", m.units());
        assertTrue(!m.isNodeLevel());
    }

    @Test
    public void resolvesAcrossAMultiModuleImportClosure() throws Exception {
        // oc-interfaces imports its counter type (and units) from oc-types.
        final YangSchemaContext schema = YangSchemaContext.fromResources(
                getClass(), "/closure/oc-types.yang", "/closure/oc-interfaces.yang");
        final OpenConfigGnmiDecoder decoder = new OpenConfigGnmiDecoder(schema);

        final DecodedMetric m = decoder.decode(
                new Update("/interfaces/interface[name=eth0]/state/counters/in-octets", 99L)).orElseThrow();

        assertEquals("interface", m.resourceType());
        assertEquals("eth0", m.resourceInstance());
        assertEquals("in-octets", m.metricName());
        assertEquals(MetricKind.COUNTER, m.kind());
        assertEquals("octets", m.units()); // units came from the imported oc-types typedef
    }
}
