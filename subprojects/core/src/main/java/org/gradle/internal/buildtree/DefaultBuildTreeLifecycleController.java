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

import java.util.List;
import java.util.concurrent.Callable;
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
            ResilientBuildTreeFailureCollector failureCollector = new ResilientBuildTreeFailureCollector();

            // Run before tasks (the projectsLoaded phase). If it fails, skip tasks and the model action, but still
            // fail the build with the failures collected so far.
            ExecutionResult<Void> beforeTasksResult = runModelAction(() -> {
                modelCreator.beforeTasks(action, failureCollector);
                return null;
            });
            if (!beforeTasksResult.getFailures().isEmpty()) {
                ExecutionResult<T> workResult = beforeTasksResult.asFailure();
                return attachCollectedFailures(workResult, failureCollector);
            }

            // Run tasks
            ExecutionResult<Void> taskRunResult = runTasks ? runTasks() : ExecutionResult.succeeded();

            // Allow the model action to run even if tasks failed
            ExecutionResult<T> buildActionResult = runModelAction(() -> modelCreator.fromBuildModel(action, failureCollector));
            ExecutionResult<T> workResult = buildActionResult.withFailures(taskRunResult);

            // The held failures must still fail the build.
            return attachCollectedFailures(workResult, failureCollector);
        });
    }

    private static <T> ExecutionResult<T> attachCollectedFailures(ExecutionResult<T> workResult, ResilientBuildTreeFailureCollector failures) {
        // A model builder failure is only ever observed here, so it always fails the build.
        ExecutionResult<T> result = workResult.withFailures(ExecutionResult.maybeFailed(failures.getModelBuilderFailures()));

        // Only surface swallowed configuration failures when the build isn't already failing - otherwise the work
        // reports them - and de-duplicate by identity, since the same failure is seen once per queried project.
        if (workResult.getFailures().isEmpty()) {
            List<Throwable> configurationFailures = failures.getConfigurationFailures().stream()
                .distinct()
                .collect(Collectors.toList());
            result = result.withFailures(ExecutionResult.maybeFailed(configurationFailures));
        }
        return result;
    }

    /**
     * Runs a phase of the model action (beforeTasks or fromBuildModel), capturing a failure as a build action failure
     * instead of throwing, so the caller can still fold in any failures collected before it.
     */
    @SuppressWarnings("DataFlowIssue")
    private <T> ExecutionResult<T> runModelAction(Callable<T> phase) {
        Try<T> result = Try.ofFailable(phase);
        return result.getFailure().isPresent()
            ? ExecutionResult.failed(BuildActionExecutionException.wrap(result.getFailure().get()))
            : ExecutionResult.succeeded(result.get());
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
