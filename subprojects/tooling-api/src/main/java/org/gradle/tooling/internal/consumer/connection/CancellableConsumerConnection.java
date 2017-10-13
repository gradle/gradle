/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalCancellableConnection;

/**
 * An adapter for {@link InternalCancellableConnection}.
 *
 * <p>Used for providers >= 2.1.</p>
 */
public class CancellableConsumerConnection extends AbstractPost12ConsumerConnection {
    private final ActionRunner actionRunner;
    private final ModelProducer modelProducer;

    public CancellableConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, VersionDetails.from(delegate.getMetaData().getVersion()));
        Transformer<RuntimeException, RuntimeException> exceptionTransformer = new CancellationExceptionTransformer();
        final InternalCancellableConnection connection = (InternalCancellableConnection) delegate;
        modelProducer = createModelProducer(connection, modelMapping, adapter, exceptionTransformer);
        actionRunner = new CancellableActionRunner(connection, exceptionTransformer, getVersionDetails());
    }

    private ModelProducer createModelProducer(InternalCancellableConnection connection, ModelMapping modelMapping, ProtocolToModelAdapter adapter, Transformer<RuntimeException, RuntimeException> exceptionTransformer) {
        return new PluginClasspathInjectionSupportedCheckModelProducer(
            getVersionDetails().getVersion(),
            new CancellableModelBuilderBackedModelProducer(adapter, getVersionDetails(), modelMapping, connection, exceptionTransformer)
        );
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
