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
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.internal.concurrent.Stoppable;

/**
 * This was the old Gradle embedding API (it used to be in the public `org.gradle` package). It is now internal and is due to be merged into {@link org.gradle.internal.invocation.BuildController}.
 */
public interface GradleLauncher extends Stoppable {

    GradleInternal getGradle();
    SettingsInternal getSettings();

    /**
     * <p>Executes the build for this {@code GradleLauncher} instance and returns the result.</p>
     *
     * @return The result. Never returns null.
     * @throws ReportedException On build failure. The failure will have been logged.
     */
    BuildResult run() throws ReportedException;

    /**
     * Evaluates the settings for this build. The information about available tasks and projects is accessible via the {@link org.gradle.api.invocation.Gradle#getRootProject()} object.
     *
     * @return The result. Never returns null.
     * @throws ReportedException On build failure. The failure will have been logged.
     */
    BuildResult load() throws ReportedException;

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible via the {@link org.gradle.api.invocation.Gradle#getRootProject()} object.
     *
     * @return The result. Never returns null.
     * @throws ReportedException On build failure. The failure will have been logged.
     */
    BuildResult getBuildAnalysis() throws ReportedException;

    /**
     * <p>Adds a listener to this build instance. Receives events for this build only.
     */
    void addListener(Object listener);

    /**
     * <p>Adds a nested listener to this build instance. Receives events for this build and all nested builds.
     */
    void addNestedListener(Object listener);

    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to standard output by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    void addStandardOutputListener(StandardOutputListener listener);

    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to standard error by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    void addStandardErrorListener(StandardOutputListener listener);
}
