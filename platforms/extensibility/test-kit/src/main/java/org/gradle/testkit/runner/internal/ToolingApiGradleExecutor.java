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

package org.gradle.testkit.runner.internal;

import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.InvalidRunnerConfigurationException;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnsupportedFeatureException;
import org.gradle.testkit.runner.internal.feature.TestKitFeature;
import org.gradle.testkit.runner.internal.io.NoCloseOutputStream;
import org.gradle.testkit.runner.internal.io.SynchronizedOutputStream;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.task.TaskFailureResult;
import org.gradle.tooling.events.task.TaskFinishEvent;
import org.gradle.tooling.events.task.TaskOperationResult;
import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.events.task.TaskSkippedResult;
import org.gradle.tooling.events.task.TaskStartEvent;
import org.gradle.tooling.events.task.TaskSuccessResult;
import org.gradle.tooling.internal.consumer.DefaultBuildLauncher;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.GradleUserHomeLookup;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.testkit.runner.TaskOutcome.FAILED;
import static org.gradle.testkit.runner.TaskOutcome.FROM_CACHE;
import static org.gradle.testkit.runner.TaskOutcome.NO_SOURCE;
import static org.gradle.testkit.runner.TaskOutcome.SKIPPED;
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;
import static org.gradle.tooling.internal.consumer.DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION;

public class ToolingApiGradleExecutor implements GradleExecutor {

    public static final String TEST_KIT_DAEMON_DIR_NAME = "test-kit-daemon";

    private static final String CLEANUP_THREAD_NAME = "gradle-runner-cleanup";

    private final static AtomicBoolean SHUTDOWN_REGISTERED = new AtomicBoolean();

    private static void maybeRegisterCleanup() {
        if (SHUTDOWN_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        DefaultGradleConnector.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, CLEANUP_THREAD_NAME));
        }
    }

    @Override
    public GradleExecutionResult run(GradleExecutionParameters parameters) {
        final StreamByteBuffer outputBuffer = new StreamByteBuffer();
        final OutputStream syncOutput = new SynchronizedOutputStream(outputBuffer.getOutputStream());

        final List<BuildTask> tasks = new ArrayList<BuildTask>();

        maybeRegisterCleanup();

        GradleConnector gradleConnector = buildConnector(
            parameters.getGradleUserHome(),
            parameters.getProjectDir(),
            parameters.isEmbedded(),
            parameters.getGradleProvider()
        );

        ProjectConnection connection = null;
        GradleVersion targetGradleVersion = null;

        try {
            connection = gradleConnector.connect();
            targetGradleVersion = determineTargetGradleVersion(connection);
            if (targetGradleVersion.compareTo(TestKitFeature.RUN_BUILDS.getSince()) < 0) {
                throw new UnsupportedFeatureException(String.format("The version of Gradle you are using (%s) is not supported by TestKit. TestKit supports all Gradle versions %s and later.",
                    targetGradleVersion.getVersion(), MINIMUM_SUPPORTED_GRADLE_VERSION.getVersion()));
            } else {
                checkDeprecationWarning(targetGradleVersion);
            }


            DefaultBuildLauncher launcher = (DefaultBuildLauncher) connection.newBuild();

            launcher.setStandardOutput(new NoCloseOutputStream(teeOutput(syncOutput, parameters.getStandardOutput())));
            launcher.setStandardError(new NoCloseOutputStream(teeOutput(syncOutput, parameters.getStandardError())));

            if (parameters.getStandardInput() != null) {
                launcher.setStandardInput(parameters.getStandardInput());
            }

            launcher.addProgressListener(new TaskExecutionProgressListener(tasks), OperationType.TASK);

            launcher.withArguments(parameters.getBuildArgs().toArray(new String[0]));
            launcher.setJvmArguments(parameters.getJvmArgs().toArray(new String[0]));
            launcher.setEnvironmentVariables(parameters.getEnvironment());

            if (!parameters.getInjectedClassPath().isEmpty()) {
                if (targetGradleVersion.compareTo(TestKitFeature.PLUGIN_CLASSPATH_INJECTION.getSince()) < 0) {
                    throw new UnsupportedFeatureException("support plugin classpath injection", targetGradleVersion, TestKitFeature.PLUGIN_CLASSPATH_INJECTION.getSince());
                } else {
                    checkDeprecationWarning(targetGradleVersion);
                }
                launcher.withInjectedClassPath(parameters.getInjectedClassPath());
            }

            launcher.run();
        } catch (UnsupportedVersionException e) {
            throw new InvalidRunnerConfigurationException("The build could not be executed due to a feature not being supported by the target Gradle version", e);
        } catch (BuildException t) {
            return new GradleExecutionResult(new BuildOperationParameters(targetGradleVersion, parameters.isEmbedded()), outputBuffer.readAsString(), tasks, t);
        } catch (GradleConnectionException t) {
            StringBuilder message = new StringBuilder("An error occurred executing build with ");
            if (parameters.getBuildArgs().isEmpty()) {
                message.append("no args");
            } else {
                message.append("args '");
                message.append(CollectionUtils.join(" ", parameters.getBuildArgs()));
                message.append("'");
            }

            message.append(" in directory '").append(parameters.getProjectDir().getAbsolutePath()).append("'");

            String capturedOutput = outputBuffer.readAsString();
            if (!capturedOutput.isEmpty()) {
                message.append(". Output before error:")
                    .append(SystemProperties.getInstance().getLineSeparator())
                    .append(capturedOutput);
            }

            throw new IllegalStateException(message.toString(), t);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }

        return new GradleExecutionResult(new BuildOperationParameters(targetGradleVersion, parameters.isEmbedded()), outputBuffer.readAsString(), tasks);
    }

    private static void checkDeprecationWarning(GradleVersion targetGradleVersion) {
        if (targetGradleVersion.compareTo(MINIMUM_SUPPORTED_GRADLE_VERSION) < 0) {
            DeprecationLogger.deprecate(String.format("The version of Gradle you are using (%s) is deprecated with TestKit. TestKit will only support the last 5 major versions in future.",
                    targetGradleVersion.getVersion()))
                .willBecomeAnErrorInGradle9()
                .withUserManual("third_party_integration", "sec:embedding_compatibility");
        }
    }

    private GradleVersion determineTargetGradleVersion(ProjectConnection connection) {
        BuildEnvironment buildEnvironment = connection.getModel(BuildEnvironment.class);
        return GradleVersion.version(buildEnvironment.getGradle().getGradleVersion());
    }

    private static OutputStream teeOutput(OutputStream capture, OutputStream user) {
        if (user == null) {
            return capture;
        } else {
            return new TeeOutputStream(capture, user);
        }
    }

    private GradleConnector buildConnector(File gradleUserHome, File projectDir, boolean embedded, GradleProvider gradleProvider) {
        DefaultGradleConnector gradleConnector = (DefaultGradleConnector) GradleConnector.newConnector();
        gradleConnector.useDistributionBaseDir(GradleUserHomeLookup.gradleUserHome());
        gradleProvider.applyTo(gradleConnector);
        gradleConnector.useGradleUserHomeDir(gradleUserHome);
        gradleConnector.daemonBaseDir(new File(gradleUserHome, TEST_KIT_DAEMON_DIR_NAME));
        gradleConnector.forProjectDirectory(projectDir);
        gradleConnector.daemonMaxIdleTime(120, TimeUnit.SECONDS);
        gradleConnector.embedded(embedded);
        return gradleConnector;
    }

    private static class TaskExecutionProgressListener implements ProgressListener {
        private final List<BuildTask> tasks;
        private final Map<String, Integer> order = new HashMap<String, Integer>();

        public TaskExecutionProgressListener(List<BuildTask> tasks) {
            this.tasks = tasks;
        }

        @Override
        public void statusChanged(ProgressEvent event) {
            if (event instanceof TaskStartEvent) {
                TaskStartEvent taskStartEvent = (TaskStartEvent) event;
                if (!accept(taskStartEvent)) {
                    return;
                }
                order.put(taskStartEvent.getDescriptor().getTaskPath(), tasks.size());
                tasks.add(null);
            }
            if (event instanceof TaskFinishEvent) {
                TaskFinishEvent taskFinishEvent = (TaskFinishEvent) event;
                if (!accept(taskFinishEvent)) {
                    return;
                }
                String taskPath = taskFinishEvent.getDescriptor().getTaskPath();
                TaskOperationResult result = taskFinishEvent.getResult();
                final Integer index = order.get(taskPath);
                if (index == null) {
                    throw new IllegalStateException("Received task finish event for task " + taskPath + " without first receiving task start event");
                }
                tasks.set(index, determineBuildTask(result, taskPath));
            }
        }

        private boolean accept(TaskProgressEvent event) {
            // Exclude tasks from `buildSrc`
            return !event.getDescriptor().getTaskPath().startsWith(":buildSrc");
        }

        private BuildTask determineBuildTask(TaskOperationResult result, String taskPath) {
            if (isFailed(result)) {
                return createBuildTask(taskPath, FAILED);
            } else if (isNoSource(result)) {
                return createBuildTask(taskPath, NO_SOURCE);
            } else if (isSkipped(result)) {
                return createBuildTask(taskPath, SKIPPED);
            } else if (isFromCache(result)) {
                return createBuildTask(taskPath, FROM_CACHE);
            } else if (isUpToDate(result)) {
                return createBuildTask(taskPath, UP_TO_DATE);
            }

            return createBuildTask(taskPath, SUCCESS);
        }

        private boolean isNoSource(TaskOperationResult result) {
            return isSkipped(result) && ((TaskSkippedResult)result).getSkipMessage().equals("NO-SOURCE");
        }

        private BuildTask createBuildTask(String taskPath, TaskOutcome outcome) {
            return new DefaultBuildTask(taskPath, outcome);
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

        private boolean isFromCache(TaskOperationResult result) {
            return result instanceof TaskSuccessResult && ((TaskSuccessResult) result).isFromCache();
        }
    }
}
