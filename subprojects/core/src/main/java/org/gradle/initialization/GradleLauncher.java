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
import org.gradle.internal.concurrent.Stoppable;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * This was the old Gradle embedding API (it used to be in the public `org.gradle` package). It is now internal and is due to be merged into {@link org.gradle.internal.invocation.BuildController} and {@link org.gradle.internal.build.BuildState}.
 */
public interface GradleLauncher extends Stoppable {

    GradleInternal getGradle();

    /**
     * Evaluates the settings for this build, if not already available.
     *
     * @return The loaded settings instance.
     */
    SettingsInternal getLoadedSettings();

    /**
     * Configures the build, if not already available.
     *
     * @return The configured Gradle build instance.
     */
    GradleInternal getConfiguredBuild();

    /**
     * Schedules the specified tasks for this build.
     */
    void scheduleTasks(final Iterable<String> tasks);

    /**
     * Schedule requested tasks.
     */
    void scheduleRequestedTasks();

    /**
     * Executes the tasks scheduled for this build.
     */
    void executeTasks();

    /**
     * Stops task execution threads and calls the `buildFinished` hooks and other user code clean up.
     * Failures to finish the build are passed to the given consumer rather than thrown.
     *
     * @param failure The build failure that should be reported to the buildFinished hooks. When null, this launcher may use whatever failure it has already collected.
     */
    void finishBuild(@Nullable Throwable failure, Consumer<? super Throwable> collector);

    /**
     * <p>Adds a listener to this build instance. Receives events for this build only.
     */
    void addListener(Object listener);
}
