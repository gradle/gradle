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

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionFailureException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalCancellableConnection;

public class CancellableConsumerConnection extends AbstractPost12ConsumerConnection {
    private final ActionRunner actionRunner;
    private final ModelProducer modelProducer;

    public CancellableConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, new R21VersionDetails(delegate.getMetaData().getVersion()));
        InternalCancellableConnection connection = (InternalCancellableConnection) delegate;
        modelProducer = new CancellableModelBuilderBackedModelProducer(adapter, getVersionDetails(), modelMapping, connection);
        actionRunner = new CancellableActionRunner(connection, adapter);
    }

    @Override
    protected ActionRunner getActionRunner() {
        return actionRunner;
    }

    @Override
    protected ModelProducer getModelProducer() {
        return modelProducer;
    }

    private static class R21VersionDetails extends VersionDetails {
        private R21VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean supportsTaskDisplayName() {
            return true;
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return true;
        }

        @Override
        public boolean supportsCancellation() {
            return true;
        }
    }

    private static class CancellableActionRunner implements ActionRunner {
        private final InternalCancellableConnection executor;
        private final ProtocolToModelAdapter adapter;

        private CancellableActionRunner(InternalCancellableConnection executor, ProtocolToModelAdapter adapter) {
            this.executor = executor;
            this.adapter = adapter;
        }

        public <T> T run(final BuildAction<T> action, ConsumerOperationParameters operationParameters)
                throws UnsupportedOperationException, IllegalStateException {
            BuildResult<T> result;
            try {
                result = executor.run(new InternalBuildActionAdapter<T>(action, adapter), new BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters);
            } catch (InternalBuildActionFailureException e) {
                throw new BuildActionFailureException("The supplied build action failed with an exception.", e.getCause());
            }
            return result.getModel();
        }
    }
}
