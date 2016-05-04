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

import org.gradle.tooling.internal.adapter.CompatibleIntrospector;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalConnection;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GradleVersion;

import java.io.PrintStream;

/**
 * An adapter for a {@link InternalConnection} based provider.
 *
 * <p>Used for providers >= 1.0-milestone-8 and <= 1.1. Will be removed in Gradle 3.0</p>
 */
public class InternalConnectionBackedConsumerConnection extends AbstractConsumerConnection {
    private final ModelProducer modelProducer;
    private final UnsupportedActionRunner actionRunner;

    public InternalConnectionBackedConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, new R10M8VersionDetails(delegate.getMetaData().getVersion()));
        ModelProducer modelProducer = new InternalConnectionBackedModelProducer(adapter, getVersionDetails(), modelMapping, (InternalConnection) delegate, this);
        modelProducer = new GradleBuildAdapterProducer(adapter, modelProducer, this);
        modelProducer = new BuildInvocationsAdapterProducer(adapter, getVersionDetails(), modelProducer);
        modelProducer = new BuildExecutingModelProducer(modelProducer);
        if (GradleVersion.version(getVersionDetails().getVersion()).compareTo(GradleVersion.version("1.0")) < 0) {
            modelProducer = new NoCommandLineArgsModelProducer(modelProducer);
        }
        modelProducer = new DeprecationWarningModelProducer(modelProducer);
        this.modelProducer = modelProducer;
        this.actionRunner = new UnsupportedActionRunner(getVersionDetails().getVersion());
    }

    @Override
    protected ActionRunner getActionRunner() {
        return actionRunner;
    }

    @Override
    protected ModelProducer getModelProducer() {
        return modelProducer;
    }

    @Override
    public void configure(ConnectionParameters connectionParameters) {
        new CompatibleIntrospector(getDelegate()).callSafely("configureLogging", connectionParameters.getVerboseLogging());
    }

    private static class R10M8VersionDetails extends VersionDetails {
        public R10M8VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return modelType.equals(Void.class)
                    || modelType.equals(HierarchicalEclipseProject.class)
                    || modelType.equals(EclipseProject.class)
                    || modelType.equals(IdeaProject.class)
                    || modelType.equals(BasicIdeaProject.class)
                    || modelType.equals(GradleProject.class)
                    || modelType.equals(BuildEnvironment.class);
        }
    }

    private class DeprecationWarningModelProducer implements ModelProducer {
        private final ModelProducer delegate;

        public DeprecationWarningModelProducer(ModelProducer delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
            String message = "Support for Gradle version " + getVersionDetails().getVersion() + " is deprecated and will be removed in tooling API version 3.0. You should upgrade your Gradle build to use Gradle 1.2 or later.";
            DeprecationLogger.nagUserWith(message);
            if (operationParameters.getStandardOutput() != null) {
                PrintStream printStream = new PrintStream(operationParameters.getStandardOutput());
                printStream.println(message);
                printStream.flush();
            }
            return delegate.produceModel(type, operationParameters);
        }
    }

    private class NoCommandLineArgsModelProducer implements ModelProducer {
        private final ModelProducer delegate;

        public NoCommandLineArgsModelProducer(ModelProducer delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
            if (operationParameters.getArguments() != null && !operationParameters.getArguments().isEmpty()) {
                 throw Exceptions.unsupportedOperationConfiguration(operationParameters.getEntryPointName() + " withArguments()", getVersionDetails().getVersion(), "1.0");
            }
            return delegate.produceModel(type, operationParameters);
        }
    }

    private class BuildExecutingModelProducer implements ModelProducer {
        private final ModelProducer delegate;

        private BuildExecutingModelProducer(ModelProducer delegate) {
            this.delegate = delegate;
        }

        public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
            if (type.equals(Void.class)) {
                getDelegate().executeBuild(operationParameters, operationParameters);
                return null;
            } else {
                if (operationParameters.getTasks() != null) {
                    throw Exceptions.unsupportedOperationConfiguration(operationParameters.getEntryPointName() + " forTasks()", getVersionDetails().getVersion(), "1.2");
                }
                return delegate.produceModel(type, operationParameters);
            }
        }
    }

    private static class InternalConnectionBackedModelProducer implements ModelProducer {
        private final ProtocolToModelAdapter adapter;
        private final VersionDetails versionDetails;
        private final ModelMapping modelMapping;
        private final InternalConnection delegate;
        private final HasCompatibilityMapping mapperProvider;

        public InternalConnectionBackedModelProducer(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, InternalConnection delegate, HasCompatibilityMapping mapperProvider) {
            this.adapter = adapter;
            this.versionDetails = versionDetails;
            this.modelMapping = modelMapping;
            this.delegate = delegate;
            this.mapperProvider = mapperProvider;
        }

        public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
            if (!versionDetails.maySupportModel(type)) {
                //don't bother asking the provider for this model
                throw Exceptions.unsupportedModel(type, versionDetails.getVersion());
            }
            Class<?> protocolType = modelMapping.getProtocolType(type);
            return adapter.adapt(type, delegate.getTheModel(protocolType, operationParameters), mapperProvider.getCompatibilityMapping(operationParameters));
        }
    }
}
