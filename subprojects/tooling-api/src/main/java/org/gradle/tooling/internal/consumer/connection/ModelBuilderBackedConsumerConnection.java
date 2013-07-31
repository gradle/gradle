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
import org.gradle.tooling.internal.protocol.ModelIdentifier;

/**
 * An adapter for a {@link ModelBuilder} based provider.
 */
public class ModelBuilderBackedConsumerConnection extends AbstractPost12ConsumerConnection {
    private final ModelBuilder builder;
    private final ProtocolToModelAdapter adapter;

    public ModelBuilderBackedConsumerConnection(ConnectionVersion4 delegate, ProtocolToModelAdapter adapter) {
        super(delegate, new R16VersionDetails(delegate.getMetaData().getVersion()));
        this.adapter = adapter;
        builder = (ModelBuilder) delegate;
    }

    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        ModelIdentifier modelIdentifier = mapModelTypeToModelIdentifier(type);
        Object model = builder.getModel(modelIdentifier, operationParameters).getModel();
        return adapter.adapt(type, model);
    }

    private ModelIdentifier mapModelTypeToModelIdentifier(final Class<?> modelType) {
        if (modelType.equals(Void.class)) {
            return new DefaultModelIdentifier(ModelIdentifier.NULL_MODEL);
        }
        String modelName = new ModelMapping().getModelName(modelType);
        if (modelName != null) {
            return new DefaultModelIdentifier(modelName);
        }
        return new DefaultModelIdentifier(modelType.getName());
    }

    private static class R16VersionDetails extends VersionDetails {
        public R16VersionDetails(String version) {
            super(version);
        }

        @Override
        public boolean isModelSupported(Class<?> modelType) {
            return true;
        }

        @Override
        public boolean supportsConfiguringJavaHome() {
            return true;
        }

        @Override
        public boolean supportsConfiguringJvmArguments() {
            return true;
        }

        @Override
        public boolean supportsConfiguringStandardInput() {
            return true;
        }

        @Override
        public boolean supportsGradleProjectModel() {
            return true;
        }

        @Override
        public boolean supportsRunningTasksWhenBuildingModel() {
            return true;
        }

    }

    private static class DefaultModelIdentifier implements ModelIdentifier {
        private final String model;

        public DefaultModelIdentifier(String model) {
            this.model = model;
        }

        public String getName() {
            return model;
        }
    }
}
