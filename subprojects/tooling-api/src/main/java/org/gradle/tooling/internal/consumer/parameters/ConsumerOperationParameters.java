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

import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.BuildParameters;
import org.gradle.tooling.internal.protocol.BuildParametersVersion1;
import org.gradle.tooling.internal.protocol.InternalLaunchable;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ConsumerOperationParameters implements BuildOperationParametersVersion1, BuildParametersVersion1, BuildParameters {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ProgressListenerAdapter progressListener = new ProgressListenerAdapter();
        private ConnectionParameters parameters;
        private OutputStream stdout;
        private OutputStream stderr;
        private InputStream stdin;
        private File javaHome;
        private List<String> jvmArguments;
        private List<String> arguments;
        private List<String> tasks;
        private List<InternalLaunchable> launchables;

        private Builder() {
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

        public Builder setStdin(InputStream stdin) {
            this.stdin = stdin;
            return this;
        }

        public Builder setJavaHome(File javaHome) {
            validateJavaHome(javaHome);
            this.javaHome = javaHome;
            return this;
        }

        public Builder setJvmArguments(String... jvmArguments) {
            this.jvmArguments = rationalizeInput(jvmArguments);
            return this;
        }

        public Builder setArguments(String [] arguments) {
            this.arguments = rationalizeInput(arguments);
            return this;
        }

        public Builder setTasks(List<String> tasks) {
            this.tasks = tasks;
            return this;
        }

        public Builder setLaunchables(List<InternalLaunchable> launchables) {
            this.launchables = launchables;
            return this;
        }

        public void addProgressListener(ProgressListener listener) {
            progressListener.add(listener);
        }

        public ConsumerOperationParameters build() {
            return new ConsumerOperationParameters(parameters, stdout, stderr, stdin,
                    javaHome, jvmArguments, arguments, tasks, launchables, progressListener);
        }
    }

    private final ProgressListenerAdapter progressListener;
    private final ConnectionParameters parameters;
    private final long startTime = System.currentTimeMillis();

    private final OutputStream stdout;
    private final OutputStream stderr;
    private final InputStream stdin;

    private final File javaHome;
    private final List<String> jvmArguments;
    private final List<String> arguments;
    private final List<String> tasks;
    private final List<InternalLaunchable> launchables;

    private ConsumerOperationParameters(ConnectionParameters parameters, OutputStream stdout, OutputStream stderr, InputStream stdin,
                                        File javaHome, List<String> jvmArguments, List<String> arguments, List<String> tasks,
                                        List<InternalLaunchable> launchables, ProgressListenerAdapter listener) {
        this.parameters = parameters;
        this.stdout = stdout;
        this.stderr = stderr;
        this.stdin = stdin;
        this.javaHome = javaHome;
        this.jvmArguments = jvmArguments;
        this.arguments = arguments;
        this.tasks = tasks;
        this.launchables = launchables;
        this.progressListener = listener;
    }

    private static List<String> rationalizeInput(String[] arguments) {
        return arguments != null && arguments.length > 0 ? Arrays.asList(arguments) : null;
    }

    private static void validateJavaHome(File javaHome) {
        if (javaHome == null) {
            return;
        }
        if (!javaHome.isDirectory()) {
            throw new IllegalArgumentException("Supplied javaHome is not a valid folder. You supplied: " + javaHome);
        }
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean getVerboseLogging() {
        return parameters.getVerboseLogging();
    }

    public File getGradleUserHomeDir() {
        return parameters.getGradleUserHomeDir();
    }

    public File getProjectDir() {
        return parameters.getProjectDir();
    }

    public Boolean isSearchUpwards() {
        return parameters.isSearchUpwards();
    }

    public Boolean isEmbedded() {
        return parameters.isEmbedded();
    }

    public TimeUnit getDaemonMaxIdleTimeUnits() {
        return parameters.getDaemonMaxIdleTimeUnits();
    }

    public Integer getDaemonMaxIdleTimeValue() {
        return parameters.getDaemonMaxIdleTimeValue();
    }

    public OutputStream getStandardOutput() {
        return stdout;
    }

    public OutputStream getStandardError() {
        return stderr;
    }

    public ProgressListenerVersion1 getProgressListener() {
        return progressListener;
    }

    public InputStream getStandardInput() {
        return stdin;
    }

    public File getJavaHome() {
        return javaHome;
    }

    public List<String> getJvmArguments() {
        return jvmArguments;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public List<InternalLaunchable> getLaunchables() {
        return launchables;
    }
}
