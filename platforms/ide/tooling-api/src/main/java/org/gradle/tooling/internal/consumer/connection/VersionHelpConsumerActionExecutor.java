/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.PhasedBuildAction;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.Help;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VersionHelpConsumerActionExecutor implements ConsumerActionExecutor {
    private final ConsumerActionExecutor delegate;

    public VersionHelpConsumerActionExecutor(ConsumerActionExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void stop() {
        delegate.stop();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    public <T> T run(final ConsumerAction<T> action) {
        final ConsumerOperationParameters parameters = action.getParameters();
        List<String> arguments = parameters.getArguments();

        if (arguments == null || arguments.isEmpty()) {
            return delegate.run(action);
        }

        boolean help = false;
        boolean version = false;
        boolean showVersion = false;

        for (String arg : arguments) {
            if ("--help".equals(arg) || "-h".equals(arg) || "-?".equals(arg)) {
                help = true;
            } else if ("--version".equals(arg) || "-v".equals(arg)) {
                version = true;
            } else if ("--show-version".equals(arg) || "-V".equals(arg)) {
                showVersion = true;
            }
        }

        if (help) {
            if (printHelp(action)) {
                return null;
            }
            return delegate.run(action);
        }

        if (version) {
            if (printVersion(action)) {
                return null;
            }
            return delegate.run(action);
        }

        if (showVersion) {
            if (printVersion(action)) {
                final ConsumerOperationParameters newParameters = removeShowVersionArgument(parameters);
                return delegate.run(new ConsumerAction<T>() {
                    @Override
                    public ConsumerOperationParameters getParameters() {
                        return newParameters;
                    }

                    @Override
                    public T run(ConsumerConnection connection) {
                        return action.run(new ParameterOverridingConsumerConnection(connection, newParameters));
                    }
                });
            }
        }

        return delegate.run(action);
    }

    private <T> boolean printHelp(ConsumerAction<T> action) {
        final ConsumerOperationParameters parameters = action.getParameters();
        final ConsumerOperationParameters newParameters = filterArguments(parameters);
        try {
            Help helpModel = delegate.run(new ConsumerAction<Help>() {
                @Override
                public ConsumerOperationParameters getParameters() {
                    return newParameters;
                }

                @Override
                public Help run(ConsumerConnection connection) {
                    return connection.run(Help.class, newParameters);
                }
            });

            printToOutput(parameters, helpModel.getRenderedText());
            return true;
        } catch (UnknownModelException e) {
            return false;
        }
    }

    private <T> boolean printVersion(ConsumerAction<T> action) {
        final ConsumerOperationParameters parameters = action.getParameters();
        final ConsumerOperationParameters newParameters = filterArguments(parameters);
        try {
            BuildEnvironment buildEnvironment = delegate.run(new ConsumerAction<BuildEnvironment>() {
                @Override
                public ConsumerOperationParameters getParameters() {
                    return newParameters;
                }

                @Override
                public BuildEnvironment run(ConsumerConnection connection) {
                    return connection.run(BuildEnvironment.class, newParameters);
                }
            });

            printToOutput(parameters, buildEnvironment.getVersionInfo());
            return true;
        } catch (UnknownModelException | UnsupportedMethodException e) {
            return false;
        }
    }

    private ConsumerOperationParameters filterArguments(ConsumerOperationParameters parameters) {
        ConnectionParameters connectionParams = parameters.getConnectionParameters();
        String entryPoint = parameters.getEntryPointName();

        ConsumerOperationParameters.Builder builder = ConsumerOperationParameters.builder();
        builder.copyFrom(parameters);
        builder.setEntryPoint(entryPoint);
        builder.setParameters(connectionParams);
        builder.setArguments(Collections.emptyList());
        builder.setLaunchables(Collections.emptyList());
        builder.setTasks(null);

        return builder.build();
    }

    private void printToOutput(ConsumerOperationParameters parameters, String output) {
        OutputStream stdout = parameters.getStandardOutput();
        if (stdout != null) {
            try {
                stdout.write(output.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // Ignore, as per requirements ("swallows all I/O errors")
            }
        }
    }

    private ConsumerOperationParameters removeShowVersionArgument(ConsumerOperationParameters parameters) {
        ConnectionParameters connectionParams = parameters.getConnectionParameters();
        String entryPoint = parameters.getEntryPointName();

        List<String> originalArgs = parameters.getArguments();
        if (originalArgs == null || originalArgs.isEmpty()) {
            return parameters;
        }
        List<String> filteredArgs = new ArrayList<>(originalArgs);
        filteredArgs.removeIf(arg -> "--show-version".equals(arg) || "-V".equals(arg));

        ConsumerOperationParameters.Builder builder = ConsumerOperationParameters.builder();
        builder.copyFrom(parameters);
        builder.setEntryPoint(entryPoint);
        builder.setParameters(connectionParams);
        builder.setArguments(filteredArgs);

        return builder.build();
    }

    private static class ParameterOverridingConsumerConnection implements ConsumerConnection {
        private final ConsumerConnection delegate;
        private final ConsumerOperationParameters parameters;

        public ParameterOverridingConsumerConnection(ConsumerConnection delegate, ConsumerOperationParameters parameters) {
            this.delegate = delegate;
            this.parameters = parameters;
        }

        @Override
        public void stop() {
            delegate.stop();
        }

        @Override
        public String getDisplayName() {
            return delegate.getDisplayName();
        }

        @Override
        public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) {
            return delegate.run(type, parameters);
        }

        @Override
        public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) {
            return delegate.run(action, parameters);
        }

        @Override
        public void run(PhasedBuildAction phasedBuildAction, ConsumerOperationParameters operationParameters) {
            delegate.run(phasedBuildAction, parameters);
        }

        @Override
        public void runTests(TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
            delegate.runTests(testExecutionRequest, parameters);
        }

        @Override
        public void notifyDaemonsAboutChangedPaths(List<String> changedPaths, ConsumerOperationParameters operationParameters) {
            delegate.notifyDaemonsAboutChangedPaths(changedPaths, parameters);
        }

        @Override
        public void stopWhenIdle(ConsumerOperationParameters operationParameters) {
            delegate.stopWhenIdle(parameters);
        }
    }
}
