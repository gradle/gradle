/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.SourceObjectMapping;
import org.gradle.tooling.internal.build.VersionOnlyBuildEnvironment;
import org.gradle.tooling.internal.consumer.converters.GradleProjectConverter;
import org.gradle.tooling.internal.consumer.converters.PropertyHandlerFactory;
import org.gradle.tooling.internal.consumer.converters.TaskPropertyHandlerFactory;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.util.GradleVersion;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An adapter that wraps a {@link ConnectionVersion4} based provider.
 */
public class ConnectionVersion4BackedConsumerConnection extends AbstractPre12ConsumerConnection {
    private final ModelProducer modelProducer;

    public ConnectionVersion4BackedConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, getMetaData(delegate), adapter);
        ModelProducer consumerConnectionBackedModelProducer = new ConnectionVersion4BackedModelProducer(adapter, getVersionDetails(), modelMapping, delegate);
        ModelProducer gradleProjectAdapterProducer = new GradleProjectAdapterProducer(adapter, getVersionDetails(), modelMapping, consumerConnectionBackedModelProducer);
        ModelProducer producerWithGradleBuild = new GradleBuildAdapterProducer(adapter, getVersionDetails(), modelMapping, gradleProjectAdapterProducer);
        modelProducer = new BuildInvocationsAdapterProducer(adapter, getVersionDetails(), modelMapping, producerWithGradleBuild);
    }

    private static VersionDetails getMetaData(final ConnectionVersion4 delegate) {
        GradleVersion version = GradleVersion.version(delegate.getMetaData().getVersion());
        if (version.compareTo(GradleVersion.version("1.0-milestone-5")) < 0) {
            return new R10M3VersionDetails(delegate);
        } else {
            return new R10M5VersionDetails(delegate);
        }
    }

    @Override
    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        VersionDetails versionDetails = getVersionDetails();
        if (operationParameters.getJavaHome() != null) {
            throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setJavaHome() and buildLauncher.setJavaHome()", versionDetails.getVersion());
        }
        if (operationParameters.getJvmArguments() != null) {
            throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setJvmArguments() and buildLauncher.setJvmArguments()", versionDetails.getVersion());
        }
        if (operationParameters.getStandardInput() != null) {
            throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setStandardInput() and buildLauncher.setStandardInput()", versionDetails.getVersion());
        }
        OutputStream out = operationParameters.getStandardOutput();
        if (out != null) {
            try {
                String deprecationMessage = String.format("Connecting to Gradle version %s from the Gradle tooling API has been deprecated and is scheduled to be removed in version 2.0 of the Gradle tooling API%n", versionDetails.getVersion());
                out.write(deprecationMessage.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Cannot write to stream", e);
            }
        }
        return super.run(type, operationParameters);
    }

    @Override
    protected <T> T doGetModel(Class<T> modelType, ConsumerOperationParameters operationParameters) {
        return modelProducer.produceModel(modelType, operationParameters);
    }

    private static class R10M3VersionDetails extends VersionDetails {
        public R10M3VersionDetails(ConnectionVersion4 delegate) {
            super(delegate.getMetaData().getVersion());
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return modelType.equals(HierarchicalEclipseProject.class) || modelType.equals(EclipseProjectVersion3.class) || modelType.equals(EclipseProject.class) || modelType.equals(Void.class);
        }
    }

    private static class R10M5VersionDetails extends VersionDetails {
        public R10M5VersionDetails(ConnectionVersion4 delegate) {
            super(delegate.getMetaData().getVersion());
        }

        @Override
        public boolean supportsGradleProjectModel() {
            return true;
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return modelType.equals(HierarchicalEclipseProject.class)
                    || modelType.equals(EclipseProject.class)
                    || modelType.equals(IdeaProject.class)
                    || modelType.equals(BasicIdeaProject.class)
                    || modelType.equals(GradleProject.class)
                    || modelType.equals(Void.class);
        }
    }

    private class ConnectionVersion4BackedModelProducer extends AbstractModelProducer {
        private final ConnectionVersion4 delegate;
        private final Action<SourceObjectMapping> mapper;

        public ConnectionVersion4BackedModelProducer(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, ConnectionVersion4 delegate) {
            super(adapter, versionDetails, modelMapping);
            this.delegate = delegate;
            mapper = Actions.composite(
                    new PropertyHandlerFactory().forVersion(versionDetails),
                    new TaskPropertyHandlerFactory().forVersion(versionDetails));
        }

        public <T> T produceModel(Class<T> modelType, ConsumerOperationParameters operationParameters) {
            if (modelType == BuildEnvironment.class && !versionDetails.maySupportModel(BuildEnvironment.class)) {
                //early versions of provider do not support BuildEnvironment model
                //since we know the gradle version at least we can give back some result
                return adapter.adapt(modelType, new VersionOnlyBuildEnvironment(versionDetails.getVersion()), mapper);
            }
            if (!versionDetails.maySupportModel(modelType)) {
                //don't bother asking the provider for this model
                throw Exceptions.unsupportedModel(modelType, versionDetails.getVersion());
            }
            Class<? extends ProjectVersion3> protocolType = modelMapping.getProtocolType(modelType).asSubclass(ProjectVersion3.class);
            final ProjectVersion3 model = delegate.getModel(protocolType, operationParameters);
            return adapter.adapt(modelType, model, mapper);
        }
    }

    private class GradleProjectAdapterProducer extends AbstractModelProducer {
        private final ModelProducer delegate;

        public GradleProjectAdapterProducer(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, ModelProducer delegate) {
            super(adapter, versionDetails, modelMapping);
            this.delegate = delegate;
        }

        public <T> T produceModel(Class<T> modelType, ConsumerOperationParameters operationParameters) {
            if (modelType == GradleProject.class && !versionDetails.maySupportModel(GradleProject.class)) {
                //we broke compatibility around M9 wrt getting the tasks of a project (issue GRADLE-1875)
                //this patch enables getting gradle tasks for target gradle version pre M5
                EclipseProjectVersion3 project = delegate.produceModel(EclipseProjectVersion3.class, operationParameters);
                return adapter.adapt(modelType, new GradleProjectConverter().convert(project));
            }
            return delegate.produceModel(modelType, operationParameters);
        }
    }
}
