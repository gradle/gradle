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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.testkit.functional.internal.dist.GradleDistribution;
import org.gradle.testkit.functional.internal.dist.InstalledGradleDistribution;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.task.*;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ToolingApiGradleExecutor implements GradleExecutor {
    private final Logger logger = Logging.getLogger(ToolingApiGradleExecutor.class);
    private final GradleDistribution gradleDistribution;
    private final File workingDirectory;
    private File gradleUserHomeDir;
    private List<String> arguments;
    private List<String> jvmArguments;

    public ToolingApiGradleExecutor(GradleDistribution gradleDistribution, File workingDirectory) {
        this.gradleDistribution = gradleDistribution;
        this.workingDirectory = workingDirectory;
    }

    public void withGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    public void withArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    public void withJvmArguments(List<String> jvmArguments) {
        this.jvmArguments = jvmArguments;
    }

    public GradleExecutionResult run() {
        final ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        final ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        final List<String> executedTasks = new ArrayList<String>();
        final List<String> skippedTasks = new ArrayList<String>();

        GradleConnector gradleConnector = buildConnector();
        ProjectConnection connection = null;

        try {
            connection = gradleConnector.connect();
            BuildLauncher launcher = connection.newBuild();
            launcher.setStandardOutput(standardOutput);
            launcher.setStandardError(standardError);
            launcher.addProgressListener(new TaskExecutionProgressListener(executedTasks, skippedTasks));

            String[] argumentArray = new String[arguments.size()];
            arguments.toArray(argumentArray);
            launcher.withArguments(argumentArray);

            String[] jvmArgumentsArray = new String[jvmArguments.size()];
            jvmArguments.toArray(jvmArgumentsArray);
            launcher.setJvmArguments(jvmArgumentsArray);

            launcher.run();
        } catch(RuntimeException t) {
            return new GradleExecutionResult(standardOutput, standardError, executedTasks, skippedTasks, t);
        } finally {
            if(connection != null) {
                connection.close();
            }
        }

        return new GradleExecutionResult(standardOutput, standardError, executedTasks, skippedTasks);
    }

    private GradleConnector buildConnector() {
        DefaultGradleConnector gradleConnector = (DefaultGradleConnector)GradleConnector.newConnector();
        gradleConnector.useGradleUserHomeDir(gradleUserHomeDir);
        gradleConnector.forProjectDirectory(workingDirectory);
        gradleConnector.searchUpwards(false);
        gradleConnector.daemonMaxIdleTime(120, TimeUnit.SECONDS);
        useGradleDistribution(gradleConnector);
        return gradleConnector;
    }

    private void useGradleDistribution(GradleConnector gradleConnector) {
        if(logger.isDebugEnabled()) {
            logger.debug("Using %s", gradleDistribution.getDisplayName());
        }

        if(gradleDistribution instanceof InstalledGradleDistribution) {
            gradleConnector.useInstallation(((InstalledGradleDistribution) gradleDistribution).getGradleHomeDir());
        }
    }

    private class TaskExecutionProgressListener implements ProgressListener {
        private final List<String> executedTasks;
        private final List<String> skippedTasks;

        public TaskExecutionProgressListener(List<String> executedTasks, List<String> skippedTasks) {
            this.executedTasks = executedTasks;
            this.skippedTasks = skippedTasks;
        }

        public void statusChanged(ProgressEvent event) {
            if(event instanceof TaskFinishEvent) {
                TaskFinishEvent taskFinishEvent = (TaskFinishEvent)event;
                String taskPath = taskFinishEvent.getDescriptor().getTaskPath();
                executedTasks.add(taskPath);

                TaskOperationResult result = taskFinishEvent.getResult();

                if(isFailed(result) || isSkipped(result) || isUpToDate(result)) {
                    skippedTasks.add(taskPath);
                }
            }
        }

        private boolean isFailed(TaskOperationResult result) {
            return result instanceof TaskFailureResult;
        }

        private boolean isSkipped(TaskOperationResult result) {
            return result instanceof TaskSkippedResult;
        }

        private boolean isUpToDate(TaskOperationResult result) {
            return result instanceof TaskSuccessResult && ((TaskSuccessResult)result).isUpToDate();
        }
    }
}
