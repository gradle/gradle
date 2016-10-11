/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalBuildActionExecutor;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.ModelBuilder;
import org.gradle.tooling.model.gradle.BuildInvocations;

import java.io.File;

/**
 * An adapter for {@link InternalBuildActionExecutor}.
 *
 * <p>Used for providers >= 1.8 and <= 2.0</p>
 */
public class ActionAwareConsumerConnection extends AbstractPost12ConsumerConnection {
    private final ActionRunner actionRunner;
    private final ModelProducer modelProducer;

    public ActionAwareConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, VersionDetails.from(delegate.getMetaData().getVersion()));
        ModelProducer modelProducer =  new ModelBuilderBackedModelProducer(adapter, getVersionDetails(), modelMapping, (ModelBuilder) delegate);
        if (!getVersionDetails().maySupportModel(BuildInvocations.class)) {
            modelProducer = new BuildInvocationsAdapterProducer(adapter, getVersionDetails(), modelProducer);

        }
        this.modelProducer = modelProducer;
        this.actionRunner = new InternalBuildActionExecutorBackedActionRunner((InternalBuildActionExecutor) delegate, getVersionDetails());
    }

    @Override
    protected ModelProducer getModelProducer() {
        return modelProducer;
    }

    @Override
    protected ActionRunner getActionRunner() {
        return actionRunner;
    }

    private static class InternalBuildActionExecutorBackedActionRunner implements ActionRunner {
        private final InternalBuildActionExecutor executor;
        private final VersionDetails versionDetails;

        private InternalBuildActionExecutorBackedActionRunner(InternalBuildActionExecutor executor, VersionDetails versionDetails) {
            this.executor = executor;
            this.versionDetails = versionDetails;
        }

        public <T> T run(final BuildAction<T> action, ConsumerOperationParameters operationParameters)
                throws UnsupportedOperationException, IllegalStateException {
            BuildResult<T> result;

            File rootDir = operationParameters.getProjectDir();
            try {
                result = executor.run(new InternalBuildActionAdapter<T>(action, rootDir, versionDetails), operationParameters);
            } catch (InternalBuildActionFailureException e) {
                throw new BuildActionFailureException("The supplied build action failed with an exception.", e.getCause());
            }
            return result.getModel();
        }
    }
}
