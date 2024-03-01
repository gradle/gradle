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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.Cast;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildToolingModelController;
import org.gradle.internal.buildtree.IntermediateBuildActionRunner;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

@NonNullApi
public class DefaultIntermediateToolingModelProvider implements IntermediateToolingModelProvider {

    private final IntermediateBuildActionRunner actionRunner;
    private final ToolingModelParameterCarrier.Factory parameterCarrierFactory;
    private final ToolingModelProjectDependencyListener projectDependencyListener;

    public DefaultIntermediateToolingModelProvider(
        IntermediateBuildActionRunner actionRunner,
        ToolingModelParameterCarrier.Factory parameterCarrierFactory,
        ToolingModelProjectDependencyListener projectDependencyListener
    ) {
        this.actionRunner = actionRunner;
        this.parameterCarrierFactory = parameterCarrierFactory;
        this.projectDependencyListener = projectDependencyListener;
    }

    @Override
    public <T> List<T> getModels(Project requester, List<Project> targets, String modelName, Class<T> modelType, @Nullable Object parameter) {
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }


        List<Object> rawModels = fetchModels(requester, targets, modelName, parameter);
        return ensureModelTypes(modelType, rawModels);
    }

    @Override
    public <P extends Plugin<Project>> void applyPlugin(Project requester, List<Project> targets, Class<P> pluginClass) {
        List<Object> rawModels = fetchModels(requester, targets, PluginApplyingBuilder.MODEL_NAME, createPluginApplyingParameter(pluginClass));
        ensureModelTypes(Boolean.class, rawModels);
    }

    private static <P extends Plugin<Project>> PluginApplyingParameter createPluginApplyingParameter(Class<P> pluginClass) {
        return () -> pluginClass;
    }

    private List<Object> fetchModels(Project requester, List<Project> targets, String modelName, @Nullable Object parameter) {
        reportToolingModelDependencies((ProjectInternal) requester, targets);
        BuildState buildState = extractSingleBuildState(targets);
        ToolingModelParameterCarrier carrier = parameter == null ? null : parameterCarrierFactory.createCarrier(parameter);
        return buildState.withToolingModels(controller -> getModels(controller, targets, modelName, carrier));
    }

    private List<Object> getModels(BuildToolingModelController controller, List<Project> targets, String modelName, @Nullable ToolingModelParameterCarrier parameter) {
        List<Supplier<Object>> fetchActions = targets.stream()
            .map(targetProject -> (Supplier<Object>) () -> fetchModel(modelName, controller, (ProjectInternal) targetProject, parameter))
            .collect(toList());

        return runFetchActions(fetchActions);
    }

    @Nullable
    private static Object fetchModel(String modelName, BuildToolingModelController controller, ProjectInternal targetProject, @Nullable ToolingModelParameterCarrier parameter) {
        ProjectState builderTarget = targetProject.getOwner();
        ToolingModelScope toolingModelScope = controller.locateBuilderForTarget(builderTarget, modelName, parameter != null);
        return toolingModelScope.getModel(modelName, parameter);
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

    private <T> List<T> runFetchActions(List<Supplier<T>> actions) {
        return actionRunner.run(actions);
    }

    private void reportToolingModelDependencies(ProjectInternal requester, List<Project> targets) {
        for (Project target : targets) {
            projectDependencyListener.onToolingModelDependency(requester.getOwner(), ((ProjectInternal) target).getOwner());
        }
    }
}
