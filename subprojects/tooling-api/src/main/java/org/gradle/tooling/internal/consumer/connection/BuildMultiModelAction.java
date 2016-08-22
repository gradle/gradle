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
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.InternalBuildAction;
import org.gradle.tooling.internal.protocol.InternalBuildController;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.HasGradleProject;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

class BuildMultiModelAction<T> extends HasCompatibilityMapping implements InternalBuildAction<InternalModelResults<T>> {
    private final ProtocolToModelAdapter adapter;
    private final ModelMapping modelMapping;
    private final Class<T> elementType;
    private final ConsumerOperationParameters operationParameters;
    private final ModelIdentifier modelIdentifier;

    BuildMultiModelAction(ProtocolToModelAdapter adapter, VersionDetails versionDetails, ModelMapping modelMapping, Class<T> elementType, ConsumerOperationParameters operationParameters) {
        super(versionDetails);
        this.adapter = adapter;
        this.modelMapping = modelMapping;
        this.elementType = elementType;
        this.operationParameters = operationParameters;
        this.modelIdentifier = modelMapping.getModelIdentifierFromModelType(elementType);
    }

    @Override
    public InternalModelResults<T> execute(InternalBuildController buildController) {
        InternalModelResults<T> results = new InternalModelResults<T>();
        try {
            if (ProjectModel.class.isAssignableFrom(elementType)) {
                getProjectModels(buildController, results);
            } else if (HierarchicalElement.class.isAssignableFrom(elementType) && HasGradleProject.class.isAssignableFrom(elementType)) {
                getModelsFromHierarchy(buildController, results);
            } else {
                getBuildModel(buildController, results);
            }
        } catch (RuntimeException e) {
            results.addBuildFailure(operationParameters.getProjectDir(), e);
        }
        return results;
    }

    private void getBuildModel(InternalBuildController buildController, InternalModelResults<T> results) {
        try {
            BuildResult<T> model = Cast.uncheckedCast(buildController.getModel(null, modelIdentifier));
            results.addBuildModel(operationParameters.getProjectDir(), model.getModel());
        } catch (RuntimeException e) {
            results.addBuildFailure(operationParameters.getProjectDir(), e);
        }
    }

    private void getModelsFromHierarchy(InternalBuildController buildController, InternalModelResults<T> results) {
        try {
            T model = Cast.uncheckedCast(buildController.getModel(null, modelIdentifier).getModel());
            addModelsFromHierarchy(model, results);
        } catch (RuntimeException e) {
            results.addBuildFailure(operationParameters.getProjectDir(), e);
        }
    }

    private void addModelsFromHierarchy(T model, InternalModelResults<T> results) {
        results.addProjectModel(operationParameters.getProjectDir(), ((HasGradleProject) model).getGradleProject().getPath(), model);
        for (HierarchicalElement child : ((HierarchicalElement) model).getChildren()) {
            addModelsFromHierarchy(Cast.<T>uncheckedCast(child), results);
        }
    }

    private void getProjectModels(InternalBuildController buildController, InternalModelResults<T> results) {
        BuildResult<GradleBuild> gradleBuild = Cast.uncheckedCast(buildController.getModel(null, modelMapping.getModelIdentifierFromModelType(GradleBuild.class)));
        for (BasicGradleProject project : gradleBuild.getModel().getProjects()) {
            try {
                results.addProjectModel(operationParameters.getProjectDir(), project.getPath(), getProjectModel(buildController, project));
            } catch (RuntimeException e) {
                results.addProjectFailure(operationParameters.getProjectDir(), project.getPath(), e);
            }
        }
    }

    private T getProjectModel(InternalBuildController buildController, BasicGradleProject project) {
        BuildResult<T> result = Cast.uncheckedCast(buildController.getModel(project, modelIdentifier));
        return applyCompatibilityMapping(adapter.builder(elementType), operationParameters.getBuildIdentifier()).build(result.getModel());
    }
}
