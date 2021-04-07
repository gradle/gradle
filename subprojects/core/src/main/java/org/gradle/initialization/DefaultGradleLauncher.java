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

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.configuration.ProjectsPreparer;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.scopes.BuildScopeServices;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DefaultGradleLauncher implements GradleLauncher {
    private enum Stage {
        LoadSettings, Configure, TaskGraph, RunTasks, Finished;

        String getDisplayName() {
            if (Configure.compareTo(this) >= 0) {
                return "Configure";
            } else {
                return "Build";
            }
        }
    }

    private final ProjectsPreparer projectsPreparer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final BuildCompletionListener buildCompletionListener;
    private final InternalBuildFinishedListener buildFinishedListener;
    private final BuildWorkExecutor buildExecuter;
    private final BuildScopeServices buildServices;
    private final List<?> servicesToStop;
    private final GradleInternal gradle;
    private final ConfigurationCache configurationCache;
    private final SettingsPreparer settingsPreparer;
    private final TaskExecutionPreparer taskExecutionPreparer;
    private final BuildOptionBuildOperationProgressEventsEmitter buildOptionBuildOperationProgressEventsEmitter;

    private Stage stage;
    @Nullable
    private RuntimeException stageFailure;

    public DefaultGradleLauncher(
        GradleInternal gradle,
        ProjectsPreparer projectsPreparer,
        ExceptionAnalyser exceptionAnalyser,
        BuildListener buildListener,
        BuildCompletionListener buildCompletionListener,
        InternalBuildFinishedListener buildFinishedListener,
        BuildWorkExecutor buildExecuter,
        BuildScopeServices buildServices,
        List<?> servicesToStop,
        SettingsPreparer settingsPreparer,
        TaskExecutionPreparer taskExecutionPreparer,
        ConfigurationCache configurationCache,
        BuildOptionBuildOperationProgressEventsEmitter buildOptionBuildOperationProgressEventsEmitter
    ) {
        this.gradle = gradle;
        this.projectsPreparer = projectsPreparer;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.buildExecuter = buildExecuter;
        this.buildCompletionListener = buildCompletionListener;
        this.buildFinishedListener = buildFinishedListener;
        this.buildServices = buildServices;
        this.servicesToStop = servicesToStop;
        this.configurationCache = configurationCache;
        this.settingsPreparer = settingsPreparer;
        this.taskExecutionPreparer = taskExecutionPreparer;
        this.buildOptionBuildOperationProgressEventsEmitter = buildOptionBuildOperationProgressEventsEmitter;
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public File getBuildRootDir() {
        return buildServices.get(BuildLayout.class).getRootDirectory();
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        doBuildStages(Stage.LoadSettings);
        return gradle.getSettings();
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        doBuildStages(Stage.Configure);
        return gradle;
    }

    @Override
    public void scheduleRequestedTasks() {
        doBuildStages(Stage.TaskGraph);
    }

    @Override
    public void executeTasks() {
        doBuildStages(Stage.RunTasks);
    }

    private void doBuildStages(Stage upTo) {
        if (stageFailure != null) {
            throw new IllegalStateException("Cannot do further work as this build has failed.");
        }
        try {
            if (stage == null && gradle.isRootBuild()) {
                buildOptionBuildOperationProgressEventsEmitter.emit(gradle.getStartParameter());
            }
            if (upTo == Stage.RunTasks) {
                prepareTaskGraph();
                runWork();
            } else if (upTo == Stage.TaskGraph) {
                prepareTaskGraph();
            } else {
                doClassicBuildStages(upTo);
            }
        } catch (Throwable t) {
            stage = upTo;
            stageFailure = exceptionAnalyser.transform(t);
            throw stageFailure;
        }
    }

    private void prepareTaskGraph() {
        if (stage == Stage.TaskGraph) {
            return;
        }
        if (configurationCache.canLoad()) {
            doLoadTaskGraphFromCache();
        } else {
            doClassicBuildStages(Stage.TaskGraph);
        }
        stage = Stage.TaskGraph;
    }

    private void doClassicBuildStages(Stage upTo) {
        if (stage == null) {
            configurationCache.prepareForConfiguration();
        }
        prepareSettings();
        if (upTo == Stage.LoadSettings) {
            return;
        }
        prepareProjects();
        if (upTo == Stage.Configure) {
            return;
        }
        prepareTaskExecution();
        configurationCache.save();
    }

    private void doLoadTaskGraphFromCache() {
        configurationCache.load();
    }

    @Override
    public void finishBuild(@Nullable Throwable failure, Consumer<? super Throwable> collector) {
        if (stage == null || stage == Stage.Finished) {
            return;
        }

        Throwable reportableFailure = failure;
        if (reportableFailure == null && stageFailure != null) {
            reportableFailure = stageFailure;
        }
        BuildResult buildResult = new BuildResult(stage.getDisplayName(), gradle, reportableFailure);
        try {
            buildListener.buildFinished(buildResult);
            buildFinishedListener.buildFinished((GradleInternal) buildResult.getGradle(), buildResult.getFailure() != null);
        } catch (Throwable t) {
            collector.accept(t);
        }
        stage = Stage.Finished;
        stageFailure = null;
    }

    private void prepareSettings() {
        if (stage == null) {
            settingsPreparer.prepareSettings(gradle);
            stage = Stage.LoadSettings;
        }
    }

    private void prepareProjects() {
        if (stage == Stage.LoadSettings) {
            projectsPreparer.prepareProjects(gradle);
            stage = Stage.Configure;
        }
    }

    private void prepareTaskExecution() {
        if (stage == Stage.Configure) {
            taskExecutionPreparer.prepareForTaskExecution(gradle);

            stage = Stage.TaskGraph;
        }
    }

    @Override
    public void scheduleTasks(final Iterable<String> taskPaths) {
        boolean added = getConfiguredBuild().getStartParameter().addTaskNames(taskPaths);
        if (!added) {
            return;
        }
        reevaluateTaskGraph();
    }

    private void reevaluateTaskGraph() {
        // Force back to configure so that task graph will get reevaluated
        stage = Stage.Configure;
        doBuildStages(Stage.TaskGraph);
    }

    private void runWork() {
        if (stage != Stage.TaskGraph) {
            throw new IllegalStateException("Cannot execute tasks: current stage = " + stage);
        }

        List<Throwable> taskFailures = new ArrayList<>();
        buildExecuter.execute(gradle, taskFailures);
        if (!taskFailures.isEmpty()) {
            throw new MultipleBuildFailures(taskFailures);
        }

        stage = Stage.RunTasks;
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
        try {
            CompositeStoppable.stoppable(buildServices).add(servicesToStop).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }
}
