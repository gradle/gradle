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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.build.VersionOnlyBuildEnvironment;
import org.gradle.tooling.internal.consumer.converters.GradleProjectConverter;
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

/**
 * An adapter that wraps a {@link ConnectionVersion4} based provider.
 */
public class ConnectionVersion4BackedConsumerConnection extends AbstractPre12ConsumerConnection {
    private final ModelMapping modelMapping;

    public ConnectionVersion4BackedConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, getMetaData(delegate), adapter);
        this.modelMapping = modelMapping;
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
    protected Object doGetModel(Class<?> modelType, ConsumerOperationParameters operationParameters) {
        VersionDetails versionDetails = getVersionDetails();
        if (modelType == BuildEnvironment.class && !versionDetails.isModelSupported(BuildEnvironment.class)) {
            //early versions of provider do not support BuildEnvironment model
            //since we know the gradle version at least we can give back some result
            return new VersionOnlyBuildEnvironment(versionDetails.getVersion());
        }
        if (modelType == GradleProject.class && !versionDetails.isModelSupported(GradleProject.class)) {
            //we broke compatibility around M9 wrt getting the tasks of a project (issue GRADLE-1875)
            //this patch enables getting gradle tasks for target gradle version pre M5
            EclipseProjectVersion3 project = (EclipseProjectVersion3) getDelegate().getModel(EclipseProjectVersion3.class, operationParameters);
            return new GradleProjectConverter().convert(project);
        }
        if (!versionDetails.isModelSupported(modelType)) {
            //don't bother asking the provider for this model
            throw Exceptions.unknownModel(modelType, versionDetails.getVersion());
        }
        Class<? extends ProjectVersion3> protocolType = modelMapping.getProtocolType(modelType).asSubclass(ProjectVersion3.class);
        return getDelegate().getModel(protocolType, operationParameters);
    }

    private static class R10M3VersionDetails extends VersionDetails {
        public R10M3VersionDetails(ConnectionVersion4 delegate) {
            super(delegate.getMetaData().getVersion());
        }

        @Override
        public boolean isModelSupported(Class<?> modelType) {
            return modelType.equals(HierarchicalEclipseProject.class) || modelType.equals(EclipseProject.class) || modelType.equals(Void.class);
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
        public boolean isModelSupported(Class<?> modelType) {
            return modelType.equals(HierarchicalEclipseProject.class)
                    || modelType.equals(EclipseProject.class)
                    || modelType.equals(IdeaProject.class)
                    || modelType.equals(BasicIdeaProject.class)
                    || modelType.equals(GradleProject.class)
                    || modelType.equals(Void.class);
        }
    }
}
