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

import com.google.common.collect.ImmutableList;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.specs.Spec;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.TaskSelection;
import org.gradle.execution.plan.BuildWorkPlan;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.execution.plan.FinalizedExecutionPlan;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.execution.plan.ScheduledWork;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.Describables;
import org.gradle.internal.Pair;
import org.gradle.internal.model.StateTransitionController;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.gradle.util.Path.path;

@SuppressWarnings("deprecation")
public class DefaultBuildLifecycleController implements BuildLifecycleController {
    private enum State implements StateTransitionController.State {
        // Configuring the build, can access build model
        Configure,
        // Scheduling tasks for execution
        TaskSchedule,
        ReadyToRun,
        BuildFinishHooks,
        ReadyToReset,
        // build has finished and should do no further work
        Finished
    }

    private static final ImmutableList<State> CONFIGURATION_STATES = ImmutableList.of(State.Configure, State.TaskSchedule, State.ReadyToRun);

    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final BuildModelLifecycleListener buildModelLifecycleListener;
    private final BuildWorkPreparer workPreparer;
    private final BuildWorkExecutor workExecutor;
    private final BuildToolingModelControllerFactory toolingModelControllerFactory;
    private final BuildModelController modelController;
    private final StateTransitionController<State> state;
    private final GradleInternal gradle;
    private boolean hasTasks;
    private boolean hasFiredBeforeModelDiscarded;

    public DefaultBuildLifecycleController(
        GradleInternal gradle,
        BuildModelController buildModelController,
        ExceptionAnalyser exceptionAnalyser,
        BuildListener buildListener,
        BuildModelLifecycleListener buildModelLifecycleListener,
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
        this.buildModelLifecycleListener = buildModelLifecycleListener;
        this.toolingModelControllerFactory = toolingModelControllerFactory;
        this.state = controllerFactory.newController(Describables.of("state of", targetBuild().getDisplayName()), State.Configure);
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
    public void resetModel() {
        state.restart(State.ReadyToReset, State.Configure, () -> {
            gradle.resetState();
            for (HoldsProjectState service : gradle.getServices().getAll(HoldsProjectState.class)) {
                service.discardAll();
            }
        });
    }

    @Override
    public ExecutionResult<Void> beforeModelReset() {
        return state.transition(CONFIGURATION_STATES, State.ReadyToReset, failures -> fireBeforeModelDiscarded(false));
    }

    @Override
    public ExecutionResult<Void> beforeModelDiscarded(boolean failed) {
        return state.transition(State.BuildFinishHooks, State.Finished, failures -> fireBeforeModelDiscarded(failed));
    }

    private ExecutionResult<Void> fireBeforeModelDiscarded(boolean failed) {
        if (hasFiredBeforeModelDiscarded) {
            return ExecutionResult.succeeded();
        } else {
            hasFiredBeforeModelDiscarded = true;
            return ExecutionResult.maybeFailing(() -> buildModelLifecycleListener.beforeModelDiscarded(gradle, failed));
        }
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
        ExecutionPlan plan = workPreparer.newExecutionPlan();
        return new DefaultBuildWorkPlan(this, plan);
    }

    @Override
    public void populateWorkGraph(BuildWorkPlan plan, Consumer<? super WorkGraphBuilder> action) {
        DefaultBuildWorkPlan workPlan = unpack(plan);
        workPlan.empty = false;
        state.inState(State.TaskSchedule, () -> workPreparer.populateWorkGraph(gradle, workPlan.plan, dest -> action.accept(new DefaultWorkGraphBuilder(dest))));
    }

    @Override
    public void finalizeWorkGraph(BuildWorkPlan plan) {
        DefaultBuildWorkPlan workPlan = unpack(plan);
        if (workPlan.empty) {
            return;
        }
        state.transition(State.TaskSchedule, State.ReadyToRun, () -> {
            for (Consumer<LocalTaskNode> handler : workPlan.handlers) {
                workPlan.plan.onComplete(handler);
            }
            workPlan.finalizedPlan = workPreparer.finalizeWorkGraph(gradle, workPlan.plan);
        });
    }

    @NonNullApi
    private class EntryTaskSelectorContext implements EntryTaskSelector.Context {

        @Override
        public TaskSelection getSelection(String taskPath) {
            // We assume taskPath is valid since the selector has been used before
            return selectionOf(path(taskPath));
        }

        private TaskSelection selectionOf(Path taskPath) {
            Path taskProjectPath = taskPath.getParent();
            assert taskProjectPath != null;
            ProjectInternal taskProject = resolveProject(taskProjectPath);
            if (taskProject != null) {
                String taskName = taskPath.getName();
                return new TaskSelection(
                    taskProject.getPath(),
                    taskName,
                    tasks -> tasks.add(taskProject.getTasks().getByName(taskName))
                );
            }
            return new TaskSelection(null, null, tasks -> {});
        }

        @Override
        public GradleInternal getGradle() {
            return gradle;
        }

        @Nullable
        private ProjectInternal resolveProject(Path path) {
            assert path.isAbsolute();

            // Try to resolve the path as "<included-build-path>:<project-path>"
            Pair<Path, BuildState> includedBuildPrefix = resolveIncludedBuildPrefixOf(path);
            if (includedBuildPrefix != null) {
                Path includedBuildPath = includedBuildPrefix.left;
                BuildState includedBuild = includedBuildPrefix.right;
                Path projectPath = path.removeFirstSegments(includedBuildPath.segmentCount());
                return findProjectOf(includedBuild, projectPath);
            }

            return findProjectOf(targetBuild(), path);
        }

        /**
         * Finds the longest {@code path} prefix that identifies an included build and returns a pair
         * with the matching {@code path} prefix and {@link BuildState included build reference}.
         *
         * @return {@code null} if no {@code path} prefix identifying an included build is found
         */
        @Nullable
        private Pair<Path, BuildState> resolveIncludedBuildPrefixOf(Path path) {
            Path resolvedPath = null;
            BuildState resolvedBuild = null;

            int prefixLength = 1;
            while (prefixLength <= path.segmentCount()) {
                Path candidatePath = path.takeFirstSegments(prefixLength);
                BuildState existingBuild = findBuild(candidatePath);
                if (existingBuild == null) {
                    break;
                }
                resolvedPath = candidatePath;
                resolvedBuild = existingBuild;
                prefixLength++;
            }

            return resolvedPath != null
                ? Pair.of(resolvedPath, resolvedBuild)
                : null;
        }

        @Nullable
        private ProjectInternal findProjectOf(BuildState build, Path projectPath) {
            ProjectState project = build.getProjects().findProject(projectPath);
            return project != null
                ? project.getMutableModel()
                : null;
        }

        @Nullable
        private BuildState findBuild(Path buildPath) {
            return getBuildStateRegistry().findBuild(new DefaultBuildIdentifier(buildPath));
        }

        private BuildStateRegistry getBuildStateRegistry() {
            return gradle.getServices().get(BuildStateRegistry.class);
        }
    }

    @Override
    public ExecutionResult<Void> executeTasks(BuildWorkPlan plan) {
        // Execute tasks and transition back to "configure", as this build may run more tasks;
        DefaultBuildWorkPlan workPlan = unpack(plan);
        if (workPlan.empty) {
            return ExecutionResult.succeeded();
        }
        return state.tryTransition(State.ReadyToRun, State.Configure, () -> {
            List<BiConsumer<EntryTaskSelector.Context, QueryableExecutionPlan>> finalizations = workPlan.finalizations;
            if (!finalizations.isEmpty()) {
                EntryTaskSelectorContext context = new EntryTaskSelectorContext();
                for (BiConsumer<EntryTaskSelector.Context, QueryableExecutionPlan> finalization : finalizations) {
                    finalization.accept(context, workPlan.finalizedPlan.getContents());
                }
                workPlan.finalizations.clear();
            }
            return workExecutor.execute(gradle, workPlan.finalizedPlan);
        });
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
        return action.apply(toolingModelControllerFactory.createController(targetBuild(), this));
    }

    private BuildState targetBuild() {
        return gradle.getOwner();
    }

    @Override
    public ExecutionResult<Void> finishBuild(@Nullable Throwable failure) {
        return state.transition(CONFIGURATION_STATES, State.BuildFinishHooks, stageFailures -> {
            // Fire the build finished events even if nothing has happened to this build, because quite a lot of internal infrastructure
            // adds listeners and expects to see a build finished event. Infrastructure should not be using the public listener types
            // In addition, they almost all should be using a build tree scoped event instead of a build scoped event

            Throwable reportableFailure = failure;
            if (reportableFailure == null && !stageFailures.getFailures().isEmpty()) {
                reportableFailure = exceptionAnalyser.transform(stageFailures.getFailures());
            }
            BuildResult buildResult = new BuildResult(hasTasks ? "Build" : "Configure", gradle, reportableFailure);
            return ExecutionResult.maybeFailing(() -> buildListener.buildFinished(buildResult));
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
        private final List<BiConsumer<EntryTaskSelector.Context, QueryableExecutionPlan>> finalizations = new ArrayList<>();
        private FinalizedExecutionPlan finalizedPlan;
        private boolean empty = true;

        public DefaultBuildWorkPlan(DefaultBuildLifecycleController owner, ExecutionPlan plan) {
            this.owner = owner;
            this.plan = plan;
        }

        @Override
        public void stop() {
            plan.close();
        }

        @Override
        public void addFilter(Spec<Task> filter) {
            plan.addFilter(filter);
        }

        @Override
        public void addFinalization(BiConsumer<EntryTaskSelector.Context, QueryableExecutionPlan> finalization) {
            finalizations.add(finalization);
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
        public void addRequestedTasks(@Nullable EntryTaskSelector selector) {
            modelController.scheduleRequestedTasks(selector, plan);
        }

        @Override
        public void addEntryTasks(List<? extends Task> tasks) {
            for (Task task : tasks) {
                plan.addEntryTask(task);
            }
        }

        @Override
        public void setScheduledWork(ScheduledWork work) {
            plan.setScheduledWork(work);
        }
    }
}
