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
package org.opennms.netmgt.telemetry.daemon;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opennms.features.scv.api.Credentials;
import org.opennms.features.scv.api.SecureCredentialsVault;
import org.opennms.netmgt.dao.api.OpenConfigSubscriptionProfileDao;
import org.opennms.netmgt.dao.api.OpenConfigTargetDao;
import org.opennms.netmgt.model.OpenConfigSubscriptionProfile;
import org.opennms.netmgt.model.OpenConfigTarget;
import org.opennms.netmgt.telemetry.config.model.AdapterConfig;
import org.opennms.netmgt.telemetry.config.model.ConnectorConfig;
import org.opennms.netmgt.telemetry.config.model.PackageConfig;
import org.opennms.netmgt.telemetry.config.model.Parameter;
import org.opennms.netmgt.telemetry.config.model.QueueConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Materializes telemetryd connector/queue/adapter configuration from the database-backed OpenConfig
 * subscription profiles and targets (NMS-19857), so that telemetryd can drive OpenConfig streaming
 * collection from the database rather than {@code telemetryd-configuration.xml}.
 *
 * <p>This is read on every {@code Telemetryd.start()} — including the restart triggered by a
 * {@code reloadDaemonConfig} event — so a fresh view of the database is always used. Targets that
 * belong to the same {@code transport} share a connector/queue/adapter chain; each enabled target
 * becomes a {@link PackageConfig} (filter + interpolatable parameters). Credentials are resolved
 * from the Secure Credentials Vault by alias and injected as parameters; the secrets are never
 * stored in the database.</p>
 */
public class OpenConfigDbConfigSource {

    private static final Logger LOG = LoggerFactory.getLogger(OpenConfigDbConfigSource.class);

    private static final String SERVICE_NAME = "OpenConfig";
    private static final String CONNECTOR_CLASS = "org.opennms.netmgt.telemetry.protocols.openconfig.connector.OpenConfigConnector";
    private static final String ADAPTER_CLASS = "org.opennms.netmgt.telemetry.protocols.openconfig.adapter.OpenConfigAdapter";
    private static final String DEFAULT_FILTER = "IPADDR != '0.0.0.0'";
    private static final int DEFAULT_PORT = 9000;
    private static final List<String> DEFAULT_RRAS = List.of(
            "RRA:AVERAGE:0.5:1:2016",
            "RRA:AVERAGE:0.5:12:1488",
            "RRA:AVERAGE:0.5:288:366",
            "RRA:MAX:0.5:288:366",
            "RRA:MIN:0.5:288:366");

    private final OpenConfigSubscriptionProfileDao profileDao;
    private final OpenConfigTargetDao targetDao;
    private final SecureCredentialsVault secureCredentialsVault;
    private final TransactionOperations transactionOperations;

    public OpenConfigDbConfigSource(final OpenConfigSubscriptionProfileDao profileDao,
                                    final OpenConfigTargetDao targetDao,
                                    final SecureCredentialsVault secureCredentialsVault,
                                    final TransactionOperations transactionOperations) {
        this.profileDao = profileDao;
        this.targetDao = targetDao;
        this.secureCredentialsVault = secureCredentialsVault;
        this.transactionOperations = transactionOperations;
    }

    /** The materialized OpenConfig portion of the telemetryd configuration. */
    public static final class Materialized {
        private final List<ConnectorConfig> connectors;
        private final List<QueueConfig> queues;

        Materialized(List<ConnectorConfig> connectors, List<QueueConfig> queues) {
            this.connectors = connectors;
            this.queues = queues;
        }

        public List<ConnectorConfig> getConnectors() {
            return connectors;
        }

        public List<QueueConfig> getQueues() {
            return queues;
        }

        public boolean isEmpty() {
            return connectors.isEmpty() && queues.isEmpty();
        }
    }

    public Materialized materialize() {
        return transactionOperations.execute(status -> doMaterialize());
    }

    private Materialized doMaterialize() {
        final List<ConnectorConfig> connectors = new ArrayList<>();
        final List<QueueConfig> queues = new ArrayList<>();

        // Group active (profile, target) pairs by transport so each transport gets its own
        // connector -> queue -> adapter chain (a queue's adapter parses a single mode).
        // Grouped by (mode, modelSet): a queue's adapter parses a single mode and decodes with a
        // single model set (or the Groovy script when no model set is bound).
        final Map<GroupKey, List<OpenConfigTarget>> targetsByGroup = new LinkedHashMap<>();
        final Map<GroupKey, OpenConfigSubscriptionProfile> firstProfileByGroup = new LinkedHashMap<>();

        for (final OpenConfigSubscriptionProfile profile : profileDao.findAllEnabled()) {
            final GroupKey key = new GroupKey(modeForTransport(profile.getTransport()), blankToNull(profile.getModelSet()));
            for (final OpenConfigTarget target : targetDao.findAllEnabledByProfile(profile.getId())) {
                targetsByGroup.computeIfAbsent(key, k -> new ArrayList<>()).add(target);
                firstProfileByGroup.putIfAbsent(key, profile);
            }
        }

        for (final Map.Entry<GroupKey, List<OpenConfigTarget>> entry : targetsByGroup.entrySet()) {
            final GroupKey key = entry.getKey();
            final List<OpenConfigTarget> targets = entry.getValue();
            final OpenConfigSubscriptionProfile rrdProfile = firstProfileByGroup.get(key);
            final String groupId = groupId(key);

            final QueueConfig queue = buildQueue(groupId, key, rrdProfile);
            queues.add(queue);

            final ConnectorConfig connector = new ConnectorConfig();
            connector.setName("OpenConfig-" + groupId + "-Connector");
            connector.setClassName(CONNECTOR_CLASS);
            connector.setServiceName(SERVICE_NAME);
            connector.setQueue(queue);
            connector.setEnabled(true);

            for (final OpenConfigTarget target : targets) {
                final PackageConfig pkg = buildConnectorPackage(target);
                if (pkg != null) {
                    connector.getPackages().add(pkg);
                }
            }

            if (!connector.getPackages().isEmpty()) {
                connectors.add(connector);
            }
        }

        LOG.debug("Materialized {} OpenConfig connector(s) and {} queue(s) from the database.",
                connectors.size(), queues.size());
        return new Materialized(connectors, queues);
    }

    private QueueConfig buildQueue(final String groupId, final GroupKey key, final OpenConfigSubscriptionProfile rrdProfile) {
        final QueueConfig queue = new QueueConfig();
        queue.setName("OpenConfig-" + groupId);

        final AdapterConfig adapter = new AdapterConfig();
        adapter.setName("OpenConfig-" + groupId + "-Adapter");
        adapter.setClassName(ADAPTER_CLASS);
        adapter.setEnabled(true);
        adapter.setQueue(queue);
        adapter.getParameters().add(new Parameter("script", scriptPathForMode(key.mode())));
        adapter.getParameters().add(new Parameter("mode", key.mode()));
        // When a model set is bound, the adapter decodes via YANG instead of the Groovy script.
        if (key.modelSet() != null) {
            adapter.getParameters().add(new Parameter("modelSet", key.modelSet()));
        }

        final PackageConfig adapterPackage = new PackageConfig();
        adapterPackage.setName("OpenConfig-" + groupId + "-Default");
        final PackageConfig.Rrd rrd = new PackageConfig.Rrd();
        rrd.setStep(rrdProfile != null && rrdProfile.getRrdStep() != null ? rrdProfile.getRrdStep() : 300);
        rrd.setRras(rrasFor(rrdProfile));
        adapterPackage.setRrd(rrd);
        adapter.getPackages().add(adapterPackage);

        queue.getAdapters().add(adapter);
        return queue;
    }

    private PackageConfig buildConnectorPackage(final OpenConfigTarget target) {
        final OpenConfigSubscriptionProfile profile = target.getProfile();
        final String filter = filterFor(target);
        if (filter == null) {
            LOG.warn("OpenConfig target '{}' (id={}) has match-type NODES but no node ids; skipping.",
                    target.getName(), target.getId());
            return null;
        }

        final PackageConfig pkg = new PackageConfig();
        pkg.setName("oc-" + profile.getId() + "-" + target.getId());
        pkg.setFilter(new PackageConfig.Filter(filter));

        final List<Parameter> params = pkg.getParameters();
        final int port = target.getPort() != null ? target.getPort() : DEFAULT_PORT;
        params.add(new Parameter("port", Integer.toString(port)));
        params.add(new Parameter("mode", modeForTransport(profile.getTransport())));
        if (profile.getPaths() != null && !profile.getPaths().isBlank()) {
            params.add(new Parameter("paths", profile.getPaths()));
        }
        if (profile.getSampleInterval() != null) {
            params.add(new Parameter("frequency", Long.toString(profile.getSampleInterval())));
        }
        if (profile.getOrigin() != null && !profile.getOrigin().isBlank()) {
            params.add(new Parameter("origin", profile.getOrigin()));
        }
        addCredentialParameters(target, params);
        return pkg;
    }

    private void addCredentialParameters(final OpenConfigTarget target, final List<Parameter> params) {
        if (secureCredentialsVault == null) {
            return;
        }
        if (target.getCredentialRef() != null && !target.getCredentialRef().isBlank()) {
            final Credentials creds = secureCredentialsVault.getCredentials(target.getCredentialRef());
            if (creds != null) {
                if (creds.getUsername() != null) {
                    params.add(new Parameter("username", creds.getUsername()));
                }
                if (creds.getPassword() != null) {
                    params.add(new Parameter("password", creds.getPassword()));
                }
            } else {
                LOG.warn("OpenConfig target '{}' references unknown credential alias '{}'.",
                        target.getName(), target.getCredentialRef());
            }
        }
        if (target.getTlsRef() != null && !target.getTlsRef().isBlank()) {
            final Credentials tls = secureCredentialsVault.getCredentials(target.getTlsRef());
            if (tls != null) {
                // The OpenConfig client picks up any parameter whose key contains "tls".
                for (final Map.Entry<String, String> attr : tls.getAttributes().entrySet()) {
                    params.add(new Parameter(attr.getKey(), attr.getValue()));
                }
            } else {
                LOG.warn("OpenConfig target '{}' references unknown TLS alias '{}'.",
                        target.getName(), target.getTlsRef());
            }
        }
    }

    private static String filterFor(final OpenConfigTarget target) {
        if ("NODES".equalsIgnoreCase(target.getMatchType())) {
            if (target.getNodeIds() == null || target.getNodeIds().isBlank()) {
                return null;
            }
            final List<String> clauses = new ArrayList<>();
            for (final String raw : target.getNodeIds().split(",")) {
                final String id = raw.trim();
                if (!id.isEmpty()) {
                    clauses.add("nodeId == " + id);
                }
            }
            return clauses.isEmpty() ? null : "(" + String.join(" || ", clauses) + ")";
        }
        // FILTER (default)
        final String rule = target.getFilterRule();
        return (rule != null && !rule.isBlank()) ? rule : DEFAULT_FILTER;
    }

    private static List<String> rrasFor(final OpenConfigSubscriptionProfile profile) {
        if (profile == null || profile.getRrdRras() == null || profile.getRrdRras().isBlank()) {
            return new ArrayList<>(DEFAULT_RRAS);
        }
        final List<String> rras = new ArrayList<>();
        for (final String line : profile.getRrdRras().split("[\\r\\n,]+")) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                rras.add(trimmed);
            }
        }
        return rras.isEmpty() ? new ArrayList<>(DEFAULT_RRAS) : rras;
    }

    /** Groups targets that share a wire mode and YANG model set into one connector/queue/adapter chain. */
    private record GroupKey(String mode, String modelSet) {
    }

    private static String groupId(final GroupKey key) {
        return key.modelSet() == null
                ? key.mode()
                : key.mode() + "-" + key.modelSet().replaceAll("[^A-Za-z0-9]", "_");
    }

    private static String blankToNull(final String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String modeForTransport(final String transport) {
        return "JTI".equalsIgnoreCase(transport) ? "jti" : "gnmi";
    }

    private static String scriptPathForMode(final String mode) {
        final String home = System.getProperty("opennms.home", "/opt/opennms");
        return Paths.get(home, "etc", "telemetryd-adapters", "openconfig-" + mode + "-telemetry.groovy").toString();
    }
}
