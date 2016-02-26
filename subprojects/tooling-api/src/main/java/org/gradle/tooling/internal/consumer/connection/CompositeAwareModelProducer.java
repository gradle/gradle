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
import org.gradle.tooling.composite.ModelResult;
import org.gradle.tooling.composite.ProjectIdentity;
import org.gradle.tooling.composite.internal.DefaultBuildIdentity;
import org.gradle.tooling.composite.internal.DefaultModelResult;
import org.gradle.tooling.composite.internal.DefaultProjectIdentity;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.parameters.BuildCancellationTokenAdapter;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalCompositeAwareConnection;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class CompositeAwareModelProducer extends CancellableModelBuilderBackedModelProducer implements MultiModelProducer {
    private final InternalCompositeAwareConnection connection;

    public CompositeAwareModelProducer(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, InternalCompositeAwareConnection connection, Transformer<RuntimeException, RuntimeException> exceptionTransformer) {
        super(adapter, versionDetails, modelMapping, connection, exceptionTransformer);
        this.connection = connection;
    }

    @Override
    public <T> Iterable<ModelResult<T>> produceModels(Class<T> elementType, ConsumerOperationParameters operationParameters) {
        BuildResult<?> result = buildModels(elementType, operationParameters);
        Set<T> models = new LinkedHashSet<T>();
        if (result.getModel() instanceof Iterable) {
            adapter.convertCollection(models, elementType, Iterable.class.cast(result.getModel()), getCompatibilityMapperAction());
        }
        return transform(models);
    }

    private <T> Iterable<ModelResult<T>> transform(Set<T> results) {
        return CollectionUtils.collect(results, new Transformer<ModelResult<T>, T>() {
            @Override
            public ModelResult<T> transform(T t) {
                return new DefaultModelResult<T>(t, extractProjectIdentityHack(t));
            }
        });
    }

    private <T> ProjectIdentity extractProjectIdentityHack(T result) {
        if (result instanceof EclipseProject) {
            EclipseProject eclipseProject = (EclipseProject)result;
            EclipseProject rootProject = eclipseProject;
            while (rootProject.getParent()!=null) {
                rootProject = rootProject.getParent();
            }
            File rootDir = rootProject.getGradleProject().getProjectDirectory();
            String projectPath = eclipseProject.getGradleProject().getPath();
            return new DefaultProjectIdentity(new DefaultBuildIdentity(rootDir), rootDir, projectPath);
        }
        return null;
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
