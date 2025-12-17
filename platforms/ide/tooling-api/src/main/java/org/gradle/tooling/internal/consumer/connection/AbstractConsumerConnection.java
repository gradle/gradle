/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.PhasedBuildAction;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.model.internal.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractConsumerConnection extends HasCompatibilityMapping implements ConsumerConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConsumerConnection.class);
    private final ConnectionVersion4 delegate;
    private final VersionDetails providerMetaData;

    public AbstractConsumerConnection(ConnectionVersion4 delegate, VersionDetails providerMetaData) {
        this.delegate = delegate;
        this.providerMetaData = providerMetaData;
    }

    @Override
    public void stop() {
    }

    @Override
    public String getDisplayName() {
        return delegate.getMetaData().getDisplayName();
    }

    public VersionDetails getVersionDetails() {
        return providerMetaData;
    }

    public ConnectionVersion4 getDelegate() {
        return delegate;
    }

    public abstract void configure(ConnectionParameters connectionParameters);

    protected abstract ModelProducer getModelProducer();

    protected abstract ActionRunner getActionRunner();

    @Override
    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (isHelpOrVersion(operationParameters)) {
            operationParameters = removeHelpVersionArgs(operationParameters);
        }
        return getModelProducer().produceModel(type, operationParameters);
    }

    @Override
    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) {
        if (isHelpOrVersion(operationParameters)) {
            operationParameters = removeHelpVersionArgs(operationParameters);
        }
        return getActionRunner().run(action, operationParameters);
    }

    @Override
    public void run(PhasedBuildAction phasedBuildAction, ConsumerOperationParameters operationParameters) {
        if (isHelpOrVersion(operationParameters)) {
            operationParameters = removeHelpVersionArgs(operationParameters);
        }
        doRun(phasedBuildAction, operationParameters);
    }

    protected void doRun(PhasedBuildAction phasedBuildAction, ConsumerOperationParameters operationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), getVersionDetails().getVersion(), "4.8");
    }

    @Override
    public void runTests(final TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        if (isHelpOrVersion(operationParameters)) {
            operationParameters = removeHelpVersionArgs(operationParameters);
        }
        doRunTests(testExecutionRequest, operationParameters);
    }

    protected void doRunTests(final TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), getVersionDetails().getVersion(), "2.6");
    }

    private boolean isHelpOrVersion(ConsumerOperationParameters operationParameters) {
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

    private ConsumerOperationParameters removeHelpVersionArgs(ConsumerOperationParameters parameters) {
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

        if (removed) {
            LOGGER.warn("The Tooling API does not support --help, --version or --show-version arguments for this operation. These arguments have been ignored.");
        }

        ConsumerOperationParameters.Builder builder = ConsumerOperationParameters.builder();
        builder.copyFrom(parameters);
        builder.setEntryPoint(entryPoint);
        builder.setParameters(connectionParams);
        builder.setArguments(filteredArgs);

        return builder.build();
    }

    @Override
    public void notifyDaemonsAboutChangedPaths(List<String> changedPaths, ConsumerOperationParameters operationParameters) {
        // Default is no-op
    }

    @Override
    public void stopWhenIdle(ConsumerOperationParameters operationParameters) {
        // Default is no-op
    }
}
