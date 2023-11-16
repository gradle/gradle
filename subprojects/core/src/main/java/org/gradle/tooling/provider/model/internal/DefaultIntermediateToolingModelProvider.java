/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.provider.model.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.Cast;
import org.gradle.internal.build.BuildState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@NonNullApi
public class DefaultIntermediateToolingModelProvider implements IntermediateToolingModelProvider {

    @Override
    public <T> List<T> getModels(List<Project> targets, Class<T> modelType) {
        return getModelsImpl(targets, modelType, null);
    }

    @Override
    public <T> List<T> getModels(List<Project> targets, Class<T> modelType, Object modelBuilderParameter) {
        return getModelsImpl(targets, modelType, modelBuilderParameter);
    }

    private static <T> List<T> getModelsImpl(List<Project> targets, Class<T> modelType, @Nullable Object modelBuilderParameter) {
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }

        String modelName = modelType.getName();
        List<Object> rawModels = getModels(targets, modelName, modelBuilderParameter);
        return ensureModelTypes(modelType, rawModels);
    }

    private static List<Object> getModels(List<Project> targets, String modelName, @Nullable Object modelBuilderParameter) {
        BuildState buildState = extractSingleBuildState(targets);
        Function<Class<?>, Object> parameterFactory = modelBuilderParameter == null ? null : createParameterFactory(modelBuilderParameter);
        return buildState.withToolingModels(controller -> {
            ArrayList<Object> models = new ArrayList<>();
            for (Project targetProject : targets) {
                ProjectState builderTarget = ((ProjectInternal) targetProject).getOwner();
                ToolingModelScope toolingModelScope = controller.locateBuilderForTarget(builderTarget, modelName, parameterFactory != null);
                Object model = toolingModelScope.getModel(modelName, parameterFactory);
                models.add(model);
            }
            return models;
        });
    }

    private static Function<Class<?>, Object> createParameterFactory(Object modelBuilderParameter) {
        return expectedParameterType -> {
            if (expectedParameterType.isInstance(modelBuilderParameter)) {
                return modelBuilderParameter;
            } else {
                throw new IllegalStateException(String.format("Expected model builder parameter type '%s', got '%s'", expectedParameterType.getName(), modelBuilderParameter.getClass().getName()));
            }
        };
    }

    private static BuildState extractSingleBuildState(List<Project> targets) {
        if (targets.isEmpty()) {
            throw new IllegalStateException("Cannot find build state without target projects");
        }

        BuildState result = getBuildState(targets.get(0));

        for (Project target : targets) {
            BuildState projectBuildState = getBuildState(target);
            if (result != projectBuildState) {
                throw new IllegalArgumentException(
                    String.format("Expected target projects to share the same build state. Found at least two: '%s' and '%s'",
                        result.getDisplayName(), projectBuildState.getDisplayName())
                );
            }
        }

        return result;
    }

    private static BuildState getBuildState(Project target) {
        return ((ProjectInternal) target).getOwner().getOwner();
    }

    private static <T> List<T> ensureModelTypes(Class<T> implementationType, List<Object> rawModels) {
        for (Object rawModel : rawModels) {
            if (rawModel == null) {
                throw new IllegalStateException(String.format("Expected model of type %s but found null", implementationType.getName()));
            }
            if (!implementationType.isInstance(rawModel)) {
                throw new IllegalStateException(String.format("Expected model of type %s but found %s", implementationType.getName(), rawModel.getClass().getName()));
            }
        }

        return Cast.uncheckedCast(rawModels);
    }
}
