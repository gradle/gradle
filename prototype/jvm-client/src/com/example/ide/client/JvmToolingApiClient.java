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
package com.example.ide.client;

import com.example.ide.IdeProjectModel;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.File;

/**
 * The JVM half of the parity check: a classic Tooling API client (no gRPC) that fetches the SAME
 * plugin-contributed model the native gRPC client fetches. The plugin's ToolingModelBuilder returns
 * a protobuf IdeProjectModel; here it travels over the normal Tooling API serialize/adapt path, so
 * one builder serves both a JVM and a native client.
 */
public final class JvmToolingApiClient {
    public static void main(String[] args) {
        File projectDir = new File(args[0]);
        File gradleInstallation = new File(args[1]);

        GradleConnector connector = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)
            .useInstallation(gradleInstallation);
        try (ProjectConnection connection = connector.connect()) {
            IdeProjectModel model = connection.model(IdeProjectModel.class).get();
            System.out.println("IdeProjectModel (via JVM Tooling API):");
            System.out.println("  project path: " + model.getProjectPath());
            System.out.println("  project name: " + model.getProjectName());
            System.out.println("  build dir:    " + model.getBuildDir());
            System.out.println("  plugins:      " + String.join(", ", model.getPluginIdsList()));
        }
    }
}
