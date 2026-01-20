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
import org.gradle.tooling.internal.consumer.PhasedBuildAction;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.build.Help;
import org.gradle.tooling.model.internal.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class HelpAndVersionHandlingConsumerConnection extends AbstractConsumerConnection {

    public HelpAndVersionHandlingConsumerConnection(ConnectionVersion4 delegate, VersionDetails providerMetaData) {
        super(delegate, providerMetaData);
    }

    @Override
    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (operationParameters.containsHelpOrVersionArgs() && type == Void.class && getVersionDetails().supportsHelpToolingModel()) {
            // For task execution, handle --help/--version and skip task execution
            return handleHelpOrVersion(type, operationParameters);
        }
        // For model requests, remove help/version args and continue
        return getModelProducer().produceModel(type, removeHelpVersionArgs(operationParameters));
    }

    @Override
    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) {
        return getActionRunner().run(action, removeHelpVersionArgs(operationParameters));
    }

    @Override
    public void run(PhasedBuildAction phasedBuildAction, ConsumerOperationParameters operationParameters) {
        doRun(phasedBuildAction, removeHelpVersionArgs(operationParameters));
    }

    protected void doRun(PhasedBuildAction phasedBuildAction, ConsumerOperationParameters operationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), getVersionDetails().getVersion(), "4.8");
    }

    @Override
    public void runTests(final TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        doRunTests(testExecutionRequest, removeHelpVersionArgs(operationParameters));
    }

    protected void doRunTests(final TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), getVersionDetails().getVersion(), "2.6");
    }

    private <T> T handleHelpOrVersion(Class<T> type, ConsumerOperationParameters operationParameters) {
        boolean containsHelpArg = operationParameters.containsHelpArg();
        boolean containsVersionArg = operationParameters.containsVersionArg();
        boolean containsShowVersionArg = operationParameters.containsShowVersionArg();

        ConsumerOperationParameters cleanParams = operationParameters.withoutHelpOrVersionArgs();
        ConsumerOperationParameters modelParams = cleanParams.withNoTasks();

        // help was requested: print help and omit producing the model
        if (containsHelpArg) {
            queryAndPrintHelp(operationParameters, modelParams);
            return null;
        }

        // version was requested: print version information
        if (containsVersionArg || containsShowVersionArg) {
            queryAndPrintVersion(operationParameters, modelParams);
        }

        // if version was requested via --version, omit producing the model
        if (containsVersionArg) {
            return null;
        }

        // if version was requested via --show-version, proceed to produce the model
        if (containsShowVersionArg) {
            return getModelProducer().produceModel(type, cleanParams);
        }

        return null;
    }

    private void queryAndPrintHelp(ConsumerOperationParameters operationParameters, ConsumerOperationParameters modelParams){
        OutputStream standardOutput = operationParameters.getStandardOutput();
        Help helpModel = getModelProducer().produceModel(Help.class, modelParams);
        print(standardOutput, helpModel.getRenderedText());
    }

    private void queryAndPrintVersion(ConsumerOperationParameters operationParameters, ConsumerOperationParameters modelParams) {
        BuildEnvironment env = getModelProducer().produceModel(BuildEnvironment.class, modelParams);
        OutputStream standardOutput = operationParameters.getStandardOutput();
        String output = env.getVersionInfo();
        print(standardOutput, output);
    }

    private static void print(OutputStream stdOut, String content) {
        try {
            if (stdOut != null) {
                stdOut.write(content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot write to stdout", e);
        }
    }

    private static ConsumerOperationParameters removeHelpVersionArgs(ConsumerOperationParameters parameters) {
        if (!parameters.containsHelpOrVersionArgs()) {
            return parameters;
        }
        print(parameters.getStandardError(), "The Tooling API does not support --help, --version or --show-version arguments for this operation. These arguments have been ignored.");
        return parameters.withoutHelpOrVersionArgs();
    }
}
