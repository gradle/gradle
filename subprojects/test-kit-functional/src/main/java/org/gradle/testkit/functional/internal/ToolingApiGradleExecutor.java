/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.functional.internal;

import org.gradle.launcher.daemon.client.DaemonDisappearedException;
import org.gradle.testkit.functional.internal.dist.GradleDistribution;
import org.gradle.testkit.functional.internal.dist.InstalledGradleDistribution;
import org.gradle.testkit.functional.internal.dist.URILocatedGradleDistribution;
import org.gradle.testkit.functional.internal.dist.VersionBasedGradleDistribution;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

public class ToolingApiGradleExecutor implements GradleExecutor {
    private final GradleDistribution gradleDistribution;
    private final File workingDirectory;

    public ToolingApiGradleExecutor(GradleDistribution gradleDistribution, File workingDirectory) {
        this.gradleDistribution = gradleDistribution;
        this.workingDirectory = workingDirectory;
    }

    public GradleExecutionHandle run(List<String> arguments, List<String> taskNames) {
        final ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        final ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        final GradleExecutionHandle gradleExecutionHandle = new GradleExecutionHandle(standardOutput, standardError);

        GradleConnector gradleConnector = buildConnector();
        ProjectConnection connection = gradleConnector.connect();

        try {
            BuildLauncher launcher = connection.newBuild();
            launcher.setStandardOutput(standardOutput);
            launcher.setStandardError(standardError);

            String[] argumentArray = new String[arguments.size()];
            arguments.toArray(argumentArray);
            launcher.withArguments(argumentArray);

            String[] tasksArray = new String[taskNames.size()];
            taskNames.toArray(tasksArray);
            launcher.forTasks(tasksArray);

            launcher.run();
        } catch(GradleConnectionException t) {
            gradleExecutionHandle.setException(t);
        } catch(DaemonDisappearedException e) {
            gradleExecutionHandle.setException(e);
        } finally {
            if(connection != null) {
                connection.close();
            }
        }

        return gradleExecutionHandle;
    }

    private GradleConnector buildConnector() {
        GradleConnector gradleConnector = GradleConnector.newConnector();
        gradleConnector.forProjectDirectory(workingDirectory);
        useGradleDistribution(gradleConnector);
        return gradleConnector;
    }

    private void useGradleDistribution(GradleConnector gradleConnector) {
        if(gradleDistribution instanceof VersionBasedGradleDistribution) {
            gradleConnector.useGradleVersion(((VersionBasedGradleDistribution) gradleDistribution).getHandle());
        } else if(gradleDistribution instanceof InstalledGradleDistribution) {
            gradleConnector.useInstallation(((InstalledGradleDistribution) gradleDistribution).getHandle());
        } else if(gradleDistribution instanceof URILocatedGradleDistribution) {
            gradleConnector.useDistribution(((URILocatedGradleDistribution) gradleDistribution).getHandle());
        }
    }
}
