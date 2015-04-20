/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.tooling.events.test.TestProgressListener;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Used to execute a {@link BuildAction} in the build process.
 *
 * @param <T> The type of result produced by this executer.
 * @since 1.8
 */
@Incubating
public interface BuildActionExecuter<T> extends LongRunningOperation {

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    BuildActionExecuter<T> withArguments(String... arguments);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    BuildActionExecuter<T> setStandardOutput(OutputStream outputStream);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    BuildActionExecuter<T> setStandardError(OutputStream outputStream);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    @Incubating
    BuildActionExecuter<T> setColorOutput(boolean colorOutput);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    BuildActionExecuter<T> setStandardInput(InputStream inputStream);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    BuildActionExecuter<T> setJavaHome(File javaHome);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    BuildActionExecuter<T> setJvmArguments(String... jvmArguments);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    BuildActionExecuter<T> addProgressListener(ProgressListener listener);

    /**
     * {@inheritDoc}
     * @since 2.4
     */
    @Incubating
    BuildActionExecuter<T> addTestProgressListener(TestProgressListener listener);

    /**
     * {@inheritDoc}
     * @since 2.3
     */
    @Incubating
    BuildActionExecuter<T> withCancellationToken(CancellationToken cancellationToken);

    /**
     * Runs the action, blocking until its result is available.
     *
     * @throws UnsupportedVersionException When the target Gradle version does not support build action execution.
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
     *          When the target Gradle version does not support some requested configuration option such as
     *          {@link #setStandardInput(java.io.InputStream)}, {@link #setJavaHome(java.io.File)},
     *          {@link #setJvmArguments(String...)}.
     * @throws org.gradle.tooling.exceptions.UnsupportedBuildArgumentException When there is a problem with build arguments provided by {@link #withArguments(String...)}.
     * @throws BuildActionFailureException When the build action fails with an exception.
     * @throws BuildCancelledException When the operation was cancelled before it completed successfully.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.8
     */
    T run() throws GradleConnectionException, IllegalStateException;

    /**
     * Starts executing the action, passing the result to the given handler when complete. This method returns immediately, and the result is later passed to the given handler's {@link
     * ResultHandler#onComplete(Object)} method.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)} method is called with the appropriate exception. See
     * {@link #run()} for a description of the various exceptions that the operation may fail with.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.8
     */
    void run(ResultHandler<? super T> handler) throws IllegalStateException;
}
