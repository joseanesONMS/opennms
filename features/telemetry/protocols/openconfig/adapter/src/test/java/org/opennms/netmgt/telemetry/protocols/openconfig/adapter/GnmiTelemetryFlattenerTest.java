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

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;
import org.opennms.features.openconfig.proto.gnmi.Gnmi;
import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder.Update;

public class GnmiTelemetryFlattenerTest {

    @Test
    public void concatenatesPrefixAndPathAndKeepsOnlyNumericValues() {
        final Gnmi.Path prefix = Gnmi.Path.newBuilder()
                .addElem(Gnmi.PathElem.newBuilder().setName("interfaces"))
                .addElem(Gnmi.PathElem.newBuilder().setName("interface").putKey("name", "eth0"))
                .addElem(Gnmi.PathElem.newBuilder().setName("state"))
                .build();

        final Gnmi.Update numeric = Gnmi.Update.newBuilder()
                .setPath(Gnmi.Path.newBuilder().addElem(Gnmi.PathElem.newBuilder().setName("in-octets")))
                .setVal(Gnmi.TypedValue.newBuilder().setUintVal(123L))
                .build();

        final Gnmi.Update stringValued = Gnmi.Update.newBuilder()
                .setPath(Gnmi.Path.newBuilder().addElem(Gnmi.PathElem.newBuilder().setName("admin-status")))
                .setVal(Gnmi.TypedValue.newBuilder().setStringVal("UP"))
                .build();

        final Gnmi.SubscribeResponse response = Gnmi.SubscribeResponse.newBuilder()
                .setUpdate(Gnmi.Notification.newBuilder()
                        .setPrefix(prefix)
                        .addUpdate(numeric)
                        .addUpdate(stringValued))
                .build();

        final List<Update> updates = GnmiTelemetryFlattener.flatten(response);

        assertEquals(1, updates.size());
        assertEquals("/interfaces/interface[name=eth0]/state/in-octets", updates.get(0).path());
        assertEquals(123L, updates.get(0).value());
    }

    @Test
    public void emptyResponseYieldsNoUpdates() {
        assertEquals(0, GnmiTelemetryFlattener.flatten(Gnmi.SubscribeResponse.newBuilder().build()).size());
    }
}
