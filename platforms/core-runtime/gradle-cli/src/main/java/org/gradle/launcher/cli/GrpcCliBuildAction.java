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
import org.gradle.initialization.ReportedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.launcher.daemon.client.DaemonConnector;
import org.gradle.launcher.daemon.client.grpc.GrpcCliBuildClient;
import org.gradle.launcher.daemon.context.DaemonContext;

import java.io.File;
import java.util.List;

/**
 * Prototype (Target beta) action for the {@code --grpc} flag: runs the build by talking to the daemon
 * over the gRPC tooling API instead of the internal Kryo protocol. Demonstrates CLI-over-gRPC.
 */
public class GrpcCliBuildAction implements Runnable {

    private final DaemonConnector connector;
    private final ExplainingSpec<DaemonContext> compatibilitySpec;
    private final File versionedDir;
    private final List<String> tasks;
    private final File projectDir;
    private final Stoppable stoppable;

    public GrpcCliBuildAction(DaemonConnector connector, ExplainingSpec<DaemonContext> compatibilitySpec, File versionedDir, List<String> tasks, File projectDir, Stoppable stoppable) {
        this.connector = connector;
        this.compatibilitySpec = compatibilitySpec;
        this.versionedDir = versionedDir;
        this.tasks = tasks;
        this.projectDir = projectDir;
        this.stoppable = stoppable;
    }

    @Override
    public void run() {
        try {
            boolean success = new GrpcCliBuildClient().runBuild(connector, compatibilitySpec, versionedDir, tasks, projectDir);
            if (!success) {
                // The daemon already streamed the failure report; just signal a non-zero exit.
                throw new ReportedException();
            }
        } finally {
            stoppable.stop();
        }
    }
}
