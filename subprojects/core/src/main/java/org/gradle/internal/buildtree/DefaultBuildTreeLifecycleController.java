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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
            // Failures that resilient model building defers behind partial results are reported here as they happen.
            List<DeferredBuildFailure> deferredFailures = Collections.synchronizedList(new ArrayList<>());
            modelCreator.beforeTasks(action, deferredFailures::add);
            ExecutionResult<Void> taskRunResult = runTasks ? runTasks() : ExecutionResult.succeeded();
            // Allow the model action to run even if tasks failed
            ExecutionResult<T> modelResult = runFromBuildModel(action, deferredFailures::add);
            ExecutionResult<T> workResult = modelResult.withFailures(taskRunResult);
            // The deferred failures must still fail the build.
            return failBuildWith(workResult, deferredFailures);
        });
    }

    private static <T> ExecutionResult<T> failBuildWith(ExecutionResult<T> workResult, List<DeferredBuildFailure> deferredFailures) {
        // A model builder failure is only ever observed here, so it always fails the build.
        List<Throwable> modelBuilderFailures = deferredFailures.stream()
            .filter(failure -> !failure.isConfigurationFailure())
            .map(DeferredBuildFailure::getFailure)
            .collect(Collectors.toList());
        ExecutionResult<T> result = workResult.withFailures(ExecutionResult.maybeFailed(modelBuilderFailures));

        // A configuration failure is normally reported by the requested work. Surface the ones resilient model
        // building swallowed (e.g. an IDE sync that runs no tasks) only when the build is not already failing, to
        // avoid reporting it twice. The same failure is observed once per queried project, so de-duplicate by identity.
        if (workResult.getFailures().isEmpty()) {
            List<Throwable> configurationFailures = deferredFailures.stream()
                .filter(DeferredBuildFailure::isConfigurationFailure)
                .map(DeferredBuildFailure::getFailure)
                .distinct()
                .collect(Collectors.toList());
            result = result.withFailures(ExecutionResult.maybeFailed(configurationFailures));
        }
        return result;
    }

    @SuppressWarnings("DataFlowIssue")
    private <T> ExecutionResult<T> runFromBuildModel(BuildTreeModelAction<? extends T> action, Consumer<DeferredBuildFailure> deferredFailureListener) {
        Try<T> model = Try.ofFailable(() -> modelCreator.fromBuildModel(action, deferredFailureListener));
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
