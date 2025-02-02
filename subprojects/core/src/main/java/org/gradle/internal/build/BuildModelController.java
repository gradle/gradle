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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.plan.ExecutionPlan;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

/**
 * Transitions the model of an individual build in the build tree through its lifecycle.
 * See also {@link BuildTreeLifecycleController} and {@link BuildLifecycleController}.
 */
@ServiceScope(Scope.Build.class)
public interface BuildModelController {
    /**
     * Ensures the build's settings object has been configured.
     *
     * @return The settings.
     */
    SettingsInternal getLoadedSettings();

    /**
     * Ensures the build's projects have been configured.
     *
     * @return The gradle instance.
     */
    GradleInternal getConfiguredModel();

    /**
     * Does whatever work is required to allow tasks to be scheduled. May configure the build, if required.
     */
    void prepareToScheduleTasks();

    /**
     * Schedules the user requested tasks for this build into the given plan.
     */
    void scheduleRequestedTasks(@Nullable EntryTaskSelector selector, ExecutionPlan plan);
}
