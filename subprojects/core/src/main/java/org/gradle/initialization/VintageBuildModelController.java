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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.configuration.ProjectsPreparer;
import org.gradle.internal.build.BuildModelController;
import org.gradle.internal.build.StateTransitionController;

public class VintageBuildModelController implements BuildModelController {
    private enum Stage implements StateTransitionController.State {
        Created, LoadSettings, Configure, ScheduleTasks, TaskGraph
    }

    private final ProjectsPreparer projectsPreparer;
    private final GradleInternal gradle;
    private final TaskSchedulingPreparer taskGraphPreparer;
    private final SettingsPreparer settingsPreparer;
    private final TaskExecutionPreparer taskExecutionPreparer;
    private final StateTransitionController<Stage> controller = new StateTransitionController<>(Stage.Created);

    public VintageBuildModelController(
        GradleInternal gradle,
        ProjectsPreparer projectsPreparer,
        TaskSchedulingPreparer taskSchedulingPreparer,
        SettingsPreparer settingsPreparer,
        TaskExecutionPreparer taskExecutionPreparer
    ) {
        this.gradle = gradle;
        this.projectsPreparer = projectsPreparer;
        this.taskGraphPreparer = taskSchedulingPreparer;
        this.settingsPreparer = settingsPreparer;
        this.taskExecutionPreparer = taskExecutionPreparer;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        doBuildStages(Stage.LoadSettings);
        return gradle.getSettings();
    }

    @Override
    public GradleInternal getConfiguredModel() {
        doBuildStages(Stage.Configure);
        return gradle;
    }

    @Override
    public void prepareToScheduleTasks() {
        doBuildStages(Stage.ScheduleTasks);
    }

    @Override
    public void scheduleRequestedTasks() {
        doBuildStages(Stage.TaskGraph);
    }

    private void doBuildStages(Stage upTo) {
        prepareSettings();
        if (upTo == Stage.LoadSettings) {
            return;
        }
        prepareProjects();
        if (upTo == Stage.Configure) {
            return;
        }
        prepareTaskGraph();
        if (upTo == Stage.ScheduleTasks) {
            return;
        }
        prepareTaskExecution();
    }

    private void prepareSettings() {
        controller.transitionIfNotPreviously(Stage.Created, Stage.LoadSettings, () -> settingsPreparer.prepareSettings(gradle));
    }

    private void prepareProjects() {
        controller.transitionIfNotPreviously(Stage.LoadSettings, Stage.Configure, () -> projectsPreparer.prepareProjects(gradle));
    }

    private void prepareTaskGraph() {
        controller.transitionIfNotPreviously(Stage.Configure, Stage.ScheduleTasks, () -> taskGraphPreparer.prepareForTaskScheduling(gradle));
    }

    private void prepareTaskExecution() {
        controller.transitionIfNotPreviously(Stage.ScheduleTasks, Stage.TaskGraph, () -> taskExecutionPreparer.prepareForTaskExecution(gradle));
    }
}
