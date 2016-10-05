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
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.model.HasGradleProject;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.internal.Exceptions;

abstract class MultiModelFromSingleModelProducer extends HasCompatibilityMapping implements ModelProducer {
    private final VersionDetails versionDetails;

    public MultiModelFromSingleModelProducer(VersionDetails versionDetails) {
        super(versionDetails);
        this.versionDetails = versionDetails;
    }

    @Override
    public <T> InternalModelResults<T> produceModels(Class<T> elementType, ConsumerOperationParameters operationParameters) {
        if (!versionDetails.maySupportModel(elementType)) {
            InternalModelResults<T> results = new InternalModelResults<T>();
            results.addBuildFailure(operationParameters.getProjectDir(), Exceptions.unsupportedModel(elementType, versionDetails.getVersion()));
            return results;
        }
        InternalModelResults<T> results = new InternalModelResults<T>();
        if (HierarchicalElement.class.isAssignableFrom(elementType) && HasGradleProject.class.isAssignableFrom(elementType)) {
            getModelsFromHierarchy(elementType, operationParameters, results);
        } else {
            getBuildModel(elementType, operationParameters, results);
        }
        return results;
    }

    protected <T> void getBuildModel(Class<T> elementType, ConsumerOperationParameters operationParameters, InternalModelResults<T> results) {
        try {
            T result = produceModel(elementType, operationParameters);
            results.addBuildModel(operationParameters.getProjectDir(), result);
        } catch (RuntimeException e) {
            results.addBuildFailure(operationParameters.getProjectDir(), e);
        }
    }

    private <T> void getModelsFromHierarchy(Class<T> elementType, ConsumerOperationParameters operationParameters, InternalModelResults<T> results) {
        try {
            T result = produceModel(elementType, operationParameters);
            addModelsFromHierarchy(result, operationParameters, results);
        } catch (RuntimeException e) {
            results.addBuildFailure(operationParameters.getProjectDir(), e);
        }
    }

    private <T> void addModelsFromHierarchy(T model, ConsumerOperationParameters operationParameters, InternalModelResults<T> results) {
        results.addProjectModel(operationParameters.getProjectDir(), ((HasGradleProject) model).getGradleProject().getPath(), model);
        for (HierarchicalElement child : ((HierarchicalElement) model).getChildren()) {
            addModelsFromHierarchy(Cast.<T>uncheckedCast(child), operationParameters, results);
        }
    }
}
