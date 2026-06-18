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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opennms.features.openconfig.proto.gnmi.Gnmi;
import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder;

/**
 * Flattens a gNMI {@link Gnmi.SubscribeResponse} into the path/value {@link OpenConfigGnmiDecoder.Update}s
 * consumed by the schema-driven decoder (NMS-19857, Phase 2). Each notification carries a common
 * {@code prefix} path plus per-update suffix paths; this concatenates them into a full gNMI path
 * string (including {@code [key=value]} predicates) and extracts a numeric value from the
 * {@code TypedValue}. Non-numeric values are skipped (they would become string attributes in the
 * full adapter).
 */
public final class GnmiTelemetryFlattener {

    private GnmiTelemetryFlattener() {
    }

    public static List<OpenConfigGnmiDecoder.Update> flatten(final Gnmi.SubscribeResponse response) {
        final List<OpenConfigGnmiDecoder.Update> updates = new ArrayList<>();
        if (response == null || !response.hasUpdate()) {
            return updates;
        }
        final Gnmi.Notification notification = response.getUpdate();
        final String prefix = renderPath(notification.getPrefix().getElemList());
        for (final Gnmi.Update update : notification.getUpdateList()) {
            final Number value = numericValue(update.getVal());
            if (value == null) {
                continue;
            }
            final String path = prefix + renderPath(update.getPath().getElemList());
            updates.add(new OpenConfigGnmiDecoder.Update(path, value));
        }
        return updates;
    }

    private static String renderPath(final List<Gnmi.PathElem> elems) {
        final StringBuilder sb = new StringBuilder();
        for (final Gnmi.PathElem elem : elems) {
            sb.append('/').append(elem.getName());
            for (final Map.Entry<String, String> key : elem.getKeyMap().entrySet()) {
                sb.append('[').append(key.getKey()).append('=').append(key.getValue()).append(']');
            }
        }
        return sb.toString();
    }

    private static Number numericValue(final Gnmi.TypedValue value) {
        switch (value.getValueCase()) {
            case UINT_VAL:
                return value.getUintVal();
            case INT_VAL:
                return value.getIntVal();
            case FLOAT_VAL:
                return value.getFloatVal();
            case JSON_VAL:
                return parseJsonNumber(value.getJsonVal().toByteArray());
            case JSON_IETF_VAL:
                return parseJsonNumber(value.getJsonIetfVal().toByteArray());
            default:
                return null;
        }
    }

    private static Number parseJsonNumber(final byte[] bytes) {
        final String text = new String(bytes).replace("\"", "").trim();
        try {
            return Double.parseDouble(text);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
