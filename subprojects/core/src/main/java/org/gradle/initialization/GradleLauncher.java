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

import org.gradle.BuildResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.internal.concurrent.Stoppable;

/**
 * This was the old Gradle embedding API (it used to be in the public `org.gradle` package). It is now internal and is due to be merged into {@link org.gradle.internal.invocation.BuildController}.
 */
public interface GradleLauncher extends Stoppable {

    GradleInternal getGradle();

    /**
     * Evaluates the settings for this build.
     *
     * @return The loaded settings instance.
     * @throws ReportedException On build failure. The failure will have been logged.
     */
    SettingsInternal getLoadedSettings();

    /**
     * Configures the build.
     * This is different from {@link #getBuildAnalysis()} in that it is not considered a complete build execution,
     * and the `buildFinished` event will not be fired automatically.
     *
     * @return The configured Gradle build instance.
     * @throws ReportedException On build failure. The failure will have been logged.
     */
    GradleInternal getConfiguredBuild();

    /**
     * Schedules the specified tasks for this build.
     * @throws ReportedException On build failure. The failure will have been logged.
     */
    void scheduleTasks(final Iterable<String> tasks);

    /**
     * <p>Executes the build for this {@code GradleLauncher} instance and returns the result.</p>
     * This method performs a complete build execution, firing the `buildFinished` event on completion.
     *
     * @return The result. Never returns null.
     * @throws ReportedException On build failure. The failure will have been logged.
     */
    BuildResult run() throws ReportedException;

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible via the {@link org.gradle.api.invocation.Gradle#getRootProject()} object.
     * This method performs a complete build execution, firing the `buildFinished` event on completion.
     *
     * @return The result. Never returns null.
     * @throws ReportedException On build failure. The failure will have been logged.
     */
    BuildResult getBuildAnalysis() throws ReportedException;

    /**
     * <p>Adds a listener to this build instance. Receives events for this build only.
     */
    void addListener(Object listener);
}
