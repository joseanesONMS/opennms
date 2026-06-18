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

import java.util.Objects;

import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder;
import org.opennms.features.openconfig.yang.YangSchemaContext;
import org.opennms.features.openconfig.yang.YangSchemaContextCache;

/**
 * Entry point for schema-driven OpenConfig decoding (NMS-19857, Phase 2): given a model-set name it
 * returns the compiled {@link YangSchemaContext} (cached) or a ready {@link OpenConfigGnmiDecoder}.
 * Backed by a {@link YangSchemaContextCache} over a {@link DbYangSourceProvider}, so the adapter can
 * obtain a decoder per subscription's model set without knowing anything about YANG parsing.
 */
public final class YangRegistry {

    private final YangSchemaContextCache cache;

    public YangRegistry(final YangSchemaContextCache.YangSourceProvider sourceProvider) {
        this.cache = new YangSchemaContextCache(Objects.requireNonNull(sourceProvider));
    }

    /** Compile (or fetch cached) the schema for a model set. */
    public YangSchemaContext schemaFor(final String modelSetName) throws Exception {
        return cache.get(modelSetName);
    }

    /** A decoder bound to a model set's schema. */
    public OpenConfigGnmiDecoder decoderFor(final String modelSetName) throws Exception {
        return new OpenConfigGnmiDecoder(schemaFor(modelSetName));
    }

    /** Drop the cached schema for a model set (call when its modules change). */
    public void invalidate(final String modelSetName) {
        cache.invalidate(modelSetName);
    }
}
