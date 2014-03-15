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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.ModelBuilder;
import org.gradle.tooling.model.gradle.BuildInvocations;
import org.gradle.tooling.model.gradle.GradleBuild;
import org.gradle.util.GradleVersion;

/**
 * An adapter for a {@link ModelBuilder} based provider.
 */
public class ModelBuilderBackedConsumerConnection extends AbstractPost12ConsumerConnection {
    private final ModelProducer modelProducer;

    public ModelBuilderBackedConsumerConnection(ConnectionVersion4 delegate, ModelMapping modelMapping, ProtocolToModelAdapter adapter) {
        super(delegate, getVersionDetails(delegate.getMetaData().getVersion()));
        ModelBuilder builder = (ModelBuilder) delegate;
        ModelProducer consumerConnectionBackedModelProducer = new ModelBuilderBackedModelProducer(adapter, getVersionDetails(), modelMapping, builder);
        ModelProducer producerWithGradleBuild = new GradleBuildAdapterProducer(adapter, getVersionDetails(), modelMapping, consumerConnectionBackedModelProducer);
        modelProducer = new BuildInvocationsAdapterProducer(adapter, getVersionDetails(), modelMapping, producerWithGradleBuild);
    }

    public static VersionDetails getVersionDetails(String versionString) {
        GradleVersion version = GradleVersion.version(versionString);
        if (version.compareTo(GradleVersion.version("1.11")) > 0) {
            return new R112VersionDetails(version.getVersion());
        }
        if (version.compareTo(GradleVersion.version("1.8-rc-1")) >= 0) {
            return new R18VersionDetails(version.getVersion());
        }
        return new R16VersionDetails(version.getVersion());
    }

    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        return modelProducer.produceModel(type, operationParameters);
    }

    private static class R16VersionDetails extends VersionDetails {
        public R16VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            return modelType != BuildInvocations.class
                    && modelType != GradleBuild.class;
        }

        @Override
        public boolean supportsGradleProjectModel() {
            return true;
        }
    }

    private static class R18VersionDetails extends R16VersionDetails {
        private R18VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            if (modelType == GradleBuild.class) {
                return true;
            }
            return super.maySupportModel(modelType);
        }
    }

    private static class R112VersionDetails extends R18VersionDetails {
        private R112VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean maySupportModel(Class<?> modelType) {
            if (modelType == BuildInvocations.class) {
                return true;
            }
            return super.maySupportModel(modelType);
        }

        @Override
        public boolean supportsTaskDisplayName() {
            return true;
        }
    }
}
