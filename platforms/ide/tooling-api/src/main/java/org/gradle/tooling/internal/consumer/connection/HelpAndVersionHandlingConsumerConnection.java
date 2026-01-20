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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.PhasedBuildAction;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.Help;
import org.gradle.tooling.model.internal.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class HelpAndVersionHandlingConsumerConnection extends AbstractConsumerConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(HelpAndVersionHandlingConsumerConnection.class);

    public HelpAndVersionHandlingConsumerConnection(ConnectionVersion4 delegate, VersionDetails providerMetaData) {
        super(delegate, providerMetaData);
    }

    @Override
    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (isHelpOrVersion(operationParameters)) {
            if (type == Void.class) {
                // For task execution, handle --help/--version and skip task execution
                return handleHelpOrVersion(type, operationParameters);
            }
            // For model requests, remove help/version args and continue
            operationParameters = removeHelpVersionArgs(operationParameters, true);
        }
        return getModelProducer().produceModel(type, operationParameters);
    }

    @Override
    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) {
        if (isHelpOrVersion(operationParameters)) {
            operationParameters = removeHelpVersionArgs(operationParameters, true);
        }
        return getActionRunner().run(action, operationParameters);
    }

    @Override
    public void run(PhasedBuildAction phasedBuildAction, ConsumerOperationParameters operationParameters) {
        if (isHelpOrVersion(operationParameters)) {
            operationParameters = removeHelpVersionArgs(operationParameters, true);
        }
        doRun(phasedBuildAction, operationParameters);
    }

    protected void doRun(PhasedBuildAction phasedBuildAction, ConsumerOperationParameters operationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), getVersionDetails().getVersion(), "4.8");
    }

    @Override
    public void runTests(final TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        if (isHelpOrVersion(operationParameters)) {
            operationParameters = removeHelpVersionArgs(operationParameters, true);
        }
        doRunTests(testExecutionRequest, operationParameters);
    }

    protected void doRunTests(final TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), getVersionDetails().getVersion(), "2.6");
    }

    private static boolean isHelpOrVersion(ConsumerOperationParameters operationParameters) {
        List<String> arguments = operationParameters.getArguments();
        if (arguments == null || arguments.isEmpty()) {
            return false;
        }
        for (String arg : arguments) {
            if ("--help".equals(arg) || "-h".equals(arg) || "-?".equals(arg) ||
                "--version".equals(arg) || "-v".equals(arg) ||
                "--show-version".equals(arg) || "-V".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private <T> T handleHelpOrVersion(Class<T> type, ConsumerOperationParameters operationParameters) {
        List<String> args = operationParameters.getArguments();
        boolean help = args.contains("--help") || args.contains("-h") || args.contains("-?");
        boolean version = args.contains("--version") || args.contains("-v");
        boolean showVersion = args.contains("--show-version") || args.contains("-V");

        ConsumerOperationParameters cleanParams = removeHelpVersionArgs(operationParameters, false);

        // Create params for model fetching (no tasks)
        ConsumerOperationParameters.Builder modelParamsBuilder = ConsumerOperationParameters.builder();
        modelParamsBuilder.copyFrom(cleanParams);
        modelParamsBuilder.setEntryPoint(cleanParams.getEntryPointName());
        modelParamsBuilder.setParameters(cleanParams.getConnectionParameters());
        modelParamsBuilder.setTasks(Collections.emptyList());
        ConsumerOperationParameters modelParams = modelParamsBuilder.build();


        try {
            // --help takes precedence over version flags (matching CLI behavior)
            if (help) {
                try {
                    Help helpModel = getModelProducer().produceModel(Help.class, modelParams);
                    operationParameters.getStandardOutput().write(helpModel.getRenderedText().getBytes(StandardCharsets.UTF_8));
                } catch (UnknownModelException e) {
                    // Fallback if Help model is not supported by this Gradle version
                    operationParameters.getStandardOutput().write(("Failed to get help content for the selected Gradle distribution." + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                }
                return null;
            }

            if (version || showVersion) {
                BuildEnvironment env = getModelProducer().produceModel(BuildEnvironment.class, modelParams);
                try {
                    String output = env.getVersionInfo();
                    operationParameters.getStandardOutput().write(output.getBytes(StandardCharsets.UTF_8));
                } catch (UnsupportedMethodException e) {
                    // Fallback for older Gradle versions that don't have getVersionInfo()
                    String fallback = "Gradle " + env.getGradle().getGradleVersion() + System.lineSeparator();
                    operationParameters.getStandardOutput().write(fallback.getBytes(StandardCharsets.UTF_8));
                }
                if (version) {
                    return null;
                }
            }

            // --show-version: version was printed above, now continue with build
            if (showVersion) {
                return getModelProducer().produceModel(type, cleanParams);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private static ConsumerOperationParameters removeHelpVersionArgs(ConsumerOperationParameters parameters, boolean warn) {
        ConnectionParameters connectionParams = parameters.getConnectionParameters();
        String entryPoint = parameters.getEntryPointName();

        List<String> originalArgs = parameters.getArguments();
        if (originalArgs == null || originalArgs.isEmpty()) {
            return parameters;
        }
        List<String> filteredArgs = new ArrayList<>(originalArgs);
        boolean removed = filteredArgs.removeIf(arg -> "--help".equals(arg) || "-h".equals(arg) || "-?".equals(arg) ||
            "--version".equals(arg) || "-v".equals(arg) ||
            "--show-version".equals(arg) || "-V".equals(arg));

        if (removed && warn) {
            LOGGER.warn("The Tooling API does not support --help, --version or --show-version arguments for this operation. These arguments have been ignored.");
        }

        ConsumerOperationParameters.Builder builder = ConsumerOperationParameters.builder();
        builder.copyFrom(parameters);
        builder.setEntryPoint(entryPoint);
        builder.setParameters(connectionParams);
        builder.setArguments(filteredArgs);

        return builder.build();
    }
}
