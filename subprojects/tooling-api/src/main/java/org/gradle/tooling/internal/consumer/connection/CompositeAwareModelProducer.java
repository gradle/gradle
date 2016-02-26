/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalCompositeAwareConnection;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.internal.Exceptions;

import java.util.LinkedHashSet;
import java.util.Set;

public class CompositeAwareModelProducer extends CancellableModelBuilderBackedModelProducer implements MultiModelProducer {
    private final InternalCompositeAwareConnection connection;

    public CompositeAwareModelProducer(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, InternalCompositeAwareConnection connection, Transformer<RuntimeException, RuntimeException> exceptionTransformer) {
        super(adapter, versionDetails, modelMapping, connection, exceptionTransformer);
        this.connection = connection;
    }

    @Override
    public <T> Set<T> produceModels(Class<T> elementType, ConsumerOperationParameters operationParameters) {
        BuildResult<?> result = buildModels(elementType, operationParameters);
        Set<T> models = new LinkedHashSet<T>();
        if (result.getModel() instanceof Iterable) {
            adapter.convertCollection(models, elementType, Iterable.class.cast(result.getModel()), getCompatibilityMapperAction());
        }
        return models;
    }

    private <T> BuildResult<?> buildModels(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (!versionDetails.maySupportModel(type)) {
            throw Exceptions.unsupportedModel(type, versionDetails.getVersion());
        }
        final ModelIdentifier modelIdentifier = modelMapping.getModelIdentifierFromModelType(type);
        BuildResult<?> result;
        try {
            result = connection.getModels(modelIdentifier, new BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters);
        } catch (InternalUnsupportedModelException e) {
            throw Exceptions.unknownModel(type, e);
        } catch (RuntimeException e) {
            throw exceptionTransformer.transform(e);
        }
        return result;
    }

}
