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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Offers ways to communicate both ways with a gradle operation, be it building a model or running tasks.
 * <p>
 * Enables tracking progress via listeners that will receive events from the gradle operation.
 * <p>
 * Allows providing standard output streams that will receive output if the gradle operation writes to standard streams.
 * <p>
 * Allows providing standard input that can be consumed by the gradle operation (useful for interactive builds).
 */
public interface LongRunningOperation {

    /**
     * Sets the {@link java.io.OutputStream} which should receive standard output logging generated while running the operation.
     * The default is to discard the output.
     *
     * @param outputStream The output stream.
     * @return this
     */
    LongRunningOperation setStandardOutput(OutputStream outputStream);

    /**
     * Sets the {@link OutputStream} which should receive standard error logging generated while running the operation.
     * The default is to discard the output.
     *
     * @param outputStream The output stream.
     * @return this
     */
    LongRunningOperation setStandardError(OutputStream outputStream);

    /**
     * If the target gradle version supports it you can use this setting
     * to set the standard {@link java.io.InputStream} that will be used by builds.
     * Useful when the tooling api drives interactive builds.
     * <p>
     * If the target gradle version does not support it the long running operation will fail eagerly with
     * {@link org.gradle.tooling.model.UnsupportedMethodException} when the operation is started.
     * <p>
     * If not configured or null passed the dummy input stream with zero bytes is used to avoid the build hanging problems.
     *
     * @param inputStream The input stream
     * @return this
     * @since 1.0-milestone-8
     */
    LongRunningOperation setStandardInput(InputStream inputStream);

    /**
     * If the target gradle version supports it you can use this setting
     * to specify the java home directory to use for the long running operation.
     * <p>
     * If the target gradle version does not support it the long running operation will fail eagerly with
     * {@link org.gradle.tooling.model.UnsupportedMethodException} when the operation is started.
     * <p>
     * {@link org.gradle.tooling.model.build.BuildEnvironment} model contains information such as java or gradle environment.
     * If you want to get hold of this information you can ask tooling API to build this model.
     * <p>
     * If not configured or null passed the sensible default will be used.
     *
     * @param javaHome to use for the gradle process
     * @return this
     * @since 1.0-milestone-8
     * @throws IllegalArgumentException when supplied javaHome is not a valid folder.
     *          Also when javaHome does not seem to be a valid jdk or jre folder.
     */
    LongRunningOperation setJavaHome(File javaHome) throws IllegalArgumentException;

    /**
     * If the target gradle version supports it you can use this setting
     * to specify the java vm arguments to use for the long running operation.
     * <p>
     * If the target gradle version does not support it the long running operation will fail eagerly with
     * {@link org.gradle.tooling.model.UnsupportedMethodException} when the operation is started.
     * <p>
     * {@link org.gradle.tooling.model.build.BuildEnvironment} model contains information such as java or gradle environment.
     * If you want to get hold of this information you can ask tooling API to build this model.
     * <p>
     * If not configured or null passed the sensible default will be used.
     *
     * @param jvmArguments to use for the gradle process
     * @return this
     * @since 1.0-milestone-8
     */
    LongRunningOperation setJvmArguments(String... jvmArguments);

    /**
     * Adds a progress listener which will receive progress events as the operation runs.
     *
     * @param listener The listener
     * @return this
     */
    LongRunningOperation addProgressListener(ProgressListener listener);

}
