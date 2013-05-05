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
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.gradle.util.GradleVersion;

/**
 * An implementation that wraps a protocol instance that has rigid compatibility policy.
 * <p>
 * by Szczepan Faber, created at: 12/22/11
 */
public class ConnectionVersion4BackedConsumerConnection extends AbstractPre12ConsumerConnection {

    public ConnectionVersion4BackedConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, getMetaData(delegate), modelMapping, adapter);
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
    protected Object doGetModel(Class<?> protocolType, ConsumerOperationParameters operationParameters) {
        return getDelegate().getModel(protocolType.asSubclass(ProjectVersion3.class), operationParameters);
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
