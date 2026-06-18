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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.ServerSocket;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opennms.features.openconfig.proto.gnmi.Gnmi;
import org.opennms.features.openconfig.proto.gnmi.gNMIGrpc;
import org.opennms.features.openconfig.simulator.GnmiSimulator;
import org.opennms.features.openconfig.yang.OpenConfigGnmiDecoder;
import org.opennms.features.openconfig.yang.YangSchemaContext;
import org.opennms.features.openconfig.yang.YangSchemaContext.MetricKind;
import org.opennms.netmgt.collection.api.CollectionAgent;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.CollectionSetVisitor;
import org.opennms.netmgt.collection.api.CollectionStatus;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

/**
 * End-to-end integration test: GnmiSimulator → gRPC Subscribe → GnmiTelemetryFlattener
 * → OpenConfigGnmiDecoder → SchemaDrivenCollectionSetBuilder.
 *
 * No Spring context required.  Uses the real gRPC transport so the test also validates
 * the wire format produced by the simulator.
 */
public class OpenConfigEndToEndTest {

    private int port;
    private GnmiSimulator simulator;
    private Server server;
    private ManagedChannel channel;

    @Before
    public void setUp() throws Exception {
        // Pick a free port
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        simulator = new GnmiSimulator(List.of("eth0", "eth1"), 200);
        server = NettyServerBuilder.forPort(port)
                .addService(simulator)
                .build()
                .start();
        channel = NettyChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
    }

    @After
    public void tearDown() {
        simulator.shutdown();
        channel.shutdownNow();
        server.shutdown();
    }

    @Test
    public void simulatorStreamsGnmiDataThroughFullPipeline() throws Exception {
        // Receive one SubscribeResponse from the simulator
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Gnmi.SubscribeResponse> received = new AtomicReference<>();

        gNMIGrpc.gNMIStub stub = gNMIGrpc.newStub(channel);
        StreamObserver<Gnmi.SubscribeRequest> requestObs = stub.subscribe(
                new StreamObserver<Gnmi.SubscribeResponse>() {
                    @Override
                    public void onNext(Gnmi.SubscribeResponse r) {
                        received.compareAndSet(null, r);
                        latch.countDown();
                    }
                    @Override public void onError(Throwable t) { latch.countDown(); }
                    @Override public void onCompleted() {}
                });

        requestObs.onNext(Gnmi.SubscribeRequest.newBuilder()
                .setSubscribe(Gnmi.SubscriptionList.newBuilder()
                        .setMode(Gnmi.SubscriptionList.Mode.STREAM))
                .build());

        assertTrue("No response from simulator within 2s", latch.await(2, TimeUnit.SECONDS));
        requestObs.onCompleted();

        Gnmi.SubscribeResponse response = received.get();
        assertTrue("Response must carry an update notification", response.hasUpdate());

        // --- Step 1: Flatten ---
        List<OpenConfigGnmiDecoder.Update> updates = GnmiTelemetryFlattener.flatten(response);
        // 2 interfaces × 6 counters = 12 updates
        assertEquals(12, updates.size());

        // Verify the path format is what the decoder expects
        boolean foundInOctets = updates.stream()
                .anyMatch(u -> u.path().equals("/interfaces/interface[name=eth0]/state/counters/in-octets"));
        assertTrue("Expected in-octets path for eth0", foundInOctets);

        // --- Step 2: Decode against the oc-interfaces YANG schema ---
        YangSchemaContext schema = YangSchemaContext.fromResources(getClass(),
                "/closure/oc-types.yang", "/closure/oc-interfaces.yang");
        OpenConfigGnmiDecoder decoder = new OpenConfigGnmiDecoder(schema);

        List<OpenConfigGnmiDecoder.DecodedMetric> metrics = decoder.decode(updates);
        // in-octets is defined in oc-interfaces.yang; out-unicast-pkts and others not yet in the
        // minimal fixture — only in-octets should decode (other leaves are unknown to this fixture)
        assertFalse("Expected at least one decoded metric", metrics.isEmpty());

        OpenConfigGnmiDecoder.DecodedMetric inOctets = metrics.stream()
                .filter(m -> "in-octets".equals(m.metricName()) && "eth0".equals(m.resourceInstance()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No in-octets metric for eth0"));

        assertEquals("interface", inOctets.resourceType());
        assertEquals(MetricKind.COUNTER, inOctets.kind());
        assertEquals("octets", inOctets.units());
        assertTrue("Counter must be positive after first tick", inOctets.value().longValue() > 0);

        // --- Step 3: Build CollectionSet ---
        CollectionAgent agent = mock(CollectionAgent.class);
        when(agent.getNodeId()).thenReturn(1);
        CollectionSet cs = SchemaDrivenCollectionSetBuilder.build(agent, metrics, new Date());
        assertEquals(CollectionStatus.SUCCEEDED, cs.getStatus());

        // Confirm there is at least one numeric attribute in the set
        final int[] attrCount = {0};
        cs.visit(new CollectionSetVisitor() {
            @Override public void visitCollectionSet(CollectionSet cs) {}
            @Override public void completeCollectionSet(CollectionSet cs) {}
            @Override public void visitResource(org.opennms.netmgt.collection.api.CollectionResource r) {}
            @Override public void completeResource(org.opennms.netmgt.collection.api.CollectionResource r) {}
            @Override public void visitAttribute(org.opennms.netmgt.collection.api.CollectionAttribute a) {
                attrCount[0]++;
            }
            @Override public void completeAttribute(org.opennms.netmgt.collection.api.CollectionAttribute a) {}
            @Override public void visitGroup(org.opennms.netmgt.collection.api.AttributeGroup g) {}
            @Override public void completeGroup(org.opennms.netmgt.collection.api.AttributeGroup g) {}
        });
        assertTrue("CollectionSet must contain at least one numeric attribute", attrCount[0] > 0);
    }

    @Test
    public void simulatorCapabilitiesRpcWorks() throws Exception {
        gNMIGrpc.gNMIBlockingStub stub = gNMIGrpc.newBlockingStub(channel);
        Gnmi.CapabilityResponse caps = stub.capabilities(
                Gnmi.CapabilityRequest.getDefaultInstance());
        assertFalse("Capabilities must list at least one model",
                caps.getSupportedModelsList().isEmpty());
        assertEquals("openconfig-interfaces", caps.getSupportedModels(0).getName());
    }
}
