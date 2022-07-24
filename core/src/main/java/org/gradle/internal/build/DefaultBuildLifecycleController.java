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
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.execution.plan.BuildWorkPlan;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.execution.plan.Node;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.internal.Describables;
import org.gradle.internal.model.StateTransitionController;
import org.gradle.internal.model.StateTransitionControllerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("deprecation")
public class DefaultBuildLifecycleController implements BuildLifecycleController {
    private enum State implements StateTransitionController.State {
        // Configuring the build, can access build model
        Configure,
        // Scheduling tasks for execution
        TaskSchedule,
        ReadyToRun,
        // build has finished and should do no further work
        Finished
    }

    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final InternalBuildFinishedListener buildFinishedListener;
    private final BuildWorkPreparer workPreparer;
    private final BuildWorkExecutor workExecutor;
    private final BuildToolingModelControllerFactory toolingModelControllerFactory;
    private final BuildModelController modelController;
    private final StateTransitionController<State> state;
    private final GradleInternal gradle;
    private boolean hasTasks;

    public DefaultBuildLifecycleController(
        GradleInternal gradle,
        BuildModelController buildModelController,
        ExceptionAnalyser exceptionAnalyser,
        BuildListener buildListener,
        InternalBuildFinishedListener buildFinishedListener,
        BuildWorkPreparer workPreparer,
        BuildWorkExecutor workExecutor,
        BuildToolingModelControllerFactory toolingModelControllerFactory,
        StateTransitionControllerFactory controllerFactory
    ) {
        this.gradle = gradle;
        this.modelController = buildModelController;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.workPreparer = workPreparer;
        this.workExecutor = workExecutor;
        this.buildFinishedListener = buildFinishedListener;
        this.toolingModelControllerFactory = toolingModelControllerFactory;
        this.state = controllerFactory.newController(Describables.of("state of", gradle.getOwner().getDisplayName()), State.Configure);
    }

    @Override
    public GradleInternal getGradle() {
        // Should not ignore other threads, however it is currently possible for this to be queried by tasks at execution time (that is, when another thread is
        // transitioning the task graph state). Instead, it may be better to:
        // - have the threads use some specific immutable view of the build model state instead of requiring direct access to the build model.
        // - not have a thread blocked around task execution, so that other threads can use the build model.
        // - maybe split the states into one for the build model and one for the task graph.
        state.assertNotInState(State.Finished);
        return gradle;
    }

    @Override
    public void loadSettings() {
        state.notInState(State.Finished, modelController::getLoadedSettings);
    }

    @Override
    public <T> T withSettings(Function<? super SettingsInternal, T> action) {
        return state.notInState(State.Finished, () -> action.apply(modelController.getLoadedSettings()));
    }

    @Override
    public void configureProjects() {
        state.notInState(State.Finished, modelController::getConfiguredModel);
    }

    @Override
    public <T> T withProjectsConfigured(Function<? super GradleInternal, T> action) {
        return state.notInState(State.Finished, () -> action.apply(modelController.getConfiguredModel()));
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        // Should not ignore other threads. See above.
        return state.notInStateIgnoreOtherThreads(State.Finished, modelController::getConfiguredModel);
    }

    @Override
    public void prepareToScheduleTasks() {
        state.maybeTransition(State.Configure, State.TaskSchedule, () -> {
            hasTasks = true;
            modelController.prepareToScheduleTasks();
        });
    }

    @Override
    public BuildWorkPlan newWorkGraph() {
        return state.inState(State.TaskSchedule, () -> {
            ExecutionPlan plan = workPreparer.newExecutionPlan();
            modelController.initializeWorkGraph(plan);
            return new DefaultBuildWorkPlan(this, plan);
        });
    }

    @Override
    public void populateWorkGraph(BuildWorkPlan plan, Consumer<? super WorkGraphBuilder> action) {
        DefaultBuildWorkPlan workPlan = unpack(plan);
        state.inState(State.TaskSchedule, () -> workPreparer.populateWorkGraph(gradle, workPlan.plan, dest -> action.accept(new DefaultWorkGraphBuilder(dest))));
    }

    @Override
    public void finalizeWorkGraph(BuildWorkPlan plan) {
        DefaultBuildWorkPlan workPlan = unpack(plan);
        state.transition(State.TaskSchedule, State.ReadyToRun, () -> {
            for (Consumer<LocalTaskNode> handler : workPlan.handlers) {
                workPlan.plan.onComplete(handler);
            }
            workPreparer.finalizeWorkGraph(gradle, workPlan.plan);
        });
    }

    @Override
    public ExecutionResult<Void> executeTasks(BuildWorkPlan plan) {
        // Execute tasks and transition back to "configure", as this build may run more tasks;
        DefaultBuildWorkPlan workPlan = unpack(plan);
        return state.tryTransition(State.ReadyToRun, State.Configure, () -> workExecutor.execute(gradle, workPlan.plan));
    }

    private DefaultBuildWorkPlan unpack(BuildWorkPlan plan) {
        DefaultBuildWorkPlan workPlan = (DefaultBuildWorkPlan) plan;
        if (workPlan.owner != this) {
            throw new IllegalArgumentException("Unexpected plan owner.");
        }
        return workPlan;
    }

    @Override
    public <T> T withToolingModels(Function<? super BuildToolingModelController, T> action) {
        return action.apply(toolingModelControllerFactory.createController(gradle.getOwner(), this));
    }

    @Override
    public ExecutionResult<Void> finishBuild(@Nullable Throwable failure) {
        return state.finish(State.Finished, stageFailures -> {
            // Fire the build finished events even if nothing has happened to this build, because quite a lot of internal infrastructure
            // adds listeners and expects to see a build finished event. Infrastructure should not be using the public listener types
            // In addition, they almost all should be using a build tree scoped event instead of a build scoped event

            Throwable reportableFailure = failure;
            if (reportableFailure == null && !stageFailures.getFailures().isEmpty()) {
                reportableFailure = exceptionAnalyser.transform(stageFailures.getFailures());
            }
            BuildResult buildResult = new BuildResult(hasTasks ? "Build" : "Configure", gradle, reportableFailure);
            ExecutionResult<Void> finishResult;
            try {
                buildListener.buildFinished(buildResult);
                buildFinishedListener.buildFinished((GradleInternal) buildResult.getGradle(), buildResult.getFailure() != null);
                finishResult = ExecutionResult.succeeded();
            } catch (Throwable t) {
                finishResult = ExecutionResult.failed(t);
            }
            return finishResult;
        });
    }

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the execution of the build.
     * See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for supported listener types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addListener(Object listener) {
        getGradle().addListener(listener);
    }

    private static class DefaultBuildWorkPlan implements BuildWorkPlan {
        private final DefaultBuildLifecycleController owner;
        private final ExecutionPlan plan;
        private final List<Consumer<LocalTaskNode>> handlers = new ArrayList<>();

        public DefaultBuildWorkPlan(DefaultBuildLifecycleController owner, ExecutionPlan plan) {
            this.owner = owner;
            this.plan = plan;
        }

        @Override
        public void stop() {
            plan.close();
        }

        @Override
        public void onComplete(Consumer<LocalTaskNode> handler) {
            handlers.add(handler);
        }
    }

    private class DefaultWorkGraphBuilder implements WorkGraphBuilder {
        private final ExecutionPlan plan;

        public DefaultWorkGraphBuilder(ExecutionPlan plan) {
            this.plan = plan;
        }

        @Override
        public void addRequestedTasks() {
            modelController.scheduleRequestedTasks(plan);
        }

        @Override
        public void addEntryTasks(List<? extends Task> tasks) {
            for (Task task : tasks) {
                plan.addEntryTasks(Collections.singletonList(task));
            }
        }

        @Override
        public void addNodes(List<? extends Node> nodes) {
            plan.addNodes(nodes);
        }
    }
}
