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
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalCancellableConnection;

import java.io.File;

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
        Transformer<RuntimeException, RuntimeException> exceptionTransformer = new ExceptionTransformer();
        InternalCancellableConnection connection = (InternalCancellableConnection) delegate;
        modelProducer = createModelProducer(connection, modelMapping, adapter, exceptionTransformer);
        actionRunner = new CancellableActionRunner(connection, exceptionTransformer, getVersionDetails());
    }

    protected ModelProducer createModelProducer(InternalCancellableConnection connection, ModelMapping modelMapping, ProtocolToModelAdapter adapter, Transformer<RuntimeException, RuntimeException> exceptionTransformer) {
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

    private static class ExceptionTransformer implements Transformer<RuntimeException, RuntimeException> {
        public RuntimeException transform(RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if ("org.gradle.api.BuildCancelledException".equals(t.getClass().getName())
                    || "org.gradle.tooling.BuildCancelledException".equals(t.getClass().getName())) {
                    return new InternalBuildCancelledException(e.getCause());
                }
            }
            return e;
        }
    }

    private static class CancellableActionRunner implements ActionRunner {
        private final InternalCancellableConnection executor;
        private final Transformer<RuntimeException, RuntimeException> exceptionTransformer;
        private final VersionDetails versionDetails;

        private CancellableActionRunner(InternalCancellableConnection executor, Transformer<RuntimeException, RuntimeException> exceptionTransformer, VersionDetails versionDetails) {
            this.executor = executor;
            this.exceptionTransformer = exceptionTransformer;
            this.versionDetails = versionDetails;
        }

        public <T> T run(final BuildAction<T> action, ConsumerOperationParameters operationParameters)
            throws UnsupportedOperationException, IllegalStateException {

            File rootDir = operationParameters.getProjectDir();
            BuildResult<T> result;
            try {
                try {
                    result = executor.run(new InternalBuildActionAdapter<T>(action, rootDir, versionDetails), new BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters);
                } catch (RuntimeException e) {
                    throw exceptionTransformer.transform(e);
                }
            } catch (InternalBuildActionFailureException e) {
                throw new BuildActionFailureException("The supplied build action failed with an exception.", e.getCause());
            }
            return result.getModel();
        }
    }
}
