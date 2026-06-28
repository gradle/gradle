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
package org.gradle.launcher.daemon.server.grpc;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.LoggingOutputInternal;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;
import org.gradle.launcher.exec.BuildExecutor;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Prototype gRPC server hosted inside the daemon process (Target beta). Binds to a loopback
 * ephemeral port, authenticates with the daemon's token, and advertises its port by writing
 * {@code <daemonVersionedDir>/<uid>.grpcport} so the {@code gradle --grpc-endpoint} helper can find it.
 *
 * <p>Advertising via a side file (rather than the daemon startup greeting + registry) is a deliberate
 * prototype simplification to avoid changing the daemon startup protocol that every build depends on.
 */
public class GrpcDaemonServer implements Stoppable {

    private static final Logger LOGGER = Logging.getLogger(GrpcDaemonServer.class);

    private final BuildExecutor buildExecutor;
    private final LoggingOutputInternal loggingOutput;
    private final File daemonVersionedDir;
    private final String uid;

    private @Nullable Server server;
    private @Nullable File endpointFile;

    public GrpcDaemonServer(BuildExecutor buildExecutor, LoggingOutputInternal loggingOutput, File daemonVersionedDir, String uid) {
        this.buildExecutor = buildExecutor;
        this.loggingOutput = loggingOutput;
        this.daemonVersionedDir = daemonVersionedDir;
        this.uid = uid;
    }

    public void start(byte[] token, DaemonStateControl stateControl) {
        try {
            ToolingServiceImpl service = new ToolingServiceImpl(buildExecutor, loggingOutput, stateControl);
            Server started = NettyServerBuilder.forAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .addService(ServerInterceptors.intercept(service, new TokenAuthInterceptor(token)))
                .build()
                .start();
            this.server = started;
            int port = started.getPort();
            File file = new File(daemonVersionedDir, uid + ".grpcport");
            Files.write(file.toPath(), Integer.toString(port).getBytes(StandardCharsets.UTF_8));
            this.endpointFile = file;
            LOGGER.lifecycle("gRPC tooling API listening on 127.0.0.1:" + port);
        } catch (Exception e) {
            LOGGER.warn("Failed to start the gRPC tooling API server; the daemon will continue without it.", e);
        }
    }

    @Override
    public void stop() {
        Server toStop = this.server;
        if (toStop != null) {
            toStop.shutdownNow();
            this.server = null;
        }
        File file = this.endpointFile;
        if (file != null) {
            if (!file.delete()) {
                LOGGER.debug("Could not delete gRPC endpoint file {}", file);
            }
            this.endpointFile = null;
        }
    }
}
