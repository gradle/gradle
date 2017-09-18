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

import org.gradle.api.Transformer;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.BuildParameters;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalCancellableConnectionVersion2;
import org.gradle.tooling.internal.protocol.InternalCancellationToken;
import org.gradle.tooling.internal.protocol.ModelIdentifier;

/**
 * An adapter for {@link InternalCancellableConnectionVersion2}.
 *
 * <p>Used for providers >= 4.3.</p>
 */
public class ParameterCancellableConsumerConnection extends TestExecutionConsumerConnection {
    private final ActionRunner actionRunner;
    private final ModelProducer modelProducer;

    public ParameterCancellableConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, modelMapping, adapter);
        final InternalCancellableConnectionVersion2 connection = (InternalCancellableConnectionVersion2) delegate;
        InternalCancellableConnectionWrapper connectionWrapper = new InternalCancellableConnectionWrapper() {
            @Override
            public <T> BuildResult<T> run(InternalBuildActionAdapter<T> action, InternalCancellationToken cancellationToken, BuildParameters operationParameters) {
                return connection.run(action, cancellationToken, operationParameters);
            }

            @Override
            public BuildResult<?> getModel(ModelIdentifier modelIdentifier, InternalCancellationToken cancellationToken, BuildParameters operationParameters) {
                return connection.getModel(modelIdentifier, cancellationToken, operationParameters);
            }
        };
        Transformer<RuntimeException, RuntimeException> exceptionTransformer = new ExceptionTransformer();
        modelProducer = createModelProducer(connectionWrapper, modelMapping, adapter, exceptionTransformer);
        actionRunner = new CancellableActionRunner(connectionWrapper, exceptionTransformer, getVersionDetails());
    }

    @Override
    protected ActionRunner getActionRunner() {
        return actionRunner;
    }

    @Override
    protected ModelProducer getModelProducer() {
        return modelProducer;
    }
}
