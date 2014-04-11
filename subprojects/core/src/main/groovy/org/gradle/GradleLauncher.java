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
package org.gradle;

import org.gradle.api.logging.StandardOutputListener;

/**
 * <p>Executes a Gradle build.
 *
 * <p>{@code GradleLauncher} is deprecated. It has been replaced by the Tooling API.
 * If you're interested in embedding Gradle you should read the user guide chapter on embedding Gradle.
 * The main entry point to the Tooling API (and embedding Gradle) is {@code org.gradle.tooling.GradleConnector}.
 *
 * @deprecated Use the tooling API instead.
 */
@Deprecated
public abstract class GradleLauncher {

    /**
     * <p>Executes the build for this {@code GradleLauncher} instance and returns the result. Note that when the build
     * fails, the exception is available using {@link org.gradle.BuildResult#getFailure()}.</p>
     *
     * @return The result. Never returns null.
     */
    public abstract BuildResult run();

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible via
     * the {@link org.gradle.api.invocation.Gradle#getRootProject()} object.
     *
     * @return The result. Never returns null.
     */
    public abstract BuildResult getBuildAnalysis();

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible via
     * the {@link org.gradle.api.invocation.Gradle#getRootProject()} object. Fills the execution plan without running
     * the build. The tasks to be executed tasks are available via {@link org.gradle.api.invocation.Gradle#getTaskGraph()}.
     *
     * @return The result. Never returns null.
     */
    public abstract BuildResult getBuildAndRunAnalysis();

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the execution of
     * the build. See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for supported listener types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    public abstract void addListener(Object listener);

    /**
     * Use the given listener. See {@link org.gradle.api.invocation.Gradle#useLogger(Object)} for details.
     *
     * @param logger The logger to use.
     */
    public abstract void useLogger(Object logger);

    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to
     * standard output by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    public abstract void addStandardOutputListener(StandardOutputListener listener);

    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to standard error by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    public abstract void addStandardErrorListener(StandardOutputListener listener);

    /**
     * Returns the {@link StartParameter} used by this build instance.
     * @return The parameter. Never null.
     */
    public abstract StartParameter getStartParameter();
}
