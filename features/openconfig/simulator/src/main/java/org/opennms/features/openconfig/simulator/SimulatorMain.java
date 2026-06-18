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

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

/**
 * Standalone gNMI simulator entry point.
 *
 * Usage:
 *   java -jar gnmi-simulator-standalone.jar [port] [intervalMs] [iface1,iface2,...]
 *
 * Defaults: port=50051, intervalMs=10000, interfaces=eth0,eth1,eth2
 *
 * Example (Docker/CI):
 *   java -jar gnmi-simulator-standalone.jar 50051 5000 eth0,eth1,eth2,eth3
 */
public class SimulatorMain {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatorMain.class);

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 50051;
        long intervalMs = args.length > 1 ? Long.parseLong(args[1]) : 10_000L;
        List<String> interfaces = args.length > 2
                ? Arrays.asList(args[2].split(","))
                : List.of("eth0", "eth1", "eth2");

        GnmiSimulator service = new GnmiSimulator(interfaces, intervalMs);

        Server server = NettyServerBuilder.forPort(port)
                .addService(service)
                .build()
                .start();

        LOG.info("gNMI simulator listening on port {} — {} interfaces, {}ms interval",
                port, interfaces.size(), intervalMs);
        LOG.info("Interfaces: {}", interfaces);
        LOG.info("Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down gNMI simulator...");
            service.shutdown();
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
