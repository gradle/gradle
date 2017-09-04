/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.initialization;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Task;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildExecuter;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;
import org.gradle.util.Path;

import java.util.List;
import java.util.Set;

public class DefaultGradleLauncher implements GradleLauncher {

    private enum Stage {
        Load, LoadBuild, Configure, TaskGraph, Build, Finished
    }

    private final InitScriptHandler initScriptHandler;
    private final SettingsLoader settingsLoader;
    private final BuildLoader buildLoader;
    private final BuildConfigurer buildConfigurer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final ModelConfigurationListener modelConfigurationListener;
    private final BuildCompletionListener buildCompletionListener;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildConfigurationActionExecuter buildConfigurationActionExecuter;
    private final BuildExecuter buildExecuter;
    private final BuildScopeServices buildServices;
    private final List<?> servicesToStop;
    private GradleInternal gradle;
    private SettingsInternal settings;
    private Stage stage;

    public DefaultGradleLauncher(GradleInternal gradle, InitScriptHandler initScriptHandler, SettingsLoader settingsLoader, BuildLoader buildLoader,
                                 BuildConfigurer buildConfigurer, ExceptionAnalyser exceptionAnalyser,
                                 BuildListener buildListener, ModelConfigurationListener modelConfigurationListener,
                                 BuildCompletionListener buildCompletionListener, BuildOperationExecutor operationExecutor,
                                 BuildConfigurationActionExecuter buildConfigurationActionExecuter, BuildExecuter buildExecuter,
                                 BuildScopeServices buildServices, List<?> servicesToStop) {
        this.gradle = gradle;
        this.initScriptHandler = initScriptHandler;
        this.settingsLoader = settingsLoader;
        this.buildLoader = buildLoader;
        this.buildConfigurer = buildConfigurer;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.modelConfigurationListener = modelConfigurationListener;
        this.buildOperationExecutor = operationExecutor;
        this.buildConfigurationActionExecuter = buildConfigurationActionExecuter;
        this.buildExecuter = buildExecuter;
        this.buildCompletionListener = buildCompletionListener;
        this.buildServices = buildServices;
        this.servicesToStop = servicesToStop;
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        doBuildStages(Stage.Load);
        return settings;
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        doBuildStages(Stage.Configure);
        return gradle;
    }

    public GradleInternal executeTasks() {
        doBuildStages(Stage.Build);
        return gradle;
    }

    @Override
    public void finishBuild() {
        if (stage != null) {
            finishBuild(new BuildResult(stage.name(), gradle, null));
        }
    }

    private void doBuildStages(Stage upTo) {
        try {
            loadSettings();
            if (upTo == Stage.Load) {
                return;
            }
            configureBuild();
            if (upTo == Stage.Configure) {
                return;
            }
            constructTaskGraph();
            if (upTo == Stage.TaskGraph) {
                return;
            }
            runTasks();
            finishBuild();
        } catch (Throwable t) {
            Throwable failure = exceptionAnalyser.transform(t);
            finishBuild(new BuildResult(upTo.name(), gradle, failure));
            throw new ReportedException(failure);
        }
    }

    private void finishBuild(BuildResult result) {
        if (stage == Stage.Finished) {
            return;
        }

        buildListener.buildFinished(result);
        if (!isNestedBuild()) {
            gradle.getServices().get(IncludedBuildControllers.class).stopTaskExecution();
        }
        stage = Stage.Finished;
    }

    private void loadSettings() {
        if (stage == null) {
            buildListener.buildStarted(gradle);

            buildOperationExecutor.run(new LoadBuild());

            stage = Stage.Load;
        }
    }

    private void configureBuild() {
        if (stage == Stage.Load) {
            buildOperationExecutor.run(new ConfigureBuild());

            stage = Stage.Configure;
        }
    }

    private void constructTaskGraph() {
        if (stage == Stage.Configure) {
            buildOperationExecutor.run(new CalculateTaskGraph());

            stage = Stage.TaskGraph;
        }
    }

    @Override
    public void scheduleTasks(final Iterable<String> taskPaths) {
        GradleInternal gradle = getConfiguredBuild();
        Set<String> allTasks = Sets.newLinkedHashSet(gradle.getStartParameter().getTaskNames());
        boolean added = allTasks.addAll(Lists.newArrayList(taskPaths));

        if (!added) {
            return;
        }

        gradle.getStartParameter().setTaskNames(allTasks);

        // Force back to configure so that task graph will get reevaluated
        stage = Stage.Configure;

        doBuildStages(Stage.TaskGraph);
    }

    private void runTasks() {
        if (stage != Stage.TaskGraph) {
            throw new IllegalStateException("Cannot execute tasks: current stage = " + stage);
        }

        buildOperationExecutor.run(new ExecuteTasks());

        stage = Stage.Build;
    }

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the execution of the build. See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for
     * supported listener types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addListener(Object listener) {
        gradle.addListener(listener);
    }

    public void stop() {
        try {
            CompositeStoppable.stoppable(buildServices).add(servicesToStop).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }

    private class LoadBuild implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            // Evaluate init scripts
            initScriptHandler.executeScripts(gradle);

            // Build `buildSrc`, load settings.gradle, and construct composite (if appropriate)
            settings = settingsLoader.findAndLoadSettings(gradle);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(contextualize("Load build")).
                parent(getGradle().getBuildOperation());
        }
    }

    private class ConfigureBuild implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            buildLoader.load(settings, gradle);
            buildConfigurer.configure(gradle);

            if (!isConfigureOnDemand()) {
                projectsEvaluated();
            }

            modelConfigurationListener.onConfigure(gradle);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(contextualize("Configure build")).
                parent(getGradle().getBuildOperation());
        }
    }

    private class CalculateTaskGraph implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext buildOperationContext) {
            buildConfigurationActionExecuter.select(gradle);

            if (isConfigureOnDemand()) {
                projectsEvaluated();
            }

            final TaskGraphExecuter taskGraph = gradle.getTaskGraph();
            taskGraph.populate();
            buildOperationContext.setResult(new CalculateTaskGraphBuildOperationType.Result() {
                @Override
                public List<String> getRequestedTaskPaths() {
                    return toTaskPaths(taskGraph.getRequestedTasks());
                }

                @Override
                public List<String> getExcludedTaskPaths() {
                    return toTaskPaths(taskGraph.getFilteredTasks());
                }

                private List<String> toTaskPaths(Set<Task> tasks) {
                    return ImmutableSortedSet.copyOf(Collections2.transform(tasks, new Function<Task, String>() {
                        @Override
                        public String apply(Task task) {
                            return task.getPath();
                        }
                    })).asList();
                }
            });
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(contextualize("Calculate task graph"))
                .details(new CalculateTaskGraphBuildOperationType.Details() {
                }).parent(getGradle().getBuildOperation());
        }
    }

    private class ExecuteTasks implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            if (!isNestedBuild()) {
                IncludedBuildControllers buildControllers = gradle.getServices().get(IncludedBuildControllers.class);
                buildControllers.startTaskExecution();
            }

            buildExecuter.execute(gradle);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(contextualize("Run tasks")).parent(getGradle().getBuildOperation());
        }
    }


    private boolean isConfigureOnDemand() {
        return gradle.getStartParameter().isConfigureOnDemand();
    }

    private void projectsEvaluated() {
        buildListener.projectsEvaluated(gradle);
    }

    private String contextualize(String descriptor) {
        if (isNestedBuild()) {
            Path contextPath = gradle.findIdentityPath();
            String context = contextPath == null ? gradle.getStartParameter().getCurrentDir().getName() : contextPath.getPath();
            return descriptor + " (" + context + ")";
        }
        return descriptor;
    }

    private boolean isNestedBuild() {
        return gradle.getParent() != null;
    }
}
