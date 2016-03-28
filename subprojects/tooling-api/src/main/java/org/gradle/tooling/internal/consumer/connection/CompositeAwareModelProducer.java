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
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.connection.ModelResult;
import org.gradle.tooling.model.ProjectIdentity;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.connection.DefaultFailedModelResult;
import org.gradle.tooling.internal.connection.DefaultModelResult;
import org.gradle.tooling.internal.consumer.ExceptionTransformer;
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalCompositeAwareConnection;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.internal.Exceptions;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CompositeAwareModelProducer extends HasCompatibilityMapperAction implements MultiModelProducer {
    private final ModelProducer delegate;
    private final ProtocolToModelAdapter adapter;
    private final VersionDetails versionDetails;
    private final ModelMapping modelMapping;
    private final InternalCompositeAwareConnection connection;
    private final Transformer<RuntimeException, RuntimeException> exceptionTransformer;

    public CompositeAwareModelProducer(ModelProducer delegate, ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, InternalCompositeAwareConnection connection, Transformer<RuntimeException, RuntimeException> exceptionTransformer) {
        super(versionDetails);
        this.delegate = delegate;
        this.adapter = adapter;
        this.versionDetails = versionDetails;
        this.modelMapping = modelMapping;
        this.connection = connection;
        this.exceptionTransformer = exceptionTransformer;
    }

    @Override
    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        return delegate.produceModel(type, operationParameters);
    }

    @Override
    public <T> Iterable<ModelResult<T>> produceModels(final Class<T> elementType, ConsumerOperationParameters operationParameters) {
        BuildResult<?> result = buildModels(elementType, operationParameters);
        if (result.getModel() instanceof Map) {
            ExceptionTransformer exceptionTransformer = new ExceptionTransformer(new Transformer<String, Throwable>() {
                @Override
                public String transform(Throwable throwable) {
                    // TODO:DAZ The installation in this message isn't going to be right
                    return String.format("Could not fetch models of type '%s' using Gradle daemon composite connection.", elementType.getSimpleName());
                }
            });
            final List<ModelResult<T>> models = new LinkedList<ModelResult<T>>();
            Map<Object, Object> resultMap = (Map) result.getModel();
            for (Map.Entry<Object, Object> entry : resultMap.entrySet()) {
                ProjectIdentity projectIdentity = adapter.adapt(ProjectIdentity.class, entry.getKey());
                if (entry.getValue() instanceof Throwable) {
                    GradleConnectionException failure = exceptionTransformer.transform((Throwable) entry.getValue());
                    models.add(new DefaultFailedModelResult<T>(projectIdentity, failure));
                } else {
                    T modelResult = adapter.adapt(elementType, entry.getValue(), getCompatibilityMapperAction(projectIdentity));
                    models.add(new DefaultModelResult<T>(modelResult));
                }
            }
            return models;
        }
        // TODO: Adapt other types?
        throw new UnsupportedOperationException(String.format("Produced result of type %s for model %s", result.getModel().getClass().getCanonicalName(), elementType.getCanonicalName()));
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
