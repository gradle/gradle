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
package com.example.bridge;

import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * Entry point for the cross-version gRPC Tooling API bridge. Binds a gRPC server that speaks the same
 * contract the in-daemon server speaks, then serves it by driving a target Gradle version through the
 * classic Tooling API. Prints its endpoint on a single line so a client (or a launch script) can find
 * it, then blocks.
 *
 * <p>Usage: {@code BridgeMain [--gradle-version X] [--gradle-installation DIR] [--port N]}. With no
 * version or installation it drives whatever Gradle the target project's wrapper selects.</p>
 */
public final class BridgeMain {

    public static void main(String[] args) throws Exception {
        String gradleVersion = argValue(args, "--gradle-version", "");
        String gradleInstallation = argValue(args, "--gradle-installation", "");
        int port = Integer.parseInt(argValue(args, "--port", "0"));

        BridgeToolingService service = new BridgeToolingService(gradleVersion, gradleInstallation);
        Server server = ServerBuilder.forPort(port)
            .addService(service)
            .build()
            .start();

        String target = !gradleInstallation.isEmpty() ? gradleInstallation
            : !gradleVersion.isEmpty() ? "Gradle " + gradleVersion
            : "the project's wrapper";
        // Single machine-readable line the client/launch script greps for, plus a human note on stderr.
        System.out.println("BRIDGE_ENDPOINT 127.0.0.1:" + server.getPort());
        System.out.flush();
        System.err.println("[bridge] serving the gRPC tooling contract via classic Tooling API -> " + target);

        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
        server.awaitTermination();
    }

    private static String argValue(String[] args, String name, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private BridgeMain() {
    }
}
