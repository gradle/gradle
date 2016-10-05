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

import org.gradle.internal.Cast;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.model.internal.DefaultBuildIdentifier;
import org.gradle.tooling.model.internal.DefaultProjectIdentifier;
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
        BuildResult<?> buildResult = buildModels(elementType, operationParameters);
        if (!versionDetails.maySupportModel(elementType)) {
            InternalModelResults<T> results = new InternalModelResults<T>();
            results.addBuildFailure(operationParameters.getProjectDir(), Exceptions.unsupportedModel(elementType, versionDetails.getVersion()));
            return results;
        }
        Object results = buildResult.getModel();
        if (results instanceof InternalModelResults) {
            for (InternalModelResult<Object> result : Cast.<InternalModelResults<Object>>uncheckedCast(results)) {
                if (result.getFailure() == null) {
                    Object model = result.getModel();
                    if (result.getProjectPath() == null) {
                        DefaultBuildIdentifier buildIdentifier = new DefaultBuildIdentifier(result.getRootDir());
                        T newModel = applyCompatibilityMapping(adapter.builder(elementType), buildIdentifier).build(model);
                        result.setModel(newModel);
                    } else {
                        DefaultProjectIdentifier projectIdentifier = new DefaultProjectIdentifier(result.getRootDir(), result.getProjectPath());
                        T newModel = applyCompatibilityMapping(adapter.builder(elementType), projectIdentifier).build(model);
                        result.setModel(newModel);
                    }
                }
            }
            return Cast.uncheckedCast(results);
        }
        throw new UnsupportedOperationException(String.format("Produced result of type %s for model %s", buildResult.getClass().getCanonicalName(), elementType.getName()));
    }

    private <T> BuildResult<?> buildModels(Class<T> type, ConsumerOperationParameters operationParameters) {
        final ModelIdentifier modelIdentifier = modelMapping.getModelIdentifierFromModelType(type);
        return connection.getModels(modelIdentifier, new BuildCancellationTokenAdapter(operationParameters.getCancellationToken()), operationParameters);
    }

}
