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

import org.gradle.api.Incubating;
import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException;
import org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException;
import org.gradle.tooling.model.Launchable;
import org.gradle.tooling.model.Task;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A {@code BuildLauncher} allows you to configure and execute a Gradle build.
 * <p>
 * Instances of {@code BuildLauncher} are not thread-safe. You use a {@code BuildLauncher} as follows:
 *
 * <ul>
 * <li>Create an instance of {@code BuildLauncher} by calling {@link org.gradle.tooling.ProjectConnection#newBuild()}.
 * <li>Configure the launcher as appropriate.
 * <li>Call either {@link #run()} or {@link #run(ResultHandler)} to execute the build.
 * <li>Optionally, you can reuse the launcher to launch additional builds.
 * </ul>
 *
 * Example:
 * <pre autoTested=''>
 * ProjectConnection connection = GradleConnector.newConnector()
 *    .forProjectDirectory(new File("someFolder"))
 *    .connect();
 *
 * try {
 *    BuildLauncher build = connection.newBuild();
 *
 *    //select tasks to run:
 *    build.forTasks("clean", "test");
 *
 *    //include some build arguments:
 *    build.withArguments("--no-search-upward", "-i", "--project-dir", "someProjectDir");
 *
 *    //configure the standard input:
 *    build.setStandardInput(new ByteArrayInputStream("consume this!".getBytes()));
 *
 *    //in case you want the build to use java different than default:
 *    build.setJavaHome(new File("/path/to/java"));
 *
 *    //if your build needs crazy amounts of memory:
 *    build.setJvmArguments("-Xmx2048m", "-XX:MaxPermSize=512m");
 *
 *    //if you want to listen to the progress events:
 *    ProgressListener listener = null; // use your implementation
 *    build.addProgressListener(listener);
 *
 *    //kick the build off:
 *    build.run();
 * } finally {
 *    connection.close();
 * }
 * </pre>
 *
 * @since 1.0-milestone-3
 */
public interface BuildLauncher extends LongRunningOperation {
    /**
     * Sets the tasks to be executed. If no tasks are specified, the project's default tasks are executed.
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created.
     * @return this
     * @since 1.0-milestone-3
     */
    BuildLauncher forTasks(String... tasks);

    /**
     * Sets the tasks to be executed. If no tasks are specified, the project's default tasks are executed.
     *
     * <p>Note that the supplied tasks do not necessarily need to belong to the project which this launcher was created for.
     *
     * @param tasks The tasks to be executed.
     * @return this
     * @since 1.0-milestone-3
     */
    BuildLauncher forTasks(Task... tasks);

    /**
     * Sets the tasks to be executed. If no tasks are specified, the project's default tasks are executed.
     *
     * <p>Note that the supplied tasks do not necessarily need to belong to the project which this launcher was created for.
     *
     * @param tasks The tasks to be executed.
     * @return this
     * @since 1.0-milestone-3
     */
    BuildLauncher forTasks(Iterable<? extends Task> tasks);

    /**
     * Sets the launchables to execute. If no entries are specified, the project's default tasks are executed.
     *
     * @param launchables The launchables for this build.
     * @return this
     * @since 1.12
     */
    @Incubating
    BuildLauncher forLaunchables(Launchable... launchables);

    /**
     * Sets the launchables to execute. If no entries are specified, the project's default tasks are executed.
     *
     * @param launchables The launchables for this build.
     * @return this
     * @since 1.12
     */
    @Incubating
    BuildLauncher forLaunchables(Iterable<? extends Launchable> launchables);

    /**
     * {@inheritDoc}
     * @since 1.0
     */
    BuildLauncher withArguments(String ... arguments);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    BuildLauncher setStandardOutput(OutputStream outputStream);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    BuildLauncher setStandardError(OutputStream outputStream);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-7
     */
    BuildLauncher setStandardInput(InputStream inputStream);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-8
     */
    BuildLauncher setJavaHome(File javaHome);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-9
     */
    BuildLauncher setJvmArguments(String... jvmArguments);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    BuildLauncher addProgressListener(ProgressListener listener);

    /**
     * Executes the build, blocking until it is complete.
     *
     * @throws UnsupportedVersionException When the target Gradle version does not support build execution.
     * @throws UnsupportedOperationConfigurationException
     *          When the target Gradle version does not support some requested configuration option such as
     *          {@link #setStandardInput(java.io.InputStream)}, {@link #setJavaHome(java.io.File)},
     *          {@link #setJvmArguments(String...)}.
     * @throws UnsupportedBuildArgumentException When there is a problem with build arguments provided by {@link #withArguments(String...)}.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.0-milestone-3
     */
    void run() throws GradleConnectionException, UnsupportedBuildArgumentException, IllegalStateException,
            BuildException, UnsupportedVersionException, UnsupportedOperationConfigurationException;

    /**
     * Launches the build. This method returns immediately, and the result is later passed to the given handler.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)}
     * method is called with the appropriate exception. See {@link #run()} for a description of the various exceptions that the operation may fail with.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.0-milestone-3
     */
    void run(ResultHandler<? super Void> handler) throws IllegalStateException;
}
