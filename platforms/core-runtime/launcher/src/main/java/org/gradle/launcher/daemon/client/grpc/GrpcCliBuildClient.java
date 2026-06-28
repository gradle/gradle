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
package org.gradle.launcher.daemon.client.grpc;

import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.launcher.daemon.client.DaemonClientConnection;
import org.gradle.launcher.daemon.client.DaemonConnector;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.tooling.grpc.proto.BuildEvent;
import org.gradle.tooling.grpc.proto.BuildRequest;
import org.gradle.tooling.grpc.proto.OutputLine;
import org.gradle.tooling.grpc.proto.Span;
import org.gradle.tooling.grpc.proto.ToolingGrpc;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;

/**
 * Prototype: drives a build from the Gradle CLI over the daemon's gRPC endpoint instead of the Kryo
 * protocol (the {@code --grpc} flag). Demonstrates that the CLI can reach the daemon over the same
 * gRPC contract used by the native client - a step toward unifying CLI + TAPI + native on one protocol.
 *
 * <p>Daemon discovery/start reuses the existing {@link DaemonConnector}; the gRPC port is read from
 * the daemon's side file; the token comes from the daemon's registry entry.
 */
public class GrpcCliBuildClient {

    private static final long ENDPOINT_WAIT_MILLIS = 10_000;

    public boolean runBuild(DaemonConnector connector, ExplainingSpec<DaemonContext> compatibilitySpec, File versionedDir, List<String> tasks, File projectDir) {
        DaemonClientConnection connection = connector.connect(compatibilitySpec);
        if (connection == null) {
            connection = connector.startDaemon(compatibilitySpec);
        }
        DaemonConnectDetails daemon = connection.getDaemon();
        String uid = daemon.getUid();
        String token = Base64.getEncoder().encodeToString(daemon.getToken());
        connection.stop();

        int port = awaitGrpcPort(versionedDir, uid);
        ManagedChannel channel = NettyChannelBuilder
            .forAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))
            .usePlaintext()
            .build();
        try {
            Metadata headers = new Metadata();
            headers.put(Metadata.Key.of("x-gradle-daemon-token", Metadata.ASCII_STRING_MARSHALLER), token);
            ToolingGrpc.ToolingBlockingStub stub = ToolingGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

            BuildRequest request = BuildRequest.newBuilder()
                .addAllArgs(tasks)
                .setProjectDir(projectDir.getAbsolutePath())
                .build();

            boolean success = false;
            Iterator<BuildEvent> events = stub.runBuild(request);
            while (events.hasNext()) {
                BuildEvent event = events.next();
                switch (event.getKindCase()) {
                    case OUTPUT:
                        System.out.print(withNewline(event.getOutput()));
                        break;
                    case STYLED:
                        StringBuilder builder = new StringBuilder();
                        for (Span span : event.getStyled().getSpansList()) {
                            builder.append(span.getText());
                        }
                        System.out.print(builder);
                        break;
                    case RESULT:
                        success = event.getResult().getSuccess();
                        break;
                    default:
                        break; // progress / not set
                }
            }
            System.out.flush();
            return success;
        } finally {
            channel.shutdownNow();
            try {
                channel.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String withNewline(OutputLine line) {
        String text = line.getText();
        return text.endsWith("\n") ? text : text + "\n";
    }

    private static int awaitGrpcPort(File versionedDir, String uid) {
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
