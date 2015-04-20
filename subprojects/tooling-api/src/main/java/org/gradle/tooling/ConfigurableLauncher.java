/*
 * Copyright 2015 the original author or authors.
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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A {@code ConfigurableLauncher} defines the methods shared by several launchers.
 *
 * @since 2.5
 */
public interface ConfigurableLauncher extends LongRunningOperation {
    /**
     * {@inheritDoc}
     * @since 1.0
     */
    ConfigurableLauncher withArguments(String... arguments);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    ConfigurableLauncher setStandardOutput(OutputStream outputStream);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    ConfigurableLauncher setStandardError(OutputStream outputStream);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    @Incubating
    ConfigurableLauncher setColorOutput(boolean colorOutput);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-7
     */
    ConfigurableLauncher setStandardInput(InputStream inputStream);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-8
     */
    ConfigurableLauncher setJavaHome(File javaHome);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-9
     */
    ConfigurableLauncher setJvmArguments(String... jvmArguments);

    /**
     * {@inheritDoc}
     * @since 1.0-milestone-3
     */
    ConfigurableLauncher addProgressListener(ProgressListener listener);

    /**
     * {@inheritDoc}
     * @since 2.4
     */
    @Incubating
    ConfigurableLauncher addTestProgressListener(TestProgressListener listener);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    @Incubating
    ConfigurableLauncher withCancellationToken(CancellationToken cancellationToken);

    /**
     * Executes the build, blocking until it is complete.
     *
     * @throws UnsupportedVersionException When the target Gradle version does not support build execution.
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
     *          When the target Gradle version does not support some requested configuration option such as
     *          {@link #setStandardInput(InputStream)}, {@link #setJavaHome(File)},
     *          {@link #setJvmArguments(String...)}.
     * @throws org.gradle.tooling.exceptions.UnsupportedBuildArgumentException When there is a problem with build arguments provided by {@link #withArguments(String...)}.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws BuildCancelledException When the operation was cancelled before it completed successfully.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 2.5
     */
    void run() throws GradleConnectionException, IllegalStateException;

    /**
     * Launches the build. This method returns immediately, and the result is later passed to the given handler.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)}
     * method is called with the appropriate exception. See {@link #run()} for a description of the various exceptions that the operation may fail with.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 2.5
     */
    void run(ResultHandler<? super Void> handler) throws IllegalStateException;
}
