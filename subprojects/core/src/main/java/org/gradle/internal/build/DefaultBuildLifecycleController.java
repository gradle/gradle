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
import org.gradle.initialization.BuildCompletionListener;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.scopes.BuildScopeServices;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefaultBuildLifecycleController implements BuildLifecycleController {
    private enum Stage {
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

    private Stage stage = Stage.Created;
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
        return gradle;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return withModel(BuildModelController::getLoadedSettings);
    }

    @Override
    public GradleInternal getConfiguredBuild() {
        return withModel(BuildModelController::getConfiguredModel);
    }

    @Override
    public void scheduleTasks(final Iterable<String> taskPaths) {
        withModel(buildModelController -> {
            stage = Stage.TaskGraph;
            buildModelController.scheduleTasks(taskPaths);
            return null;
        });
    }

    @Override
    public void scheduleRequestedTasks() {
        withModel(buildModelController -> {
            stage = Stage.TaskGraph;
            buildModelController.scheduleRequestedTasks();
            return null;
        });
    }

    @Override
    public void executeTasks() {
        withModel(buildModelController -> {
            runWork();
            return null;
        });
    }

    private <T> T withModel(Function<BuildModelController, T> action) {
        if (stageFailure != null) {
            throw new IllegalStateException("Cannot do further work as this build has failed.", stageFailure);
        }
        try {
            try {
                return action.apply(modelController);
            } finally {
                if (stage == Stage.Created) {
                    stage = Stage.Configure;
                }
            }
        } catch (Throwable t) {
            stageFailure = exceptionAnalyser.transform(t);
            throw stageFailure;
        }
    }

    @Override
    public void finishBuild(@Nullable Throwable failure, Consumer<? super Throwable> collector) {
        if (stage == Stage.Created || stage == Stage.Finished) {
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

    private void runWork() {
        List<Throwable> taskFailures = new ArrayList<>();
        buildExecuter.execute(gradle, taskFailures);
        if (!taskFailures.isEmpty()) {
            throw new MultipleBuildFailures(taskFailures);
        }
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
        if (stage != Stage.Created && stage != Stage.Finished) {
            throw new IllegalStateException("This build has not been finished.");
        }
        try {
            CompositeStoppable.stoppable(buildServices).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }
}
