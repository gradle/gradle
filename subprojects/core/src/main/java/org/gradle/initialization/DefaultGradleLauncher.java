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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.configuration.ProjectsPreparer;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.scopes.BuildScopeServices;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultGradleLauncher implements GradleLauncher {
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

    private final ProjectsPreparer projectsPreparer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final BuildCompletionListener buildCompletionListener;
    private final InternalBuildFinishedListener buildFinishedListener;
    private final BuildWorkExecutor buildExecuter;
    private final BuildScopeServices buildServices;
    private final List<?> servicesToStop;
    private final IncludedBuildControllers includedBuildControllers;
    private final GradleInternal gradle;
    private final ConfigurationCache configurationCache;
    private final SettingsPreparer settingsPreparer;
    private final TaskExecutionPreparer taskExecutionPreparer;
    private final BuildOptionBuildOperationProgressEventsEmitter buildOptionBuildOperationProgressEventsEmitter;

    private Stage stage;

    public DefaultGradleLauncher(GradleInternal gradle,
                                 ProjectsPreparer projectsPreparer,
                                 ExceptionAnalyser exceptionAnalyser,
                                 BuildListener buildListener,
                                 BuildCompletionListener buildCompletionListener,
                                 InternalBuildFinishedListener buildFinishedListener,
                                 BuildWorkExecutor buildExecuter,
                                 BuildScopeServices buildServices,
                                 List<?> servicesToStop,
                                 IncludedBuildControllers includedBuildControllers,
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
        this.includedBuildControllers = includedBuildControllers;
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
    public File getBuildRootDir() {
        return buildServices.get(BuildLayout.class).getRootDirectory();
    }

    @Override
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
        Preconditions.checkArgument(
            upTo != Stage.Finished,
            "Stage.Finished is not supported by doBuildStages."
        );
        try {
            if (stage == null && gradle.isRootBuild()) {
                buildOptionBuildOperationProgressEventsEmitter.emit(gradle.getStartParameter());
            }

            if (upTo == Stage.RunTasks && configurationCache.canLoad()) {
                doConfigurationCacheBuild();
            } else {
                doClassicBuildStages(upTo);
            }
        } catch (Throwable t) {
            finishBuild(upTo.getDisplayName(), t);
        }
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
        if (upTo == Stage.TaskGraph) {
            return;
        }
        configurationCache.save();
        runWork();
    }

    @SuppressWarnings("deprecation")
    private void doConfigurationCacheBuild() {
        buildListener.buildStarted(gradle);
        configurationCache.load();
        stage = Stage.TaskGraph;
        runWork();
    }

    private void finishBuild(String action, @Nullable Throwable stageFailure) {
        if (stage == Stage.Finished) {
            return;
        }

        RuntimeException reportableFailure = stageFailure == null ? null : exceptionAnalyser.transform(stageFailure);
        BuildResult buildResult = new BuildResult(action, gradle, reportableFailure);
        List<Throwable> failures = new ArrayList<>();
        includedBuildControllers.finishBuild(failures);
        try {
            buildListener.buildFinished(buildResult);
            buildFinishedListener.buildFinished((GradleInternal) buildResult.getGradle());
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

    @SuppressWarnings("deprecation")
    private void prepareSettings() {
        if (stage == null) {
            buildListener.buildStarted(gradle);

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
