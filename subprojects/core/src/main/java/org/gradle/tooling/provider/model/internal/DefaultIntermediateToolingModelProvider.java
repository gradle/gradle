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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.Cast;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildToolingModelController;
import org.gradle.internal.buildtree.IntermediateBuildActionRunner;
import org.gradle.internal.buildtree.ToolingModelRequestContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

@NullMarked
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
    public <T> List<T> getModels(ProjectState requester, List<ProjectState> targets, String modelName, Class<T> modelType, @Nullable Object parameter) {
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> rawModels = fetchModels(requester, targets, modelName, parameter);
        return ensureModelTypes(modelType, rawModels);
    }

    @Override
    public <P extends Plugin<Project>> void applyPlugin(ProjectState requester, List<ProjectState> targets, Class<P> pluginClass) {
        List<Object> rawModels = fetchModels(requester, targets, PluginApplyingBuilder.MODEL_NAME, createPluginApplyingParameter(pluginClass));
        ensureModelTypes(Boolean.class, rawModels);
    }

    private static <P extends Plugin<Project>> PluginApplyingParameter createPluginApplyingParameter(Class<P> pluginClass) {
        return () -> pluginClass;
    }

    private List<Object> fetchModels(ProjectState requester, List<ProjectState> targets, String modelName, @Nullable Object parameter) {
        reportToolingModelDependencies(requester, targets);
        BuildState buildState = extractSingleBuildState(targets);
        ToolingModelParameterCarrier carrier = parameter == null ? null : parameterCarrierFactory.createCarrier(parameter);
        return buildState.withToolingModels(false, controller -> getModels(controller, targets, modelName, carrier));
    }

    private List<Object> getModels(BuildToolingModelController controller, List<ProjectState> targets, String modelName, @Nullable ToolingModelParameterCarrier parameter) {
        List<Supplier<Object>> fetchActions = targets.stream()
            .map(targetProject -> (Supplier<Object>) () -> fetchModel(modelName, controller, targetProject, parameter))
            .collect(toList());

        return runFetchActions(fetchActions);
    }

    @Nullable
    private static Object fetchModel(String modelName, BuildToolingModelController controller, ProjectState builderTarget, @Nullable ToolingModelParameterCarrier parameter) {
        ToolingModelScope toolingModelScope = controller.locateBuilderForTarget(builderTarget, new ToolingModelRequestContext(modelName, parameter, false));
        return toolingModelScope.getModel(modelName, parameter);
    }

    private static BuildState extractSingleBuildState(List<ProjectState> targets) {
        if (targets.isEmpty()) {
            throw new IllegalStateException("Cannot find build state without target projects");
        }

        BuildState result = targets.get(0).getOwner();

        for (ProjectState target : targets) {
            if (result != target.getOwner()) {
                throw new IllegalArgumentException(
                    String.format("Expected target projects to share the same build state. Found at least two: '%s' and '%s'",
                        result.getDisplayName(), target.getOwner().getDisplayName())
                );
            }
        }

        return result;
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

    private void reportToolingModelDependencies(ProjectState requester, List<ProjectState> targets) {
        for (ProjectState target : targets) {
            projectDependencyListener.onToolingModelDependency(requester, target);
        }
    }
}
