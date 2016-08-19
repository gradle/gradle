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

import com.google.common.collect.Lists;
import org.gradle.internal.Cast;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.connection.DefaultBuildIdentifier;
import org.gradle.tooling.internal.connection.DefaultProjectIdentifier;
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalModelResult;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.internal.protocol.InternalMultiModelAwareConnection;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.internal.Exceptions;

import java.util.List;

public class MultiModelAwareModelProducer extends HasCompatibilityMapping implements ModelProducer {
    private final ModelProducer delegate;
    private final ProtocolToModelAdapter adapter;
    private final VersionDetails versionDetails;
    private final ModelMapping modelMapping;
    private final InternalMultiModelAwareConnection connection;

    public MultiModelAwareModelProducer(ModelProducer delegate, ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, InternalMultiModelAwareConnection connection) {
        super(versionDetails);
        this.delegate = delegate;
        this.adapter = adapter;
        this.versionDetails = versionDetails;
        this.modelMapping = modelMapping;
        this.connection = connection;
    }

    @Override
    public <T> T produceModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        return delegate.produceModel(type, operationParameters);
    }

    @Override
    public <T> InternalModelResults<T> produceModels(final Class<T> elementType, ConsumerOperationParameters operationParameters) {
        BuildResult<?> result = buildModels(elementType, operationParameters);
        Object models = result.getModel();
        if (models instanceof InternalModelResults) {
            List<InternalModelResult<T>> results = Lists.newArrayList();
            for (InternalModelResult<?> original : (InternalModelResults<?>) models) {
                if (original.getFailure() == null) {
                    Object model = original.getModel();
                    if (original.getProjectPath() == null) {
                        DefaultBuildIdentifier buildIdentifier = new DefaultBuildIdentifier(original.getRootDir());
                        T newModel = applyCompatibilityMapping(adapter.builder(elementType), buildIdentifier).build(model);
                        results.add(InternalModelResult.model(original.getRootDir(), newModel));
                    } else {
                        DefaultProjectIdentifier projectIdentifier = new DefaultProjectIdentifier(original.getRootDir(), original.getProjectPath());
                        T newModel = applyCompatibilityMapping(adapter.builder(elementType), projectIdentifier).build(model);
                        results.add(InternalModelResult.model(original.getRootDir(), original.getProjectPath(), newModel));
                    }
                } else {
                    results.add(Cast.<InternalModelResult<T>>uncheckedCast(original));
                }
            }
            return new InternalModelResults<T>(results);
        }
        throw new UnsupportedOperationException(String.format("Produced result of type %s for model %s", result.getClass().getCanonicalName(), elementType.getName()));
    }

    private <T> BuildResult<?> buildModels(Class<T> type, ConsumerOperationParameters operationParameters) {
        if (!versionDetails.maySupportModel(type)) {
            throw Exceptions.unsupportedModel(type, versionDetails.getVersion());
        }
        final ModelIdentifier modelIdentifier = modelMapping.getModelIdentifierFromModelType(type);
        return connection.getModels(modelIdentifier, new BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters);
    }

}
