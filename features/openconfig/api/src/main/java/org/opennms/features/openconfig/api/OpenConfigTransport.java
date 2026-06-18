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
package org.opennms.features.openconfig.api;

/**
 * The transport mechanism used to collect OpenConfig streaming telemetry from a device.
 *
 * <p>Historically the transport was selected via a free-form {@code mode} parameter where the only
 * recognized value was {@code jti} (everything else defaulted to gNMI). This enum makes the choice
 * first-class so that configuration (and, in later phases, additional transports such as gNMI
 * dial-out and NETCONF/yang-push) can be modeled explicitly while remaining backwards compatible
 * with the legacy {@code mode} values.</p>
 */
public enum OpenConfigTransport {

    /** gNMI dial-in: OpenNMS opens the gRPC channel to the device and issues a Subscribe RPC. */
    GNMI_DIALIN,

    /** Juniper Telemetry Interface (JTI) OpenConfig RPC. */
    JTI;

    /** The transport assumed when none is configured (preserves the historical gNMI default). */
    public static final OpenConfigTransport DEFAULT = GNMI_DIALIN;

    /**
     * Resolves a transport from the legacy {@code mode} parameter value.
     *
     * <p>Only {@code jti} (case-insensitive) selects {@link #JTI}; {@code gnmi}, {@code gnmi_dialin},
     * {@code gnmi-dialin}, blank and unknown values all resolve to {@link #GNMI_DIALIN}. This mirrors
     * the previous {@code "jti".equalsIgnoreCase(mode)} behavior exactly.</p>
     */
    public static OpenConfigTransport fromMode(final String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return DEFAULT;
        }
        switch (mode.trim().toLowerCase()) {
            case "jti":
                return JTI;
            case "gnmi":
            case "gnmi_dialin":
            case "gnmi-dialin":
                return GNMI_DIALIN;
            default:
                return DEFAULT;
        }
    }
}
