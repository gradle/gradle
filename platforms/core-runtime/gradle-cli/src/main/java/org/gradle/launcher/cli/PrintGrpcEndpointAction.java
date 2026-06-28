/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.launcher.cli;

import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.launcher.daemon.client.DaemonClientConnection;
import org.gradle.launcher.daemon.client.DaemonConnector;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Prototype (Target beta) helper for the {@code --grpc-endpoint} flag. Finds or starts a daemon, then
 * prints its gRPC tooling API endpoint and authentication token to stdout in the form
 * {@code 127.0.0.1:<port> <base64-token>}, which a non-JVM client parses to dial the daemon.
 *
 * <p>The gRPC port is read from the side file the daemon writes ({@code <versionedDir>/<uid>.grpcport});
 * the token comes from the daemon's existing registry entry via the connection.
 */
public class PrintGrpcEndpointAction implements Runnable {

    private static final long ENDPOINT_WAIT_MILLIS = 10000;

    private final DaemonConnector connector;
    private final ExplainingSpec<DaemonContext> compatibilitySpec;
    private final File versionedDir;
    private final Stoppable stoppable;

    public PrintGrpcEndpointAction(DaemonConnector connector, ExplainingSpec<DaemonContext> compatibilitySpec, File versionedDir, Stoppable stoppable) {
        this.connector = connector;
        this.compatibilitySpec = compatibilitySpec;
        this.versionedDir = versionedDir;
        this.stoppable = stoppable;
    }

    @Override
    public void run() {
        try {
            DaemonClientConnection connection = connector.connect(compatibilitySpec);
            if (connection == null) {
                connection = connector.startDaemon(compatibilitySpec);
            }
            DaemonConnectDetails daemon = connection.getDaemon();
            String uid = daemon.getUid();
            String token = Base64.getEncoder().encodeToString(daemon.getToken());
            connection.stop();

            int port = awaitGrpcPort(uid);
            System.out.println("127.0.0.1:" + port + " " + token);
        } finally {
            stoppable.stop();
        }
    }

    private int awaitGrpcPort(String uid) {
        File file = new File(versionedDir, uid + ".grpcport");
        long deadline = System.currentTimeMillis() + ENDPOINT_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (file.isFile()) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
                    if (!content.isEmpty()) {
                        return Integer.parseInt(content);
                    }
                } catch (Exception ignore) {
                    // file may be mid-write; retry until the deadline
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("Timed out waiting for the daemon to advertise its gRPC port at " + file);
    }
}
