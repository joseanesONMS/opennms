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

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.spi.source.URLYangTextSource;
import org.opendaylight.yangtools.yang.parser.api.YangParser;
import org.opendaylight.yangtools.yang.parser.ri.DefaultYangParserFactory;

/**
 * A compiled YANG schema (the import-closure of a model set) plus the resolution logic that turns a
 * gNMI-style {@link YangPath} into the typed facts the OpenConfig adapter needs — replacing the
 * hand-written Groovy mapping (NMS-19857, Phase 2).
 *
 * <p>Paths are resolved by local name across all modules in the context (gNMI paths do not name
 * modules), descending through containers and lists. List elements carrying {@code [key=value]}
 * predicates become {@link ResourceInstance}s (the OpenNMS resource keys); the terminal leaf yields
 * its type, units and a coarse {@link MetricKind}.</p>
 */
public final class YangSchemaContext {

    /** Coarse metric classification derived from the leaf's root built-in type. */
    public enum MetricKind { COUNTER, GAUGE, OTHER }

    /** A keyed list instance addressed along the path (e.g. {@code interface[name=eth0]}). */
    public record ResourceInstance(String listName, Map<String, String> keys) {
    }

    /** The result of resolving a path to a leaf. */
    public record ResolvedMetric(String leafName, String typeName, String baseTypeName, String units,
                                 MetricKind kind, List<ResourceInstance> resourceInstances) {
    }

    private final EffectiveModelContext context;

    public YangSchemaContext(final EffectiveModelContext context) {
        this.context = Objects.requireNonNull(context);
    }

    public EffectiveModelContext effectiveModelContext() {
        return context;
    }

    /** Compile a schema context from one or more classpath {@code .yang} resources. */
    public static YangSchemaContext fromResources(final Class<?> anchor, final String... resourceNames) throws Exception {
        final List<URL> urls = new ArrayList<>();
        for (final String name : resourceNames) {
            final URL url = anchor.getResource(name);
            Objects.requireNonNull(url, "YANG resource not found on classpath: " + name);
            urls.add(url);
        }
        return fromUrls(urls);
    }

    /** Compile a schema context from YANG source URLs (the closure must be complete). */
    public static YangSchemaContext fromUrls(final List<URL> urls) throws Exception {
        final YangParser parser = new DefaultYangParserFactory().createParser();
        for (final URL url : urls) {
            parser.addSource(new URLYangTextSource(url));
        }
        return new YangSchemaContext(parser.buildEffectiveModel());
    }

    /** A YANG module's source by name (the form a database registry yields). */
    public record NamedYangSource(String name, String content) {
    }

    /**
     * Compile a schema context from in-memory YANG sources — the bridge from the database registry
     * (a model set's modules) to the schema cache. Sources are materialized to a temporary directory
     * named {@code <module-name>.yang} so the parser can derive each source identifier; the closure
     * must be complete (all imports/includes present).
     */
    public static YangSchemaContext fromContents(final List<NamedYangSource> sources) throws Exception {
        final Path dir = Files.createTempDirectory("opennms-yang-");
        dir.toFile().deleteOnExit();
        final List<URL> urls = new ArrayList<>();
        for (final NamedYangSource source : sources) {
            final Path file = dir.resolve(source.name() + ".yang");
            Files.writeString(file, source.content());
            file.toFile().deleteOnExit();
            urls.add(file.toUri().toURL());
        }
        return fromUrls(urls);
    }

    /** Resolve a gNMI-style path to a typed metric (with its resource instances), if it ends at a leaf. */
    public Optional<ResolvedMetric> resolve(final YangPath path) {
        final List<YangPath.Element> elements = path.elements();
        if (elements.isEmpty()) {
            return Optional.empty();
        }
        final List<ResourceInstance> instances = new ArrayList<>();
        DataSchemaNode node = null;
        DataNodeContainer current = null;
        for (int i = 0; i < elements.size(); i++) {
            final YangPath.Element element = elements.get(i);
            node = (i == 0) ? findTopLevel(element.name()) : (current == null ? null : findChild(current, element.name()));
            if (node == null) {
                return Optional.empty();
            }
            if (node instanceof ListSchemaNode && !element.keys().isEmpty()) {
                instances.add(new ResourceInstance(element.name(), element.keys()));
            }
            current = (node instanceof DataNodeContainer dnc) ? dnc : null;
        }
        if (!(node instanceof LeafSchemaNode leaf)) {
            return Optional.empty();
        }
        return Optional.of(toMetric(leaf, instances));
    }

    private ResolvedMetric toMetric(final LeafSchemaNode leaf, final List<ResourceInstance> instances) {
        final TypeDefinition<?> type = leaf.typeDefinition();
        final String baseName = rootType(type).getQName().getLocalName();
        return new ResolvedMetric(
                leaf.getQName().getLocalName(),
                type.getQName().getLocalName(),
                baseName,
                firstUnits(type),
                classify(baseName),
                List.copyOf(instances));
    }

    private DataSchemaNode findTopLevel(final String localName) {
        for (final Module module : context.getModules()) {
            final DataSchemaNode found = findChild(module, localName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static DataSchemaNode findChild(final DataNodeContainer container, final String localName) {
        for (final DataSchemaNode child : container.getChildNodes()) {
            if (child.getQName().getLocalName().equals(localName)) {
                return child;
            }
        }
        return null;
    }

    private static TypeDefinition<?> rootType(final TypeDefinition<?> type) {
        TypeDefinition<?> current = type;
        while (current.getBaseType() != null) {
            current = current.getBaseType();
        }
        return current;
    }

    private static String firstUnits(final TypeDefinition<?> type) {
        for (TypeDefinition<?> t = type; t != null; t = t.getBaseType()) {
            final Optional<String> units = t.getUnits();
            if (units.isPresent()) {
                return units.get();
            }
        }
        return null;
    }

    private static MetricKind classify(final String baseTypeName) {
        return switch (baseTypeName) {
            case "uint8", "uint16", "uint32", "uint64" -> MetricKind.COUNTER;
            case "int8", "int16", "int32", "int64", "decimal64" -> MetricKind.GAUGE;
            default -> MetricKind.OTHER;
        };
    }
}
