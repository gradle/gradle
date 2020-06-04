/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.concurrent.Stoppable;

import java.io.File;

/**
 * This was the old Gradle embedding API (it used to be in the public `org.gradle` package). It is now internal and is due to be merged into {@link org.gradle.internal.invocation.BuildController} and {@link org.gradle.internal.build.BuildState}.
 */
public interface GradleLauncher extends Stoppable {

    GradleInternal getGradle();

    /**
     * Evaluates the settings for this build.
     *
     * @return The loaded settings instance.
     */
    SettingsInternal getLoadedSettings();

    /**
     * Configures the build.
     *
     * @return The configured Gradle build instance.
     */
    GradleInternal getConfiguredBuild();

    /**
     * The root directory of the build, never null.
     *
     * @see BuildLayout#getRootDirectory()
     */
    File getBuildRootDir();

    /**
     * Schedules the specified tasks for this build.
     */
    void scheduleTasks(final Iterable<String> tasks);

    /**
     * Executes the tasks scheduled for this build.
     *
     * @return The configured Gradle build instance.
     */
    GradleInternal executeTasks();

    /**
     * Stops task execution threads and calls the `buildFinished` listener event.
     */
    void finishBuild();

    /**
     * <p>Adds a listener to this build instance. Receives events for this build only.
     */
    void addListener(Object listener);
}
