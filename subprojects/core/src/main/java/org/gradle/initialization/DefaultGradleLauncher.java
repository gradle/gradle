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
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildExecuter;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultGradleLauncher implements GradleLauncher {

    private static final ConfigureBuildBuildOperationType.Result CONFIGURE_BUILD_RESULT = new ConfigureBuildBuildOperationType.Result() {
    };
    private static final NotifyProjectsEvaluatedBuildOperationType.Result PROJECTS_EVALUATED_RESULT = new NotifyProjectsEvaluatedBuildOperationType.Result() {
    };

    private enum Stage {
        LoadSettings, Configure, TaskGraph, RunTasks() {
            @Override
            String getDisplayName() {
                return "Build";
            }
        }, Finished;

        String getDisplayName() {
            return name();
        }
    }

    private static final LoadBuildBuildOperationType.Result RESULT = new LoadBuildBuildOperationType.Result() {
    };

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
    private final IncludedBuildControllers includedBuildControllers;
    private final PublicBuildPath fromBuild;
    private final GradleInternal gradle;
    private SettingsInternal settings;
    private Stage stage;

    public DefaultGradleLauncher(GradleInternal gradle, InitScriptHandler initScriptHandler, SettingsLoader settingsLoader, BuildLoader buildLoader,
                                 BuildConfigurer buildConfigurer, ExceptionAnalyser exceptionAnalyser,
                                 BuildListener buildListener, ModelConfigurationListener modelConfigurationListener,
                                 BuildCompletionListener buildCompletionListener, BuildOperationExecutor operationExecutor,
                                 BuildConfigurationActionExecuter buildConfigurationActionExecuter, BuildExecuter buildExecuter,
                                 BuildScopeServices buildServices, List<?> servicesToStop, IncludedBuildControllers includedBuildControllers, PublicBuildPath fromBuild) {
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
        this.includedBuildControllers = includedBuildControllers;
        this.fromBuild = fromBuild;
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        doBuildStages(Stage.LoadSettings);
        return settings;
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        doBuildStages(Stage.Configure);
        return gradle;
    }

    public GradleInternal executeTasks() {
        doBuildStages(Stage.RunTasks);
        return gradle;
    }

    @Override
    public void finishBuild() {
        if (stage != null) {
            finishBuild(stage.getDisplayName(), null);
        }
    }

    private void doBuildStages(Stage upTo) {
        try {
            loadSettings();
            if (upTo == Stage.LoadSettings) {
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
            if (upTo == Stage.RunTasks) {
                return;
            }
            finishBuild();
        } catch (Throwable t) {
            finishBuild(upTo.getDisplayName(), t);
        }
    }

    private void finishBuild(String action, @Nullable Throwable stageFailure) {
        if (stage == Stage.Finished) {
            return;
        }

        RuntimeException reportableFailure = stageFailure == null ? null : exceptionAnalyser.transform(stageFailure);
        BuildResult buildResult = new BuildResult(action, gradle, reportableFailure);
        List<Throwable> failures = new ArrayList<Throwable>();
        includedBuildControllers.finishBuild(failures);
        try {
            buildListener.buildFinished(buildResult);
        } catch (Throwable t) {
            failures.add(t);
        }
        stage = Stage.Finished;

        if (failures.isEmpty() && reportableFailure != null) {
            throw reportableFailure;
        }
        if (!failures.isEmpty()) {
            if (stageFailure instanceof MultipleBuildFailures) {
                failures.addAll(0, ((MultipleBuildFailures) stageFailure).getCauses());
            } else if (stageFailure != null) {
                failures.add(0, stageFailure);
            }
            throw exceptionAnalyser.transform(new MultipleBuildFailures(failures));
        }
    }

    private void loadSettings() {
        if (stage == null) {
            buildListener.buildStarted(gradle);

            buildOperationExecutor.run(new LoadBuild());

            stage = Stage.LoadSettings;
        }
    }

    private void configureBuild() {
        if (stage == Stage.LoadSettings) {
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

        stage = Stage.RunTasks;
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
            context.setResult(RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(gradle.contextualize("Load build"))
                    .details(new LoadBuildBuildOperationType.Details() {
                        @Override
                        public String getBuildPath() {
                            return gradle.getIdentityPath().toString();
                        }

                        @Override
                        public String getIncludedBy() {
                            return fromBuild == null ? null : fromBuild.getBuildPath().toString();
                        }
                    });
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
            context.setResult(CONFIGURE_BUILD_RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName(gradle.contextualize("Configure build"));
            if (gradle.getParent() == null) {
                builder.operationType(BuildOperationCategory.CONFIGURE_ROOT_BUILD);
            } else {
                builder.operationType(BuildOperationCategory.CONFIGURE_BUILD);
            }
            builder.totalProgress(settings.getProjectRegistry().size());
            return builder.details(new ConfigureBuildBuildOperationType.Details() {
                @Override
                public String getBuildPath() {
                    return getGradle().getIdentityPath().toString();
                }
            });
        }
    }

    private class CalculateTaskGraph implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext buildOperationContext) {
            buildConfigurationActionExecuter.select(gradle);

            if (isConfigureOnDemand()) {
                projectsEvaluated();
            }

            final TaskExecutionGraphInternal taskGraph = gradle.getTaskGraph();
            taskGraph.populate();

            includedBuildControllers.populateTaskGraphs();

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
            return BuildOperationDescriptor.displayName(gradle.contextualize("Calculate task graph"))
                    .details(new CalculateTaskGraphBuildOperationType.Details() {
                        @Override
                        public String getBuildPath() {
                            return getGradle().getIdentityPath().getPath();
                        }
                    });
        }
    }

    private class ExecuteTasks implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            includedBuildControllers.startTaskExecution();
            List<Throwable> taskFailures = new ArrayList<Throwable>();
            buildExecuter.execute(gradle, taskFailures);
            includedBuildControllers.awaitTaskCompletion(taskFailures);
            if (!taskFailures.isEmpty()) {
                throw new MultipleBuildFailures(taskFailures);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            BuildOperationDescriptor.Builder builder = BuildOperationDescriptor.displayName(gradle.contextualize("Run tasks"));
            if (gradle.getParent() == null) {
                builder.operationType(BuildOperationCategory.RUN_WORK_ROOT_BUILD);
            } else {
                builder.operationType(BuildOperationCategory.RUN_WORK);
            }
            builder.totalProgress(gradle.getTaskGraph().size());
            return builder;
        }
    }

    private class NotifyProjectsEvaluatedListeners implements RunnableBuildOperation {

        @Override
        public void run(BuildOperationContext context) {
            buildListener.projectsEvaluated(gradle);
            context.setResult(PROJECTS_EVALUATED_RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName(gradle.contextualize("Notify projectsEvaluated listeners"))
                    .details(new NotifyProjectsEvaluatedBuildOperationType.Details() {
                        @Override
                        public String getBuildPath() {
                            return gradle.getIdentityPath().toString();
                        }
                    });
        }
    }

    private boolean isConfigureOnDemand() {
        return gradle.getStartParameter().isConfigureOnDemand();
    }

    private void projectsEvaluated() {
        buildOperationExecutor.run(new NotifyProjectsEvaluatedListeners());
    }

}
