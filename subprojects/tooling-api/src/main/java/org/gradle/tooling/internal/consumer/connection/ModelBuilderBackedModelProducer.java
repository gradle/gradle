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
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelBuilder;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.internal.Exceptions;

public class ModelBuilderBackedModelProducer extends HasCompatibilityMapping implements ModelProducer {
    private final ProtocolToModelAdapter adapter;
    private final VersionDetails versionDetails;
    private final ModelMapping modelMapping;
    private final ModelBuilder builder;

    public ModelBuilderBackedModelProducer(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, ModelBuilder builder) {
        this.adapter = adapter;
        this.versionDetails = versionDetails;
        this.modelMapping = modelMapping;
        this.builder = builder;
    }

    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (!versionDetails.maySupportModel(type)) {
            throw Exceptions.unsupportedModel(type, versionDetails.getVersion());
        }
        final ModelIdentifier modelIdentifier = modelMapping.getModelIdentifierFromModelType(type);
        BuildResult<?> result;
        try {
            result = builder.getModel(modelIdentifier, operationParameters);
        } catch (InternalUnsupportedModelException e) {
            throw Exceptions.unknownModel(type, e);
        }
        return applyCompatibilityMapping(adapter.builder(type), operationParameters).build(result.getModel());
    }
}
