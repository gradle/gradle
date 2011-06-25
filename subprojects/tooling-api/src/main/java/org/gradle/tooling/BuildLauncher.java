/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling;

import org.gradle.tooling.model.Task;

import java.io.OutputStream;

/**
 * <p>A {@code BuildLauncher} allows you to configure and execute a Gradle build.
 *
 * <p>You use a {@code BuildLauncher} as follows:
 *
 * <ul>
 *
 * <li>Create an instance of {@code BuildLauncher} by calling {@link org.gradle.tooling.ProjectConnection#newBuild()}.
 *
 * <li>Configure the launcher as appropriate.
 *
 * <li>Call either {@link #run()} or {@link #run(ResultHandler)} to execute the build.
 *
 * <li>Optionally, you can reuse the launcher to launcher additional builds.
 *
 * </ul>
 *
 * <p>Instances of {@code BuildLauncher} are not thread-safe.
 */
public interface BuildLauncher {
    /**
     * Sets the tasks to be executed.
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created.
     * @return this
     */
    BuildLauncher forTasks(String... tasks);

    /**
     * Sets the tasks to be executed. Note that the supplied tasks do not necessarily belong to the project which this launcher was created for.
     *
     * @param tasks The tasks to be executed.
     * @return this
     */
    BuildLauncher forTasks(Task... tasks);

    /**
     * Sets the tasks to be executed. Note that the supplied tasks do not necessarily belong to the project which this launcher was created for.
     *
     * @param tasks The tasks to be executed.
     * @return this
     */
    BuildLauncher forTasks(Iterable<? extends Task> tasks);

    /**
     * Sets the {@link OutputStream} that should receive standard output logging from this build. The default is to discard the output.
     *
     * @param outputStream The output stream.
     * @return this
     */
    BuildLauncher setStandardOutput(OutputStream outputStream);

    /**
     * Sets the {@link OutputStream} that should receive standard error logging from this build. The default is to discard the output.
     *
     * @param outputStream The output stream.
     * @return this
     */
    BuildLauncher setStandardError(OutputStream outputStream);

    /**
     * Adds a progress listener which will receive progress events as the build executes.
     *
     * @param listener The listener
     * @return this
     */
    BuildLauncher addProgressListener(ProgressListener listener);

    /**
     * Execute the build, blocking until it is complete.
     *
     * @throws UnsupportedVersionException When the target Gradle version does not support the features required for this build.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    void run() throws GradleConnectionException;

    /**
     * Launchers the build. This method returns immediately, and the result is later passed to the given handler.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    void run(ResultHandler<? super Void> handler) throws IllegalStateException;
}
