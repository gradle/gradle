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

import org.gradle.tooling.exceptions.UnsupportedBuildArgumentException;
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
 */
public interface BuildLauncher extends LongRunningOperation {
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
     * {@inheritDoc}
     */
    BuildLauncher withArguments(String ... arguments);

    /**
     * {@inheritDoc}
     */
    BuildLauncher setStandardOutput(OutputStream outputStream);

    /**
     * {@inheritDoc}
     */
    BuildLauncher setStandardError(OutputStream outputStream);

    /**
     * {@inheritDoc}
     */
    BuildLauncher setStandardInput(InputStream inputStream);

    /**
     * {@inheritDoc}
     */
    BuildLauncher setJavaHome(File javaHome);

    /**
     * {@inheritDoc}
     */
    BuildLauncher setJvmArguments(String... jvmArguments);

    /**
     * {@inheritDoc}
     */
    BuildLauncher addProgressListener(ProgressListener listener);

    /**
     * Executes the build, blocking until it is complete.
     *
     * @throws UnsupportedVersionException When the target Gradle version does not support the features required for this build.
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
     *          when you have configured the long running operation with a settings
     *          like: {@link #setStandardInput(java.io.InputStream)}, {@link #setJavaHome(java.io.File)},
     *          {@link #setJvmArguments(String...)} but those settings are not supported on the target Gradle.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws UnsupportedBuildArgumentException When there is a problem with build arguments provided by {@link #withArguments(String...)}
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    void run() throws GradleConnectionException, UnsupportedBuildArgumentException, IllegalStateException,
            BuildException, UnsupportedVersionException;

    /**
     * Launches the build. This method returns immediately, and the result is later passed to the given handler.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    void run(ResultHandler<? super Void> handler) throws IllegalStateException;
}
