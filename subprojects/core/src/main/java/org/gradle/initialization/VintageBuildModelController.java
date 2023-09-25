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
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.internal.Describables;
import org.gradle.internal.build.BuildModelController;
import org.gradle.internal.model.StateTransitionController;
import org.gradle.internal.model.StateTransitionControllerFactory;

import javax.annotation.Nullable;

public class VintageBuildModelController implements BuildModelController {
    private enum Stage implements StateTransitionController.State {
        Created, SettingsLoaded, Configured
    }

    private final ProjectsPreparer projectsPreparer;
    private final GradleInternal gradle;
    private final SettingsPreparer settingsPreparer;
    private final TaskExecutionPreparer taskExecutionPreparer;
    private final StateTransitionController<Stage> state;

    public VintageBuildModelController(
        GradleInternal gradle,
        ProjectsPreparer projectsPreparer,
        SettingsPreparer settingsPreparer,
        TaskExecutionPreparer taskExecutionPreparer,
        StateTransitionControllerFactory controllerFactory
    ) {
        this.gradle = gradle;
        this.projectsPreparer = projectsPreparer;
        this.settingsPreparer = settingsPreparer;
        this.taskExecutionPreparer = taskExecutionPreparer;
        this.state = controllerFactory.newController(Describables.of("vintage state of", gradle.getOwner().getDisplayName()), Stage.Created);
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        prepareSettings();
        return gradle.getSettings();
    }

    @Override
    public GradleInternal getConfiguredModel() {
        prepareSettings();
        prepareProjects();
        return gradle;
    }

    @Override
    public void prepareToScheduleTasks() {
        prepareSettings();
        prepareProjects();
    }

    @Override
    public void scheduleRequestedTasks(@Nullable EntryTaskSelector selector, ExecutionPlan plan, boolean isModelBuildingRequested) {
        state.inState(Stage.Configured, () -> taskExecutionPreparer.scheduleRequestedTasks(gradle, selector, plan, isModelBuildingRequested));
    }

    private void prepareSettings() {
        state.transitionIfNotPreviously(Stage.Created, Stage.SettingsLoaded, () -> settingsPreparer.prepareSettings(gradle));
    }

    private void prepareProjects() {
        state.transitionIfNotPreviously(Stage.SettingsLoaded, Stage.Configured, () -> projectsPreparer.prepareProjects(gradle));
    }

}
