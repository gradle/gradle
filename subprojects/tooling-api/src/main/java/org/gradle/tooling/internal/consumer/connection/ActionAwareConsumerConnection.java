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
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.model.gradle.BuildInvocations;
import org.gradle.util.GradleVersion;

/**
 * An adapter for {@link InternalBuildActionExecutor}.
 *
 * <p>Used for providers >= 1.8 and <= 2.0</p>
 */
public class ActionAwareConsumerConnection extends AbstractPost12ConsumerConnection {
    private final ActionRunner actionRunner;
    private final ModelProducer modelProducer;

    public ActionAwareConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, getVersionDetails(delegate.getMetaData().getVersion()));
        ModelProducer modelProducer =  new ModelBuilderBackedModelProducer(adapter, getVersionDetails(), modelMapping, (ModelBuilder) delegate);
        if (!getVersionDetails().maySupportModel(BuildInvocations.class)) {
            modelProducer = new BuildInvocationsAdapterProducer(adapter, getVersionDetails(), modelProducer);

        }
        this.modelProducer = modelProducer;
        this.actionRunner = new InternalBuildActionExecutorBackedActionRunner((InternalBuildActionExecutor) delegate, adapter);
    }

    @Override
    protected ModelProducer getModelProducer() {
        return modelProducer;
    }

    @Override
    protected ActionRunner getActionRunner() {
        return actionRunner;
    }

    protected static VersionDetails getVersionDetails(String versionString) {
        GradleVersion version = GradleVersion.version(versionString);
        if (version.compareTo(GradleVersion.version("1.11")) > 0) {
            return new R112VersionDetails(version.getVersion());
        }
        return new R18VersionDetails(version.getVersion());
    }

    static class R18VersionDetails extends VersionDetails {
        private R18VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return modelType != BuildInvocations.class;
        }
    }

    static class R112VersionDetails extends VersionDetails {
        private R112VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return true;
        }

        @Override
        public boolean supportsTaskDisplayName() {
            return true;
        }
    }

    private static class InternalBuildActionExecutorBackedActionRunner implements ActionRunner {
        private final InternalBuildActionExecutor executor;
        private final ProtocolToModelAdapter adapter;

        private InternalBuildActionExecutorBackedActionRunner(InternalBuildActionExecutor executor, ProtocolToModelAdapter adapter) {
            this.executor = executor;
            this.adapter = adapter;
        }

        public <T> T run(final BuildAction<T> action, ConsumerOperationParameters operationParameters)
                throws UnsupportedOperationException, IllegalStateException {
            BuildResult<T> result;
            try {
                result = executor.run(new InternalBuildActionAdapter<T>(action, adapter), operationParameters);
            } catch (InternalBuildActionFailureException e) {
                throw new BuildActionFailureException("The supplied build action failed with an exception.", e.getCause());
            }
            return result.getModel();
        }
    }
}
