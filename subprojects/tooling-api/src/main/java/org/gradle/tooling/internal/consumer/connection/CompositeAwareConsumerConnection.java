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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.api.Transformer;
import org.gradle.internal.Cast;
import org.gradle.tooling.connection.ModelResult;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalCancellableConnection;
import org.gradle.tooling.internal.protocol.InternalCompositeAwareConnection;

/**
 * An adapter for {@link org.gradle.tooling.internal.protocol.InternalCompositeAwareConnection}.
 *
 * <p>Used for providers >= 2.13.</p>
 */
public class CompositeAwareConsumerConnection extends TestExecutionConsumerConnection {
    public CompositeAwareConsumerConnection(ConnectionVersion4 connection, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(connection, modelMapping, adapter);
    }

    @Override
    protected ModelProducer createModelProducer(InternalCancellableConnection connection, ModelMapping modelMapping, ProtocolToModelAdapter adapter, Transformer<RuntimeException, RuntimeException> exceptionTransformer) {
        ModelProducer delegate = super.createModelProducer(connection, modelMapping, adapter, exceptionTransformer);
        return new CompositeAwareModelProducer(delegate, adapter, getVersionDetails(), modelMapping, (InternalCompositeAwareConnection) connection, exceptionTransformer);
    }

    protected MultiModelProducer getMultiModelProducer() {
        return Cast.uncheckedCast(getModelProducer());
    }

    @Override
    public <T> Iterable<ModelResult<T>> buildModels(Class<T> elementType, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        return getMultiModelProducer().produceModels(elementType, operationParameters);
    }
}
