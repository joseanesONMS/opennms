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
package org.opennms.features.openconfig.simulator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.opennms.features.openconfig.proto.gnmi.Gnmi;
import org.opennms.features.openconfig.proto.gnmi.gNMIGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;

/**
 * gNMI service implementation that streams fake openconfig-interfaces counters.
 *
 * Generated paths (openconfig-interfaces shape):
 *   /interfaces/interface[name=<iface>]/state/counters/in-octets
 *   /interfaces/interface[name=<iface>]/state/counters/out-octets
 *   /interfaces/interface[name=<iface>]/state/counters/in-unicast-pkts
 *   /interfaces/interface[name=<iface>]/state/counters/out-unicast-pkts
 *   /interfaces/interface[name=<iface>]/state/counters/in-errors
 *   /interfaces/interface[name=<iface>]/state/counters/out-errors
 */
public class GnmiSimulator extends gNMIGrpc.gNMIImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(GnmiSimulator.class);

    private final List<String> interfaces;
    private final long intervalMs;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Per-interface per-metric counters — increment each tick so they look like real counters
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    public GnmiSimulator(List<String> interfaces, long intervalMs) {
        this.interfaces = interfaces;
        this.intervalMs = intervalMs;
    }

    @Override
    public StreamObserver<Gnmi.SubscribeRequest> subscribe(
            StreamObserver<Gnmi.SubscribeResponse> responseObserver) {

        LOG.info("New gNMI subscription; streaming {} interfaces every {}ms", interfaces.size(), intervalMs);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                Gnmi.SubscribeResponse response = buildResponse();
                responseObserver.onNext(response);
            } catch (Exception e) {
                LOG.warn("Error sending subscription update", e);
                responseObserver.onError(e);
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        return new StreamObserver<Gnmi.SubscribeRequest>() {
            @Override
            public void onNext(Gnmi.SubscribeRequest request) {
                LOG.debug("Received SubscribeRequest (ignored by simulator)");
            }

            @Override
            public void onError(Throwable t) {
                LOG.info("Subscription stream error: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                LOG.info("Subscription stream completed by client");
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void capabilities(Gnmi.CapabilityRequest request,
            StreamObserver<Gnmi.CapabilityResponse> responseObserver) {
        Gnmi.ModelData model = Gnmi.ModelData.newBuilder()
                .setName("openconfig-interfaces")
                .setOrganization("OpenConfig working group")
                .setVersion("2.4.3")
                .build();
        responseObserver.onNext(Gnmi.CapabilityResponse.newBuilder()
                .addSupportedModels(model)
                .setGNMIVersion("0.7.0")
                .build());
        responseObserver.onCompleted();
    }

    private Gnmi.SubscribeResponse buildResponse() {
        long timestampNs = System.currentTimeMillis() * 1_000_000L;
        Gnmi.Notification.Builder notification = Gnmi.Notification.newBuilder()
                .setTimestamp(timestampNs);

        for (String iface : interfaces) {
            notification.addUpdate(makeUpdate(iface, "in-octets",        increment(iface, "in-octets",       1400)));
            notification.addUpdate(makeUpdate(iface, "out-octets",       increment(iface, "out-octets",       800)));
            notification.addUpdate(makeUpdate(iface, "in-unicast-pkts",  increment(iface, "in-unicast-pkts",   10)));
            notification.addUpdate(makeUpdate(iface, "out-unicast-pkts", increment(iface, "out-unicast-pkts",   6)));
            notification.addUpdate(makeUpdate(iface, "in-errors",        increment(iface, "in-errors",           0)));
            notification.addUpdate(makeUpdate(iface, "out-errors",       increment(iface, "out-errors",          0)));
        }

        return Gnmi.SubscribeResponse.newBuilder()
                .setUpdate(notification.build())
                .build();
    }

    private Gnmi.Update makeUpdate(String iface, String leaf, long value) {
        Gnmi.Path path = Gnmi.Path.newBuilder()
                .addElem(Gnmi.PathElem.newBuilder().setName("interfaces").build())
                .addElem(Gnmi.PathElem.newBuilder().setName("interface").putKey("name", iface).build())
                .addElem(Gnmi.PathElem.newBuilder().setName("state").build())
                .addElem(Gnmi.PathElem.newBuilder().setName("counters").build())
                .addElem(Gnmi.PathElem.newBuilder().setName(leaf).build())
                .build();
        Gnmi.TypedValue val = Gnmi.TypedValue.newBuilder().setUintVal(value).build();
        return Gnmi.Update.newBuilder().setPath(path).setVal(val).build();
    }

    /** Increment the counter for this interface+leaf by delta and return the new value. */
    private long increment(String iface, String leaf, long delta) {
        String key = iface + "/" + leaf;
        return counters.computeIfAbsent(key, k -> new AtomicLong(0))
                       .addAndGet(delta);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
