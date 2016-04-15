/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.connection;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.connection.ModelResult;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.model.UnsupportedMethodException;

public class ToolingClientConsumerConnection implements ConsumerConnection {
    @Override
    public void stop() {
        // stateless, nothing to stop
    }

    @Override
    public String getDisplayName() {
        return "client-side composite connection";
    }

    @Override
    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        if (type == Void.class) {
            new ToolingClientCompositeBuildLauncher(operationParameters).run();
            return null;
        }
        // TODO: What would this be for?
        return unsupportedMethod();
    }

    @Override
    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        return unsupportedMethod();
    }

    @Override
    public void runTests(TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        unsupportedMethod();
    }

    @Override
    public <T> Iterable<ModelResult<T>> buildModels(Class<T> elementType, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        return new ToolingClientCompositeModelBuilder<T>(elementType, operationParameters).get();
    }

    private <T> T unsupportedMethod() {
        throw new UnsupportedMethodException("Not supported for composite connections.");
    }
}
