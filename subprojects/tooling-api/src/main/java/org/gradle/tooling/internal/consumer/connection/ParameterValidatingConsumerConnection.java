/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.model.internal.Exceptions;

public class ParameterValidatingConsumerConnection implements ConsumerConnection {
    private final VersionDetails targetVersionDetails;
    private final ConsumerConnection delegate;

    public ParameterValidatingConsumerConnection(VersionDetails targetVersionDetails, ConsumerConnection connection) {
        this.targetVersionDetails = targetVersionDetails;
        this.delegate = connection;
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
    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        validateParameters(operationParameters);
        return delegate.run(type, operationParameters);
    }

    @Override
    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        validateParameters(operationParameters);
        validateBuildActionParameters(operationParameters);
        return delegate.run(action, operationParameters);
    }

    @Override
    public void runTests(TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        validateParameters(operationParameters);
        delegate.runTests(testExecutionRequest, operationParameters);
    }

    private void validateParameters(ConsumerOperationParameters operationParameters) {
        if (!targetVersionDetails.supportsEnvironmentVariablesCustomization()) {
            if (operationParameters.getEnvironmentVariables() != null) {
                throw Exceptions.unsupportedFeature("environment variables customization feature", targetVersionDetails.getVersion(), "3.5");
            }
        }
    }

    private void validateBuildActionParameters(ConsumerOperationParameters operationParameters) {
        if (!targetVersionDetails.supportsRunTasksBeforeExecutingAction()) {
            if (operationParameters.getTasks() != null) {
                throw Exceptions.unsupportedFeature("forTasks() method on BuildActionExecuter", targetVersionDetails.getVersion(), "3.5");
            }
        }
    }
}
