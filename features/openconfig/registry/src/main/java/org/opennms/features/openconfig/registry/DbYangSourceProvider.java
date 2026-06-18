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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.opennms.features.openconfig.yang.YangSchemaContext.NamedYangSource;
import org.opennms.features.openconfig.yang.YangSchemaContextCache;
import org.opennms.netmgt.model.YangModelSet;
import org.opennms.netmgt.model.YangModule;

/**
 * Feeds the {@link YangSchemaContextCache} from the database YANG registry: resolves a model-set
 * name to its {@link YangModelSet} and hands the cache that set's modules as in-memory sources
 * (NMS-19857, Phase 2). Decoupled from the (fat) DAO via a narrow {@link ModelSetLookup} so it needs
 * only {@code opennms-model} — production wiring supplies {@code name -> yangModelSetDao.findByName(name)}
 * (executed within a transaction, since a model set's modules are lazily loaded).
 */
public final class DbYangSourceProvider implements YangSchemaContextCache.YangSourceProvider {

    /** Narrow lookup from a model-set name to its (modules-populated) entity. */
    @FunctionalInterface
    public interface ModelSetLookup {
        YangModelSet byName(String name);
    }

    private final ModelSetLookup lookup;

    public DbYangSourceProvider(final ModelSetLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup);
    }

    @Override
    public List<NamedYangSource> sourcesFor(final String modelSetName) {
        final YangModelSet modelSet = lookup.byName(modelSetName);
        if (modelSet == null) {
            throw new IllegalArgumentException("Unknown YANG model set: " + modelSetName);
        }
        final List<NamedYangSource> sources = new ArrayList<>();
        for (final YangModule module : modelSet.getModules()) {
            sources.add(new NamedYangSource(module.getName(), module.getContent()));
        }
        return sources;
    }
}
