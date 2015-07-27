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
import org.gradle.api.Nullable;
import org.gradle.tooling.events.OperationType;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * Offers ways to communicate both ways with a Gradle operation, be it building a model or running tasks.
 * <p>
 * Enables tracking progress via listeners that will receive events from the Gradle operation.
 * <p>
 * Allows providing standard output streams that will receive output if the Gradle operation writes to standard streams.
 * <p>
 * Allows providing standard input that can be consumed by the gradle operation (useful for interactive builds).
 * <p>
 * Enables configuring the build run / model request with options like the Java home or JVM arguments.
 * Those settings might not be supported by the target Gradle version. Refer to Javadoc for those methods
 * to understand what kind of exception throw and when is it thrown.
 *
 * @since 1.0-milestone-7
 */
public interface LongRunningOperation {

    /**
     * Sets the {@link java.io.OutputStream} which should receive standard output logging generated while running the operation.
     * The default is to discard the output.
     *
     * @param outputStream The output stream. The system default character encoding will be used to encode characters written to this stream.
     * @return this
     * @since 1.0-milestone-7
     */
    LongRunningOperation setStandardOutput(OutputStream outputStream);

    /**
     * Sets the {@link OutputStream} which should receive standard error logging generated while running the operation.
     * The default is to discard the output.
     *
     * @param outputStream The output stream. The system default character encoding will be used to encode characters written to this stream.
     * @return this
     * @since 1.0-milestone-7
     */
    LongRunningOperation setStandardError(OutputStream outputStream);

    /**
     * Specifies whether to generate colored (ANSI encoded) output for logging. The default is to not generate color output.
     *
     * <p>Supported by Gradle 2.3 or later. Ignored for older versions.</p>
     *
     * @param colorOutput {@code true} to request color output (using ANSI encoding).
     * @return this
     * @since 2.3
     */
    @Incubating
    LongRunningOperation setColorOutput(boolean colorOutput);

    /**
     * Sets the {@link java.io.InputStream} that will be used as standard input for this operation.
     * Defaults to an empty input stream.
     *
     * @param inputStream The input stream
     * @return this
     * @since 1.0-milestone-8
     */
    LongRunningOperation setStandardInput(InputStream inputStream);

    /**
     * Specifies the Java home directory to use for this operation.
     * <p>
     * {@link org.gradle.tooling.model.build.BuildEnvironment} model contains information such as Java or Gradle environment.
     * If you want to get hold of this information you can ask tooling API to build this model.
     * <p>
     * If not configured or null is passed, then the sensible default will be used.
     *
     * @param javaHome to use for the Gradle process
     * @return this
     * @throws IllegalArgumentException when supplied javaHome is not a valid folder.
     * @since 1.0-milestone-8
     */
    LongRunningOperation setJavaHome(@Nullable File javaHome) throws IllegalArgumentException;

    /**
     * Specifies the Java VM arguments to use for this operation.
     * <p>
     * {@link org.gradle.tooling.model.build.BuildEnvironment} model contains information such as Java or Gradle environment.
     * If you want to get hold of this information you can ask tooling API to build this model.
     * <p>
     * If not configured, null, or an empty array is passed, then the reasonable default will be used.
     *
     * @param jvmArguments to use for the Gradle process
     * @return this
     * @since 1.0-milestone-8
     */
    LongRunningOperation setJvmArguments(@Nullable String... jvmArguments);

    /**
     * Specifies the Java VM arguments to use for this operation.
     * <p>
     * {@link org.gradle.tooling.model.build.BuildEnvironment} model contains information such as Java or Gradle environment.
     * If you want to get hold of this information you can ask tooling API to build this model.
     * <p>
     * If not configured, null, or an empty list is passed, then the reasonable default will be used.
     *
     * @param jvmArguments to use for the Gradle process
     * @return this
     * @since 2.6
     */
    LongRunningOperation setJvmArguments(@Nullable Iterable<String> jvmArguments);

    /**
     * Specify the command line build arguments. Useful mostly for running tasks via {@link BuildLauncher}.
     * <p>
     * Be aware that not all of the Gradle command line options are supported!
     * Only the build arguments that configure the build execution are supported.
     * They are modelled in the Gradle API via {@link org.gradle.StartParameter}.
     * Examples of supported build arguments: '--info', '-u', '-p'.
     * The command line instructions that are actually separate commands (like '-?' and '-v') are not supported.
     * Some other instructions like '--daemon' are also not supported - the tooling API always runs with the daemon.
     * <p>
     * If an unknown or unsupported command line option is specified, {@link org.gradle.tooling.exceptions.UnsupportedBuildArgumentException}
     * will be thrown at the time the operation is executed via {@link BuildLauncher#run()} or {@link ModelBuilder#get()}.
     * <p>
     * For the list of all Gradle command line options please refer to the user guide
     * or take a look at the output of the 'gradle -?' command. Majority of arguments modeled by
     * {@link org.gradle.StartParameter} are supported.
     * <p>
     * The arguments can potentially override some other settings you have configured.
     * For example, the project directory or Gradle user home directory that are configured
     * in the {@link GradleConnector}.
     * Also, the task names configured by {@link BuildLauncher#forTasks(String...)} can be overridden
     * if you happen to specify other tasks via the build arguments.
     * <p>
     * See the example in the docs for {@link BuildLauncher}
     *
     * If not configured, null, or an empty array is passed, then the reasonable default will be used.
     *
     * <p>Requires Gradle 1.0 or later.</p>
     *
     * @param arguments Gradle command line arguments
     * @return this
     * @since 1.0
     */
    LongRunningOperation withArguments(@Nullable String... arguments);

    /**
     * Specify the command line build arguments. Useful mostly for running tasks via {@link BuildLauncher}.
     * <p>
     * If not configured, null, or an empty list is passed, then the reasonable default will be used.
     *
     * <p>Requires Gradle 1.0 or later.</p>
     *
     * @param arguments Gradle command line arguments
     * @return this
     * @since 2.6
     */
    LongRunningOperation withArguments(@Nullable Iterable<String> arguments);

    /**
     * Adds a progress listener which will receive progress events as the operation runs.
     *
     * <p>This method is intended to be replaced by {@link #addProgressListener(org.gradle.tooling.events.ProgressListener)}. The new progress listener type
     * provides much richer information and much better handling of parallel operations that run during the build, such as tasks that run in parallel.
     * You should prefer using the new listener interface where possible. Note, however, that the new interface is supported only for Gradle 2.5 and later
     * and is currently {@link Incubating}. It may change in later Gradle releases.
     * </p>
     *
     * @param listener The listener
     * @return this
     * @since 1.0-milestone-7
     */
    LongRunningOperation addProgressListener(ProgressListener listener);

    /**
     * Adds a progress listener which will receive progress events of all types as the operation runs.
     *
     * <p>This method is intended to replace {@link #addProgressListener(ProgressListener)}. You should prefer using the new progress listener method where possible,
     * as the new interface provides much richer information and much better handling of parallel operations that run during the build.
     * </p>
     *
     * <p>Supported by Gradle 2.5 or later. Gradle 2.4 supports {@link OperationType#TEST} operations only. Ignored for older versions.</p>
     *
     * @param listener The listener
     * @return this
     * @since 2.5
     */
    @Incubating
    LongRunningOperation addProgressListener(org.gradle.tooling.events.ProgressListener listener);

    /**
     * Adds a progress listener which will receive progress events as the operations of the requested type run.
     *
     * <p>This method is intended to replace {@link #addProgressListener(ProgressListener)}. You should prefer using the new progress listener method where possible,
     * as the new interface provides much richer information and much better handling of parallel operations that run during the build.
     * </p>
     *
     * <p>Supported by Gradle 2.5 or later. Gradle 2.4 supports {@link OperationType#TEST} operations only. Ignored for older versions.</p>
     *
     * @param listener The listener
     * @param operationTypes The types of operations to receive progress events for.
     * @return this
     * @since 2.5
     */
    @Incubating
    LongRunningOperation addProgressListener(org.gradle.tooling.events.ProgressListener listener, Set<OperationType> operationTypes);

    /**
     * Adds a progress listener which will receive progress events as the operations of the requested type run.
     *
     * <p>This method is intended to replace {@link #addProgressListener(ProgressListener)}. You should prefer using the new progress listener method where possible,
     * as the new interface provides much richer information and much better handling of parallel operations that run during the build.
     * </p>
     *
     * <p>Supported by Gradle 2.5 or later. Gradle 2.4 supports {@link OperationType#TEST} operations only. Ignored for older versions.</p>
     *
     * @param listener The listener
     * @param operationTypes The types of operations to receive progress events for.
     * @return this
     * @since 2.6
     */
    LongRunningOperation addProgressListener(org.gradle.tooling.events.ProgressListener listener, OperationType... operationTypes);

    /**
     * Sets the cancellation token to use to cancel the operation if required.
     *
     * <p>Supported by Gradle 2.1 or later. Ignored for older versions.</p>
     *
     * @since 2.1
     */
    @Incubating
    LongRunningOperation withCancellationToken(CancellationToken cancellationToken);
}
