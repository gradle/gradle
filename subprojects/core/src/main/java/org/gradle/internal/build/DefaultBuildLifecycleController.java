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
package org.gradle.internal.build;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.initialization.BuildCompletionListener;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.scopes.BuildScopeServices;

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DefaultBuildLifecycleController implements BuildLifecycleController {
    private enum State {
        Created, Configure, TaskGraph, Finished;

        String getDisplayName() {
            if (TaskGraph == this) {
                return "Build";
            } else {
                return "Configure";
            }
        }
    }

    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final BuildCompletionListener buildCompletionListener;
    private final InternalBuildFinishedListener buildFinishedListener;
    private final BuildWorkPreparer workPreparer;
    private final BuildWorkExecutor workExecutor;
    private final BuildScopeServices buildServices;
    private final GradleInternal gradle;
    private final BuildModelController modelController;
    private State state = State.Created;
    @Nullable
    private ExecutionResult<?> stageFailures;

    public DefaultBuildLifecycleController(
        GradleInternal gradle,
        BuildModelController buildModelController,
        ExceptionAnalyser exceptionAnalyser,
        BuildListener buildListener,
        BuildCompletionListener buildCompletionListener,
        InternalBuildFinishedListener buildFinishedListener,
        BuildWorkPreparer workPreparer,
        BuildWorkExecutor workExecutor,
        BuildScopeServices buildServices
    ) {
        this.gradle = gradle;
        this.modelController = buildModelController;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.workPreparer = workPreparer;
        this.workExecutor = workExecutor;
        this.buildCompletionListener = buildCompletionListener;
        this.buildFinishedListener = buildFinishedListener;
        this.buildServices = buildServices;
    }

    @Override
    public GradleInternal getGradle() {
        if (state == State.Finished) {
            throw new IllegalStateException("Cannot use Gradle object after build has finished.");
        }
        return gradle;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return withModelOrThrow(modelController::getLoadedSettings);
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        return withModelOrThrow(modelController::getConfiguredModel);
    }

    @Override
    public void prepareToScheduleTasks() {
        withModelOrThrow(() -> {
            state = State.TaskGraph;
            modelController.prepareToScheduleTasks();
            return null;
        });
    }

    @Override
    public void scheduleRequestedTasks() {
        withModelOrThrow(() -> {
            state = State.TaskGraph;
            modelController.prepareToScheduleTasks();
            workPreparer.populateWorkGraph(gradle, taskGraph -> modelController.scheduleRequestedTasks());
            return null;
        });
    }

    @Override
    public void populateWorkGraph(Consumer<? super TaskExecutionGraphInternal> action) {
        withModelOrThrow(() -> {
            state = State.TaskGraph;
            modelController.prepareToScheduleTasks();
            workPreparer.populateWorkGraph(gradle, action);
            return null;
        });
    }

    @Override
    public ExecutionResult<Void> executeTasks() {
        return withModel(() -> {
            if (state != State.TaskGraph) {
                throw new IllegalStateException("Cannot execute tasks as none have been scheduled for this build yet.");
            }
            return workExecutor.execute(gradle);
        });
    }

    private <T> T withModelOrThrow(Supplier<T> action) {
        return withModel(() -> {
            try {
                T result = action.get();
                return ExecutionResult.succeeded(result);
            } catch (Throwable t) {
                return ExecutionResult.failed(exceptionAnalyser.transform(t));
            }
        }).getValueOrRethrow();
    }

    private <T> ExecutionResult<T> withModel(Supplier<ExecutionResult<T>> action) {
        if (stageFailures != null) {
            throw new IllegalStateException("Cannot do further work as this build has failed.", stageFailures.getFailure());
        }
        if (state == State.Finished) {
            throw new IllegalStateException("Cannot do further work as this build has finished.");
        }
        ExecutionResult<T> result = action.get();
        if (state == State.Created) {
            state = State.Configure;
        }
        if (!result.getFailures().isEmpty()) {
            stageFailures = result;
        }
        return result;
    }

    @Override
    public ExecutionResult<Void> finishBuild(@Nullable Throwable failure) {
        if (state == State.Finished) {
            return ExecutionResult.succeeded();
        }
        // Fire the build finished events even if nothing has happened to this build, because quite a lot of internal infrastructure
        // adds listeners and expects to see a build finished event. Infrastructure should not be using the public listener types
        // In addition, they almost all should be using a build tree scoped event instead of a build scoped event

        Throwable reportableFailure = failure;
        if (reportableFailure == null && stageFailures != null) {
            reportableFailure = exceptionAnalyser.transform(stageFailures.getFailures());
        }
        BuildResult buildResult = new BuildResult(state.getDisplayName(), gradle, reportableFailure);
        ExecutionResult<Void> finishResult;
        try {
            buildListener.buildFinished(buildResult);
            buildFinishedListener.buildFinished((GradleInternal) buildResult.getGradle(), buildResult.getFailure() != null);
            finishResult = ExecutionResult.succeeded();
        } catch (Throwable t) {
            finishResult = ExecutionResult.failed(t);
        }
        state = State.Finished;
        stageFailures = null;
        return finishResult;
    }

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the execution of the build.
     * See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for supported listener types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addListener(Object listener) {
        gradle.addListener(listener);
    }

    @Override
    public void stop() {
        if (state != State.Created && state != State.Finished) {
            throw new IllegalStateException("This build has not been finished.");
        }
        try {
            CompositeStoppable.stoppable(buildServices).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }
}
