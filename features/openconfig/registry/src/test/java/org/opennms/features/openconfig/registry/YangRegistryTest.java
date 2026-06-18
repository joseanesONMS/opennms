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
package org.opennms.features.openconfig.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Test;
import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder;
import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder.DecodedMetric;
import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder.Update;
import org.opennms.features.openconfig.yang.YangSchemaContext.MetricKind;
import org.opennms.netmgt.model.YangModelSet;
import org.opennms.netmgt.model.YangModule;

public class YangRegistryTest {

    private static final String COUNTER_YANG = """
            module reg-counters {
              namespace "urn:opennms:reg:counters";
              prefix rc;
              typedef counter64 {
                type uint64;
                units "packets";
              }
              container interfaces {
                list interface {
                  key "name";
                  leaf name { type string; }
                  container state {
                    leaf in-pkts { type counter64; }
                  }
                }
              }
            }
            """;

    private YangModelSet modelSet(final String name) {
        final YangModule module = new YangModule();
        module.setName("reg-counters");
        module.setContent(COUNTER_YANG);
        final YangModelSet set = new YangModelSet();
        set.setName(name);
        set.setModules(new LinkedHashSet<>(Set.of(module)));
        return set;
    }

    @Test
    public void compilesAModelSetFromTheRegistryAndDecodes() throws Exception {
        final YangModelSet set = modelSet("oc-2024");
        final DbYangSourceProvider provider =
                new DbYangSourceProvider(name -> "oc-2024".equals(name) ? set : null);
        final YangRegistry registry = new YangRegistry(provider);

        final OpenConfigGnmiDecoder decoder = registry.decoderFor("oc-2024");
        final DecodedMetric metric = decoder
                .decode(new Update("/interfaces/interface[name=eth0]/state/in-pkts", 42L))
                .orElseThrow();

        assertEquals("interface", metric.resourceType());
        assertEquals("eth0", metric.resourceInstance());
        assertEquals(MetricKind.COUNTER, metric.kind());
        assertEquals("packets", metric.units());
        assertEquals(42L, metric.value());
    }

    @Test
    public void unknownModelSetIsRejected() {
        final YangRegistry registry = new YangRegistry(new DbYangSourceProvider(name -> null));
        assertThrows(Exception.class, () -> registry.schemaFor("does-not-exist"));
    }
}
