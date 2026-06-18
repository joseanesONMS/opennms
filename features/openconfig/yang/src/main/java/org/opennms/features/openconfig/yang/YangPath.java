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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A gNMI-style telemetry path, e.g. {@code /interfaces/interface[name=eth0]/state/counters/in-octets},
 * parsed into ordered elements each carrying an optional set of list keys. Mirrors the path grammar
 * already used by {@code OpenConfigClientImpl#buildGnmiPath} so the same strings round-trip.
 */
public record YangPath(List<Element> elements) {

    /** One path element: a node name plus any {@code [key=value]} predicates on it. */
    public record Element(String name, Map<String, String> keys) {
    }

    // Path separator '/' but not inside square brackets.
    private static final Pattern PATH_SEPARATOR = Pattern.compile("\\/(?![^\\[]*])");
    private static final Pattern KEY_PREDICATE = Pattern.compile("\\[(.+?=.+?)\\]");

    /** Parse a gNMI-style path string. Empty/"/" yields an empty path. */
    public static YangPath parse(final String path) {
        final List<Element> elements = new ArrayList<>();
        if (path == null) {
            return new YangPath(elements);
        }
        for (final String raw : PATH_SEPARATOR.split(path)) {
            final String segment = raw.trim();
            if (segment.isEmpty()) {
                continue;
            }
            final int bracket = segment.indexOf('[');
            if (bracket < 0) {
                elements.add(new Element(segment, Map.of()));
            } else {
                final String name = segment.substring(0, bracket);
                final Map<String, String> keys = new LinkedHashMap<>();
                final Matcher m = KEY_PREDICATE.matcher(segment);
                while (m.find()) {
                    final String[] kv = m.group(1).split("=", 2);
                    keys.put(kv[0].trim(), stripQuotes(kv[1].trim()));
                }
                elements.add(new Element(name, keys));
            }
        }
        return new YangPath(elements);
    }

    private static String stripQuotes(final String value) {
        if (value.length() >= 2
                && ((value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'')
                || (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"'))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
