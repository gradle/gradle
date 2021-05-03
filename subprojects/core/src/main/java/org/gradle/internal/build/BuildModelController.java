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
import org.gradle.internal.buildtree.BuildTreeLifecycleController;

/**
 * Transitions the model of an individual build in the build tree through its lifecycle.
 * See also {@link BuildTreeLifecycleController} and {@link BuildLifecycleController}.
 */
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
     * Schedules the given tasks. May configure the build, if required.
     */
    void scheduleTasks(Iterable<String> tasks);

    /**
     * Schedules the user requested tasks for this build. May configure the build, if required.
     */
    void scheduleRequestedTasks();
}
