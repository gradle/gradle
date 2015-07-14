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

public class GradleExecutor {

    public GradleExecutionResult run(File gradleHome, File gradleUserHome, File workDir, List<String> buildArgs, List<String> jvmArgs) {
        final ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
        final ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        final List<String> executedTasks = new ArrayList<String>();
        final List<String> skippedTasks = new ArrayList<String>();

        GradleConnector gradleConnector = buildConnector(gradleHome, gradleUserHome, workDir);
        ProjectConnection connection = null;

        try {
            connection = gradleConnector.connect();
            BuildLauncher launcher = connection.newBuild();
            launcher.setStandardOutput(standardOutput);
            launcher.setStandardError(standardError);
            launcher.addProgressListener(new TaskExecutionProgressListener(executedTasks, skippedTasks));

            launcher.withArguments(buildArgs.toArray(new String[buildArgs.size()]));
            launcher.setJvmArguments(jvmArgs.toArray(new String[jvmArgs.size()]));

            launcher.run();
        } catch (RuntimeException t) {
            return new GradleExecutionResult(standardOutput, standardError, executedTasks, skippedTasks, t);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        return new GradleExecutionResult(standardOutput, standardError, executedTasks, skippedTasks);
    }

    private GradleConnector buildConnector(File gradleHome, File gradleUserHome, File workDir) {
        DefaultGradleConnector gradleConnector = (DefaultGradleConnector) GradleConnector.newConnector();
        gradleConnector.useGradleUserHomeDir(gradleUserHome);
        gradleConnector.forProjectDirectory(workDir);
        gradleConnector.searchUpwards(false);
        gradleConnector.daemonMaxIdleTime(120, TimeUnit.SECONDS);
        gradleConnector.useInstallation(gradleHome);
        return gradleConnector;
    }

    private class TaskExecutionProgressListener implements ProgressListener {
        private final List<String> executedTasks;
        private final List<String> skippedTasks;

        public TaskExecutionProgressListener(List<String> executedTasks, List<String> skippedTasks) {
            this.executedTasks = executedTasks;
            this.skippedTasks = skippedTasks;
        }

        public void statusChanged(ProgressEvent event) {
            if (event instanceof TaskFinishEvent) {
                TaskFinishEvent taskFinishEvent = (TaskFinishEvent) event;
                String taskPath = taskFinishEvent.getDescriptor().getTaskPath();
                executedTasks.add(taskPath);

                TaskOperationResult result = taskFinishEvent.getResult();

                if (isFailed(result) || isSkipped(result) || isUpToDate(result)) {
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
            return result instanceof TaskSuccessResult && ((TaskSuccessResult) result).isUpToDate();
        }
    }
}
