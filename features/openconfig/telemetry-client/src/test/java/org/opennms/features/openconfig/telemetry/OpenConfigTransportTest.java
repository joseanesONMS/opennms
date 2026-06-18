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
package org.opennms.features.openconfig.telemetry;

import org.junit.Assert;
import org.junit.Test;
import org.opennms.features.openconfig.api.OpenConfigTransport;

public class OpenConfigTransportTest {

    @Test
    public void resolvesJtiOnlyForJtiMode() {
        Assert.assertEquals(OpenConfigTransport.JTI, OpenConfigTransport.fromMode("jti"));
        Assert.assertEquals(OpenConfigTransport.JTI, OpenConfigTransport.fromMode("JTI"));
        Assert.assertEquals(OpenConfigTransport.JTI, OpenConfigTransport.fromMode("  Jti  "));
    }

    @Test
    public void resolvesGnmiForGnmiAndAliases() {
        Assert.assertEquals(OpenConfigTransport.GNMI_DIALIN, OpenConfigTransport.fromMode("gnmi"));
        Assert.assertEquals(OpenConfigTransport.GNMI_DIALIN, OpenConfigTransport.fromMode("GNMI"));
        Assert.assertEquals(OpenConfigTransport.GNMI_DIALIN, OpenConfigTransport.fromMode("gnmi_dialin"));
        Assert.assertEquals(OpenConfigTransport.GNMI_DIALIN, OpenConfigTransport.fromMode("gnmi-dialin"));
    }

    @Test
    public void defaultsToGnmiForNullBlankAndUnknown() {
        // Preserves the historical behavior where anything that is not "jti" fell through to gNMI.
        Assert.assertEquals(OpenConfigTransport.GNMI_DIALIN, OpenConfigTransport.fromMode(null));
        Assert.assertEquals(OpenConfigTransport.GNMI_DIALIN, OpenConfigTransport.fromMode(""));
        Assert.assertEquals(OpenConfigTransport.GNMI_DIALIN, OpenConfigTransport.fromMode("   "));
        Assert.assertEquals(OpenConfigTransport.GNMI_DIALIN, OpenConfigTransport.fromMode("something-else"));
        Assert.assertEquals(OpenConfigTransport.GNMI_DIALIN, OpenConfigTransport.DEFAULT);
    }
}
