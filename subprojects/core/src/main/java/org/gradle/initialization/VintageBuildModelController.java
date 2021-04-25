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

public class VintageBuildModelController implements BuildModelController {
    private enum Stage {
        Created, LoadSettings, Configure, TaskGraph
    }

    private final ProjectsPreparer projectsPreparer;
    private final GradleInternal gradle;
    private final SettingsPreparer settingsPreparer;
    private final TaskExecutionPreparer taskExecutionPreparer;

    private Stage stage = Stage.Created;

    public VintageBuildModelController(
        GradleInternal gradle,
        ProjectsPreparer projectsPreparer,
        SettingsPreparer settingsPreparer,
        TaskExecutionPreparer taskExecutionPreparer
    ) {
        this.gradle = gradle;
        this.projectsPreparer = projectsPreparer;
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
        prepareTaskExecution();
    }

    private void prepareSettings() {
        if (stage == Stage.Created) {
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
        boolean added = getConfiguredModel().getStartParameter().addTaskNames(taskPaths);
        if (!added) {
            return;
        }
        // Force back to configure so that task graph will get reevaluated
        stage = Stage.Configure;
        doBuildStages(Stage.TaskGraph);
    }
}
