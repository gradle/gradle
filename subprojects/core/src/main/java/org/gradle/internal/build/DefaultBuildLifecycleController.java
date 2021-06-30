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
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.initialization.BuildCompletionListener;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.scopes.BuildScopeServices;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
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
    private final BuildWorkExecutor buildExecuter;
    private final BuildScopeServices buildServices;
    private final GradleInternal gradle;
    private final BuildModelController modelController;
    private State state = State.Created;
    @Nullable
    private RuntimeException stageFailure;

    public DefaultBuildLifecycleController(
        GradleInternal gradle,
        BuildModelController buildModelController,
        ExceptionAnalyser exceptionAnalyser,
        BuildListener buildListener,
        BuildCompletionListener buildCompletionListener,
        InternalBuildFinishedListener buildFinishedListener,
        BuildWorkExecutor buildExecuter,
        BuildScopeServices buildServices
    ) {
        this.gradle = gradle;
        this.modelController = buildModelController;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.buildExecuter = buildExecuter;
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
        return withModel(modelController::getLoadedSettings);
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        return withModel(modelController::getConfiguredModel);
    }

    @Override
    public void scheduleTasks(final Iterable<String> taskPaths) {
        withModel(() -> {
            state = State.TaskGraph;
            modelController.scheduleTasks(taskPaths);
            return null;
        });
    }

    @Override
    public void scheduleRequestedTasks() {
        withModel(() -> {
            state = State.TaskGraph;
            modelController.scheduleRequestedTasks();
            return null;
        });
    }

    @Override
    public void populateWorkGraph(Consumer<? super TaskExecutionGraphInternal> action) {
        withModel(() -> {
            state = State.TaskGraph;
            action.accept(gradle.getTaskGraph());
            return null;
        });
    }

    @Override
    public void executeTasks() {
        withModel(() -> {
            if (state != State.TaskGraph) {
                throw new IllegalStateException("Cannot execute tasks as none have been scheduled for this build yet.");
            }
            List<Throwable> taskFailures = new ArrayList<>();
            buildExecuter.execute(gradle, taskFailures);
            if (!taskFailures.isEmpty()) {
                throw new MultipleBuildFailures(taskFailures);
            }
            return null;
        });
    }

    private <T> T withModel(Supplier<T> action) {
        if (stageFailure != null) {
            throw new IllegalStateException("Cannot do further work as this build has failed.", stageFailure);
        }
        if (state == State.Finished) {
            throw new IllegalStateException("Cannot do further work as this build has finished.");
        }
        try {
            try {
                return action.get();
            } finally {
                if (state == State.Created) {
                    state = State.Configure;
                }
            }
        } catch (Throwable t) {
            stageFailure = exceptionAnalyser.transform(t);
            throw stageFailure;
        }
    }

    @Override
    public void finishBuild(@Nullable Throwable failure, Consumer<? super Throwable> collector) {
        if (state == State.Created) {
            state = State.Finished;
            return;
        }
        if (state == State.Finished) {
            return;
        }

        Throwable reportableFailure = failure;
        if (reportableFailure == null && stageFailure != null) {
            reportableFailure = stageFailure;
        }
        BuildResult buildResult = new BuildResult(state.getDisplayName(), gradle, reportableFailure);
        try {
            buildListener.buildFinished(buildResult);
            buildFinishedListener.buildFinished((GradleInternal) buildResult.getGradle(), buildResult.getFailure() != null);
        } catch (Throwable t) {
            collector.accept(t);
        }
        state = State.Finished;
        stageFailure = null;
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
