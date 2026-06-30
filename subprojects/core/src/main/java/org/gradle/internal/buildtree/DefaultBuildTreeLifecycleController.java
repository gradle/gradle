/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.buildtree;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.GradleException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.internal.Describables;
import org.gradle.internal.Try;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.buildtree.BuildTreeWorkController.TaskRunResult;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.WorkTypeAware;
import org.gradle.internal.model.StateTransitionController;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultBuildTreeLifecycleController implements BuildTreeLifecycleController {
    private enum State implements StateTransitionController.State {
        NotStarted, Complete
    }

    private final BuildLifecycleController buildLifecycleController;
    private final BuildTreeWorkController workController;
    private final BuildTreeModelCreator modelCreator;
    private final BuildTreeFinishExecutor finishExecutor;
    private final StateTransitionController<State> state;

    public DefaultBuildTreeLifecycleController(
        BuildLifecycleController buildLifecycleController,
        BuildTreeWorkController workController,
        BuildTreeModelCreator modelCreator,
        BuildTreeFinishExecutor finishExecutor,
        StateTransitionControllerFactory controllerFactory
    ) {
        this.buildLifecycleController = buildLifecycleController;
        this.workController = workController;
        this.modelCreator = modelCreator;
        this.finishExecutor = finishExecutor;
        this.state = controllerFactory.newController(Describables.of("build tree state"), State.NotStarted);
    }

    @Override
    public void beforeBuild(Consumer<? super GradleInternal> action) {
        state.inState(State.NotStarted, () -> action.accept(buildLifecycleController.getGradle()));
    }

    @Override
    public void scheduleAndRunTasks() {
        scheduleAndRunTasks(null);
    }

    @Override
    public void scheduleAndRunTasks(@Nullable EntryTaskSelector selector) {
        runBuild(() -> workController.scheduleAndRunRequestedTasks(selector).getExecutionResultOrThrow());
    }

    @Override
    public <T> T fromBuildModel(boolean runTasks, BuildTreeModelAction<? extends T> action) {
        return runBuild(() -> {
            modelCreator.beforeTasks(action);
            ExecutionResult<Void> taskRunResult = ExecutionResult.succeeded();
            if (runTasks) {
                taskRunResult = runTasks();
            }
            // Allow model action to run even if tasks failed
            ExecutionResult<T> modelResult = runFromBuildModel(action);

            // Failures captured during resilient model building travel back with the model results. Fold them onto
            // the execution result so they reach finishBuildTree through the normal failure channel. The "build
            // already failing" check must use only the work/action failures, before the model builder failures below.
            boolean buildAlreadyFailing = !taskRunResult.getFailures().isEmpty() || !modelResult.getFailures().isEmpty();
            ResilientModelBuildingFailures resilientFailures = modelCreator.drainModelBuildingFailures();
            modelResult = modelResult.withFailures(taskRunResult);
            // Model builder failures are always propagated.
            modelResult = modelResult.withFailures(ExecutionResult.maybeFailed(resilientFailures.getModelBuilderFailures()));
            // Configuration failures are normally swallowed when no tasks run; propagate them unless the build is
            // already failing because of the same configuration failure (e.g. tasks were requested and configuration failed).
            if (!buildAlreadyFailing) {
                modelResult = modelResult.withFailures(ExecutionResult.maybeFailed(resilientFailures.getConfigurationFailures()));
            }
            return modelResult;
        });
    }

    @SuppressWarnings("DataFlowIssue")
    private <T> ExecutionResult<T> runFromBuildModel(BuildTreeModelAction<? extends T> action) {
        Try<T> model = Try.ofFailable(() -> modelCreator.fromBuildModel(action));
        return model.getFailure().isPresent()
            ? ExecutionResult.failed(BuildActionExecutionException.wrap(model.getFailure().get()))
            : ExecutionResult.succeeded(model.get());
    }

    private ExecutionResult<Void> runTasks() {
        TaskRunResult result = workController.scheduleAndRunRequestedTasks(null);
        if (!result.getScheduleResult().isSuccessful()) {
            return result.getScheduleResult();
        }
        return result.getExecutionResultOrThrow();
    }

    @Override
    public <T> T withEmptyBuild(Function<? super SettingsInternal, T> action) {
        return runBuild(() -> {
            T result = buildLifecycleController.withSettings(action);
            return ExecutionResult.succeeded(result);
        });
    }

    private <T> T runBuild(Supplier<ExecutionResult<? extends T>> action) {
        return state.transition(State.NotStarted, State.Complete, () -> {
            ExecutionResult<? extends T> result;
            try {
                result = action.get();
            } catch (Throwable t) {
                result = ExecutionResult.failed(t);
            }

            RuntimeException finalReportableFailure = finishExecutor.finishBuildTree(result.getFailures());
            if (finalReportableFailure != null) {
                throw finalReportableFailure;
            }

            return result.getValue();
        });
    }

    @NullMarked
    @Contextual
    @VisibleForTesting
    static class BuildActionExecutionException extends GradleException implements WorkTypeAware {

        private BuildActionExecutionException(Throwable cause) {
            super(cause.getMessage(), cause);
        }

        @Override
        public String getWorkType() {
            return "BuildAction";
        }

        public static BuildActionExecutionException wrap(Throwable throwable) {
            return new BuildActionExecutionException(throwable);
        }
    }
}
