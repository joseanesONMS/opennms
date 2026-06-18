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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opennms.features.openconfig.yang.YangSchemaContext.NamedYangSource;

/**
 * Caches compiled {@link YangSchemaContext}s per model set, building each lazily on first use
 * (NMS-19857, Phase 2). The {@link YangSourceProvider} seam decouples where the YANG comes from:
 * the spike feeds classpath URLs; the eventual registry will feed the import-closure of a database
 * model set. Compiling a closure is relatively expensive, so contexts are cached until invalidated
 * (e.g. when a model set's modules change).
 */
public final class YangSchemaContextCache {

    /** Supplies the complete YANG source closure (by name + content) for a model-set identifier. */
    @FunctionalInterface
    public interface YangSourceProvider {
        List<NamedYangSource> sourcesFor(String modelSetId) throws Exception;
    }

    private final YangSourceProvider provider;
    private final ConcurrentMap<String, YangSchemaContext> cache = new ConcurrentHashMap<>();

    public YangSchemaContextCache(final YangSourceProvider provider) {
        this.provider = Objects.requireNonNull(provider);
    }

    /** Returns the (cached) compiled schema for a model set, building it on first request. */
    public YangSchemaContext get(final String modelSetId) throws Exception {
        YangSchemaContext context = cache.get(modelSetId);
        if (context == null) {
            synchronized (this) {
                context = cache.get(modelSetId);
                if (context == null) {
                    context = YangSchemaContext.fromContents(provider.sourcesFor(modelSetId));
                    cache.put(modelSetId, context);
                }
            }
        }
        return context;
    }

    /** True if a compiled context is currently cached for the model set. */
    public boolean isCached(final String modelSetId) {
        return cache.containsKey(modelSetId);
    }

    /** Drop the cached context for a model set (call when its modules change). */
    public void invalidate(final String modelSetId) {
        cache.remove(modelSetId);
    }

    public void invalidateAll() {
        cache.clear();
    }
}
