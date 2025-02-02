/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer.parameters;

import com.google.common.collect.Lists;
import org.gradle.api.GradleException;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.tooling.CancellationToken;
import org.gradle.tooling.StreamedValueListener;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.CancellationTokenInternal;
import org.gradle.tooling.internal.consumer.ConnectionConfigurationUtil;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.gradle.TaskListingLaunchable;
import org.gradle.tooling.internal.protocol.BuildParameters;
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;
import org.gradle.tooling.model.Launchable;
import org.gradle.tooling.model.Task;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * This is used via reflection from {@code ProviderOperationParameters}.
 */
public class ConsumerOperationParameters implements BuildParameters {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<org.gradle.tooling.ProgressListener> legacyProgressListeners = new ArrayList<org.gradle.tooling.ProgressListener>();
        private final Map<OperationType, List<ProgressListener>> progressListeners = new EnumMap<OperationType, List<ProgressListener>>(OperationType.class);
        private String entryPoint;
        private CancellationToken cancellationToken;
        private ConnectionParameters parameters;
        private OutputStream stdout;
        private OutputStream stderr;
        private Boolean colorOutput;
        private InputStream stdin;
        private File javaHome;
        private List<String> baseJvmArguments = null;
        private List<String> additionalJvmArguments = null;
        private Map<String, String> envVariables;
        private List<String> arguments;
        private List<String> tasks;
        private List<InternalLaunchable> launchables;
        private ClassPath injectedPluginClasspath = ClassPath.EMPTY;
        private Map<String, String> systemProperties;
        @Nullable
        private StreamedValueListener streamedValueListener;

        private Builder() {
        }

        public Builder setEntryPoint(String entryPoint) {
            this.entryPoint = entryPoint;
            return this;
        }

        public Builder setParameters(ConnectionParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder setStdout(OutputStream stdout) {
            this.stdout = stdout;
            return this;
        }

        public Builder setStderr(OutputStream stderr) {
            this.stderr = stderr;
            return this;
        }

        public Builder setColorOutput(Boolean colorOutput) {
            this.colorOutput = colorOutput;
            return this;
        }

        public Builder setStdin(InputStream stdin) {
            this.stdin = stdin;
            return this;
        }

        public Builder setJavaHome(File javaHome) {
            validateJavaHome(javaHome);
            this.javaHome = javaHome;
            return this;
        }

        public Builder setBaseJvmArguments(@Nullable List<String> baseJvmArguments) {
            if (baseJvmArguments != null) {
                this.baseJvmArguments = new ArrayList<>(baseJvmArguments);
            }
            return this;
        }

        public Builder addJvmArguments(@Nullable List<String> jvmArguments) {
            this.additionalJvmArguments = concat(this.additionalJvmArguments, jvmArguments);
            return this;
        }

        public Builder setArguments(List<String> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder addArguments(List<String> arguments) {
            this.arguments = concat(this.arguments, arguments);
            return this;
        }

        private static List<String> concat(@Nullable List<String> first, @Nullable List<String> second) {
            List<String> result = new ArrayList<String>();
            if (first != null) {
                result.addAll(first);
            }
            if (second != null) {
                result.addAll(second);
            }
            return result;
        }

        public Builder setEnvironmentVariables(Map<String, String> envVariables) {
            this.envVariables = envVariables;
            return this;
        }

        public Builder setTasks(List<String> tasks) {
            this.tasks = tasks;
            return this;
        }

        public Builder setLaunchables(Iterable<? extends Launchable> launchables) {
            Set<String> taskPaths = new LinkedHashSet<String>();
            List<InternalLaunchable> launchablesParams = new ArrayList<>();
            for (Launchable launchable : launchables) {
                Object original = new ProtocolToModelAdapter().unpack(launchable);
                if (original instanceof InternalLaunchable) {
                    // A launchable created by the provider - just hand it back
                    launchablesParams.add((InternalLaunchable) original);
                } else if (original instanceof TaskListingLaunchable) {
                    // A launchable synthesized by the consumer - unpack it into a set of task names
                    taskPaths.addAll(((TaskListingLaunchable) original).getTaskNames());
                } else if (launchable instanceof Task) {
                    // A task created by a provider that does not understand launchables
                    taskPaths.add(((Task) launchable).getPath());
                } else {
                    throw new GradleException("Only Task or TaskSelector instances are supported: "
                        + (launchable != null ? launchable.getClass() : "null"));
                }
            }
            // Tasks are ignored by providers if launchables is not null
            this.launchables = launchablesParams.isEmpty() ? null : launchablesParams;
            tasks = Lists.newArrayList(taskPaths);
            return this;
        }

        public Builder setInjectedPluginClasspath(ClassPath classPath) {
            this.injectedPluginClasspath = classPath;
            return this;
        }

        public Builder setSystemProperties(Map<String, String> systemProperties) {
            this.systemProperties = systemProperties;
            return this;
        }

        public void addProgressListener(org.gradle.tooling.ProgressListener listener) {
            legacyProgressListeners.add(listener);
        }

        public void addProgressListener(ProgressListener listener, Set<OperationType> eventTypes) {
            for (OperationType type : eventTypes) {
                List<ProgressListener> listeners = this.progressListeners.computeIfAbsent(type, new Function<OperationType, List<ProgressListener>>() {
                    @Override
                    public List<ProgressListener> apply(OperationType operationType) {
                        return new ArrayList<ProgressListener>();
                    }
                });
                listeners.add(listener);
            }
        }

        public void setCancellationToken(CancellationToken cancellationToken) {
            this.cancellationToken = cancellationToken;
        }

        public void setStreamedValueListener(StreamedValueListener streamedValueListener) {
            this.streamedValueListener = streamedValueListener;
        }

        public ConsumerOperationParameters build() {
            if (entryPoint == null) {
                throw new IllegalStateException("No entry point specified.");
            }

            return new ConsumerOperationParameters(
                entryPoint,
                parameters,
                stdout,
                stderr,
                colorOutput,
                stdin,
                javaHome,
                baseJvmArguments,
                additionalJvmArguments,
                envVariables,
                arguments,
                tasks,
                launchables,
                injectedPluginClasspath,
                legacyProgressListeners,
                progressListeners,
                cancellationToken,
                systemProperties,
                new FailsafeStreamedValueListener(streamedValueListener)
            );
        }

        public void copyFrom(ConsumerOperationParameters operationParameters) {
            tasks = operationParameters.tasks;
            launchables = operationParameters.launchables;
            cancellationToken = operationParameters.cancellationToken;
            legacyProgressListeners.addAll(operationParameters.legacyProgressListeners);
            progressListeners.putAll(operationParameters.progressListeners);
            arguments = operationParameters.arguments;
            baseJvmArguments = operationParameters.baseJvmArguments;
            additionalJvmArguments = operationParameters.additionalJvmArguments;
            envVariables = operationParameters.envVariables;
            stdout = operationParameters.stdout;
            stderr = operationParameters.stderr;
            stdin = operationParameters.stdin;
            colorOutput = operationParameters.colorOutput;
            javaHome = operationParameters.javaHome;
            injectedPluginClasspath = operationParameters.injectedPluginClasspath;
            systemProperties = operationParameters.systemProperties;
        }
    }

    private final String entryPointName;
    private final ProgressListenerAdapter progressListener;
    private final FailsafeBuildProgressListenerAdapter buildProgressListener;
    private final CancellationToken cancellationToken;
    private final ConnectionParameters parameters;
    private final long startTime = System.currentTimeMillis();

    private final OutputStream stdout;
    private final OutputStream stderr;
    private final Boolean colorOutput;
    private final InputStream stdin;

    private final File javaHome;
    private final List<String> baseJvmArguments;
    private final List<String> additionalJvmArguments;
    private final Map<String, String> envVariables;
    private final List<String> arguments;
    private final List<String> tasks;
    private final List<InternalLaunchable> launchables;
    private final ClassPath injectedPluginClasspath;

    private final List<org.gradle.tooling.ProgressListener> legacyProgressListeners;
    private final Map<OperationType, List<ProgressListener>> progressListeners;

    private final Map<String, String> systemProperties;
    private final FailsafeStreamedValueListener streamedValueListener;

    private ConsumerOperationParameters(
        String entryPointName,
        ConnectionParameters parameters,
        OutputStream stdout,
        OutputStream stderr,
        Boolean colorOutput,
        InputStream stdin,
        File javaHome,
        @Nullable
        List<String> baseJvmArguments,
        @Nullable
        List<String> additionalJvmArguments,
        Map<String, String> envVariables,
        List<String> arguments,
        List<String> tasks,
        List<InternalLaunchable> launchables,
        ClassPath injectedPluginClasspath,
        List<org.gradle.tooling.ProgressListener> legacyProgressListeners,
        Map<OperationType, List<ProgressListener>> progressListeners,
        CancellationToken cancellationToken,
        Map<String, String> systemProperties,
        FailsafeStreamedValueListener streamedValueListener
    ) {
        this.entryPointName = entryPointName;
        this.parameters = parameters;
        this.stdout = stdout;
        this.stderr = stderr;
        this.colorOutput = colorOutput;
        this.stdin = stdin;
        this.javaHome = javaHome;
        this.baseJvmArguments = baseJvmArguments;
        this.additionalJvmArguments = additionalJvmArguments;
        this.envVariables = envVariables;
        this.arguments = arguments;
        this.tasks = tasks;
        this.launchables = launchables;
        this.injectedPluginClasspath = injectedPluginClasspath;
        this.cancellationToken = cancellationToken;
        this.legacyProgressListeners = legacyProgressListeners;
        this.progressListeners = progressListeners;
        this.systemProperties = systemProperties;
        this.streamedValueListener = streamedValueListener;

        // create the listener adapters right when the ConsumerOperationParameters are instantiated but no earlier,
        // this ensures that when multiple requests are issued that are built from the same builder, such requests do not share any state kept in the listener adapters
        // e.g. if the listener adapters do per-request caching, such caching must not leak between different requests built from the same builder
        this.progressListener = new ProgressListenerAdapter(this.legacyProgressListeners);
        this.buildProgressListener = new FailsafeBuildProgressListenerAdapter(new BuildProgressListenerAdapter(this.progressListeners));
    }

    private static void validateJavaHome(File javaHome) {
        if (javaHome == null) {
            return;
        }
        if (!javaHome.isDirectory()) {
            throw new IllegalArgumentException("Supplied javaHome is not a valid folder. You supplied: " + javaHome);
        }
    }

    public String getEntryPointName() {
        return entryPointName;
    }

    /**
     * @since 1.0-milestone-3
     */
    public long getStartTime() {
        return startTime;
    }

    public boolean getVerboseLogging() {
        return parameters.getVerboseLogging();
    }

    /**
     * @since 1.0-milestone-3
     */
    public File getGradleUserHomeDir() {
        return parameters.getGradleUserHomeDir();
    }

    /**
     * @since 1.0-milestone-3
     */
    public File getProjectDir() {
        return parameters.getProjectDir();
    }

    /**
     * @since 1.0-milestone-3
     */
    public Boolean isSearchUpwards() {
        return parameters.isSearchUpwards();
    }

    /**
     * @since 1.0-milestone-3
     */
    public Boolean isEmbedded() {
        return parameters.isEmbedded();
    }

    /**
     * @since 1.0-milestone-3
     */
    public TimeUnit getDaemonMaxIdleTimeUnits() {
        return parameters.getDaemonMaxIdleTimeUnits();
    }

    /**
     * @since 1.0-milestone-3
     */
    public Integer getDaemonMaxIdleTimeValue() {
        return parameters.getDaemonMaxIdleTimeValue();
    }

    /**
     * @since 2.2-rc-1
     */
    public File getDaemonBaseDir() {
        return parameters.getDaemonBaseDir();
    }

    /**
     * @since 1.0-milestone-3
     */
    public OutputStream getStandardOutput() {
        return stdout;
    }

    /**
     * @since 1.0-milestone-3
     */
    public OutputStream getStandardError() {
        return stderr;
    }

    /**
     * @since 2.3-rc-1
     */
    public Boolean isColorOutput() {
        return colorOutput;
    }

    public InputStream getStandardInput() {
        return stdin;
    }

    public File getJavaHome() {
        return javaHome;
    }

    @Nullable
    public List<String> getJvmArguments() {
        // Backport fix for https://github.com/gradle/gradle/issues/31462
        // Cross-version scenarios
        // - Old TAPI (8.12 and before) invokes new Gradle (8.13 and after):
        //     ProviderConnection will catch UnsupportedMethodException when calling getBaseJvmArgs() and will fall back to this method, but with the previous behavior (ie `return jvmArguments`)
        //     see https://github.com/gradle/gradle/blob/v8.12.0/platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/parameters/ConsumerOperationParameters.java#L406
        // - New TAPI (8.13 and after) invokes old Gradle (8.12 and before):
        //     We try to approximate the behavior of the new Gradle by returning the combined list of JVM arguments defined in gradle.properties and in the TAPI client config; see the method body below.
        //     see https://github.com/gradle/gradle/blob/v8.12.0/platforms/core-runtime/launcher/src/main/java/org/gradle/tooling/internal/provider/ProviderConnection.java#L350

        if (baseJvmArguments == null && additionalJvmArguments == null) {
            // To keep the old behavior, when no JVM arguments are set, return null.
            return null;
        } else {
            // Otherwise, return the combined list of JVM arguments defined in the gradle.properties file and in the TAPI client config.
            List<String> arguments = new ArrayList<>();
            if (baseJvmArguments != null) {
                arguments.addAll(baseJvmArguments);
            } else {
                arguments.addAll(ConnectionConfigurationUtil.determineJvmArguments(parameters));
            }
            if (additionalJvmArguments != null) {
                arguments.addAll(additionalJvmArguments);
            }
            return arguments;
        }
    }

    @Nullable
    public List<String> getBaseJvmArguments() {
        return baseJvmArguments;
    }

    @Nullable
    public List<String> getAdditionalJvmArguments() {
        return additionalJvmArguments;
    }

    public Map<String, String> getEnvironmentVariables() {
        return envVariables;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public List<String> getTasks() {
        return tasks;
    }

    /**
     * @since 1.12-rc-1
     */
    public List<InternalLaunchable> getLaunchables() {
        return launchables;
    }

    /**
     * @since 2.8-rc-1
     */
    public List<File> getInjectedPluginClasspath() {
        return injectedPluginClasspath.getAsFiles();
    }

    /**
     * @since 1.0-milestone-3
     */
    public ProgressListenerVersion1 getProgressListener() {
        return progressListener;
    }

    /**
     * @since 2.4-rc-1
     */
    public FailsafeBuildProgressListenerAdapter getBuildProgressListener() {
        return buildProgressListener;
    }

    public BuildCancellationToken getCancellationToken() {
        return ((CancellationTokenInternal) cancellationToken).getToken();
    }

    /**
     * @since 7.6
     */
    public Map<String, ?> getSystemProperties() {
        return systemProperties;
    }

    public FailsafeStreamedValueListener getStreamedValueListener() {
        return streamedValueListener;
    }

    /**
     * @since 8.6
     */
    public void onStreamedValue(Object model) {
        streamedValueListener.onValue(model);
    }
}
