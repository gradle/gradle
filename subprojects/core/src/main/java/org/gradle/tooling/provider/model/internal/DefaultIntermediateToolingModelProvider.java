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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildToolingModelController;
import org.gradle.internal.buildtree.IntermediateBuildActionRunner;
import org.gradle.internal.buildtree.ToolingModelRequestContext;
import org.gradle.internal.operations.MultipleBuildOperationFailures;
import org.gradle.internal.problems.failure.FailureFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

@NullMarked
public class DefaultIntermediateToolingModelProvider implements IntermediateToolingModelProvider {

    private final IntermediateBuildActionRunner actionRunner;
    private final ToolingModelParameterCarrier.Factory parameterCarrierFactory;
    private final ToolingModelProjectDependencyListener projectDependencyListener;
    private final FailureFactory failureFactory;

    public DefaultIntermediateToolingModelProvider(
        IntermediateBuildActionRunner actionRunner,
        ToolingModelParameterCarrier.Factory parameterCarrierFactory,
        ToolingModelProjectDependencyListener projectDependencyListener,
        FailureFactory failureFactory
    ) {
        this.actionRunner = actionRunner;
        this.parameterCarrierFactory = parameterCarrierFactory;
        this.projectDependencyListener = projectDependencyListener;
        this.failureFactory = failureFactory;
    }

    @Override
    public <T> List<T> getModels(ProjectState requester, List<ProjectState> targets, String modelName, Class<T> modelType, @Nullable Object parameter) {
        ToolingModelRequestContext context = new ToolingModelRequestContext(modelName, parameter, false);
        return unwrapOrRethrow(fetchResults(requester, targets, context), modelType);
    }

    @Override
    public List<ToolingModelBuilderResultInternal> getModelsAllowingFailures(ProjectState requester, List<ProjectState> targets, Class<?> modelType, @Nullable Object parameter) {
        ToolingModelRequestContext context = new ToolingModelRequestContext(modelType.getName(), parameter, true);
        return fetchResults(requester, targets, context);
    }

    @Override
    public <P extends Plugin<Project>> void applyPlugin(ProjectState requester, List<ProjectState> targets, Class<P> pluginClass) {
        getModels(requester, targets, PluginApplyingBuilder.MODEL_NAME, Boolean.class, createPluginApplyingParameter(pluginClass));
    }

    private static <P extends Plugin<Project>> PluginApplyingParameter createPluginApplyingParameter(Class<P> pluginClass) {
        return () -> pluginClass;
    }

    private List<ToolingModelBuilderResultInternal> fetchResults(ProjectState requester, List<ProjectState> targets, ToolingModelRequestContext context) {
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }
        reportToolingModelDependencies(requester, targets);
        BuildState buildState = extractSingleBuildState(targets);
        return buildState.withToolingModels(context.inResilientContext(), controller -> fanOut(controller, targets, context));
    }

    private List<ToolingModelBuilderResultInternal> fanOut(BuildToolingModelController controller, List<ProjectState> targets, ToolingModelRequestContext context) {
        ToolingModelParameterCarrier carrier = context.getParameter().map(parameterCarrierFactory::createCarrier).orElse(null);
        List<Supplier<ToolingModelBuilderResultInternal>> fetchActions = targets.stream()
            .map(target -> (Supplier<ToolingModelBuilderResultInternal>) () -> {
                try {
                    return controller.locateBuilderForTarget(target, context).getModel(context, carrier);
                } catch (Throwable t) {
                    // Safety net for an unexpected throw; the resilient controller normally
                    // captures configuration failures into the result wrapper itself.
                    return ToolingModelBuilderResultInternal.of(ImmutableList.of(failureFactory.create(t)));
                }
            })
            .collect(toList());

        return actionRunner.run(fetchActions);
    }

    private static <T> List<T> unwrapOrRethrow(List<ToolingModelBuilderResultInternal> results, Class<T> modelType) {
        List<T> typed = new ArrayList<>(results.size());
        List<Throwable> failures = new ArrayList<>();
        for (ToolingModelBuilderResultInternal result : results) {
            Object model = result.getModel();
            if (model == null) {
                if (result.getFailures().isEmpty()) {
                    failures.add(new IllegalStateException("Expected model of type " + modelType.getName() + " but found null"));
                }
            } else if (!modelType.isInstance(model)) {
                failures.add(new IllegalStateException("Expected model of type " + modelType.getName() + " but found " + model.getClass().getName()));
            } else {
                typed.add(modelType.cast(model));
            }
            result.getFailures().forEach(f -> failures.add(f.getOriginal()));
        }
        if (!failures.isEmpty()) {
            throw new MultipleBuildOperationFailures(failures, null);
        }
        return typed;
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

    private void reportToolingModelDependencies(ProjectState requester, List<ProjectState> targets) {
        for (ProjectState target : targets) {
            projectDependencyListener.onToolingModelDependency(requester, target);
        }
    }
}
