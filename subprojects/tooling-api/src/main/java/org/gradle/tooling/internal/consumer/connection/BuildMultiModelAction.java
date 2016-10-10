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
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.protocol.InternalModelResults;
import org.gradle.tooling.model.HasGradleProject;
import org.gradle.tooling.model.HierarchicalElement;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.File;

class BuildMultiModelAction<T> implements BuildAction<InternalModelResults<T>> {
    private final Class<T> elementType;
    private final File rootProject;

    BuildMultiModelAction(Class<T> elementType, ConsumerOperationParameters operationParameters) {
        this.elementType = elementType;
        this.rootProject = operationParameters.getProjectDir();
    }

    @Override
    public InternalModelResults<T> execute(BuildController buildController) {
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
            results.addBuildFailure(rootProject, e);
        }
        return results;
    }

    private void getBuildModel(BuildController buildController, InternalModelResults<T> results) {
        try {
            results.addBuildModel(rootProject, buildController.getModel(elementType));
        } catch (RuntimeException e) {
            results.addBuildFailure(rootProject, e);
        }
    }

    private void getModelsFromHierarchy(BuildController buildController, InternalModelResults<T> results) {
        try {
            T model = buildController.getModel(elementType);
            addModelsFromHierarchy(model, results);
        } catch (RuntimeException e) {
            results.addBuildFailure(rootProject, e);
        }
    }

    private void addModelsFromHierarchy(T model, InternalModelResults<T> results) {
        results.addProjectModel(rootProject, ((HasGradleProject) model).getGradleProject().getPath(), model);
        for (HierarchicalElement child : ((HierarchicalElement) model).getChildren()) {
            addModelsFromHierarchy(Cast.<T>uncheckedCast(child), results);
        }
    }

    private void getProjectModels(BuildController buildController, InternalModelResults<T> results) {
        GradleBuild gradleBuild = buildController.getBuildModel();
        for (BasicGradleProject project : gradleBuild.getProjects()) {
            try {
                results.addProjectModel(rootProject, project.getPath(), getProjectModel(buildController, project));
            } catch (RuntimeException e) {
                results.addProjectFailure(rootProject, project.getPath(), e);
            }
        }
    }

    private T getProjectModel(BuildController buildController, BasicGradleProject project) {
        return buildController.getModel(project, elementType);
    }
}
