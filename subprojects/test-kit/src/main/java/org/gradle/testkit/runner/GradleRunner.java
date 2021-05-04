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

package org.gradle.testkit.runner;

import org.gradle.testkit.runner.internal.DefaultGradleRunner;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Writer;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Executes a Gradle build, allowing inspection of the outcome.
 * <p>
 * A Gradle runner can be used to functionally test build logic, by executing a contrived build.
 * Assertions can then be made on the outcome of the build, such as the state of files created by the build,
 * or what tasks were actually executed during the build.
 * <p>
 * A runner can be created via the {@link #create()} method.
 * <p>
 * Typically, the test code using the runner will programmatically create a build (e.g. by writing Gradle build files to a temporary space) to execute.
 * The build to execute is effectively specified by the {@link #withProjectDir(File)}} method.
 * It is a requirement that a project directory be set.
 * <p>
 * The {@link #withArguments(String...)} method allows the build arguments to be specified,
 * just as they would be on the command line.
 * <p>
 * The {@link #build()} method can be used to invoke the build when it is expected to succeed,
 * while the {@link #buildAndFail()} method can be used when the build is expected to fail.
 * <p>
 * GradleRunner instances are not thread safe and cannot be used concurrently.
 * However, multiple instances are able to be used concurrently.
 * <p>
 * On Windows, Gradle runner disables file system watching for the executed build, since the Windows watchers add a file lock
 * on the root project directory, causing problems when trying to delete it. You can still enable file system watching manually
 * for your test by adding the `--watch-fs` command line argument via {@link #withArguments(String...)}.
 * <p>
 * Please see the Gradle <a href="https://docs.gradle.org/current/userguide/test_kit.html" target="_top">TestKit</a> User Manual chapter for more information.
 *
 * @since 2.6
 */
public abstract class GradleRunner {

    /**
     * Creates a new Gradle runner.
     * <p>
     * The runner requires a Gradle distribution (and therefore a specific version of Gradle) in order to execute builds.
     * This method will find a Gradle distribution, based on the filesystem location of this class.
     * That is, it is expected that this class is loaded from a Gradle distribution.
     * <p>
     * When using the runner as part of tests <i>being executed by Gradle</i> (i.e. a build using the {@code gradleTestKit()} dependency),
     * this means that the same distribution of Gradle that is executing the tests will be used by runner returned by this method.
     * <p>
     * When using the runner as part of tests <i>being executed by an IDE</i>,
     * this means that the same distribution of Gradle that was used when importing the project will be used.
     *
     * @return a new Gradle runner
     */
    public static GradleRunner create() {
        return new DefaultGradleRunner();
    }

    /**
     * Configures the runner to execute the build with the version of Gradle specified.
     * <p>
     * Unless previously downloaded, this method will cause the Gradle runtime for the version specified
     * to be downloaded over the Internet from Gradle's distribution servers.
     * The download will be cached beneath the Gradle User Home directory, the location of which is determined by the following in order of precedence:
     * <ol>
     * <li>The system property {@code "gradle.user.home"}</li>
     * <li>The environment variable {@code "GRADLE_USER_HOME"}</li>
     * </ol>
     * <p>
     * If neither are present, {@code "~/.gradle"} will be used, where {@code "~"} is the value advertised by the JVM's {@code "user.dir"} system property.
     * The system property and environment variable are read in the process using the runner, not the build process.
     * <p>
     * Alternatively, you may use {@link #withGradleInstallation(File)} to use an installation already on the filesystem.
     * <p>
     * To use a non standard Gradle runtime, or to obtain the runtime from an alternative location, use {@link #withGradleDistribution(URI)}.
     *
     * @param versionNumber the version number (e.g. "2.9")
     * @return this
     * @since 2.9
     * @see #withGradleInstallation(File)
     * @see #withGradleDistribution(URI)
     */
    public abstract GradleRunner withGradleVersion(String versionNumber);

    /**
     * Configures the runner to execute the build using the installation of Gradle specified.
     * <p>
     * The given file must be a directory containing a valid Gradle installation.
     * <p>
     * Alternatively, you may use {@link #withGradleVersion(String)} to use an automatically installed Gradle version.
     *
     * @param installation a valid Gradle installation
     * @return this
     * @since 2.9
     * @see #withGradleVersion(String)
     * @see #withGradleDistribution(URI)
     */
    public abstract GradleRunner withGradleInstallation(File installation);

    /**
     * Configures the runner to execute the build using the distribution of Gradle specified.
     * <p>
     * The given URI must point to a valid Gradle distribution ZIP file.
     * This method is typically used as an alternative to {@link #withGradleVersion(String)},
     * where it is preferable to obtain the Gradle runtime from "local" servers.
     * <p>
     * Unless previously downloaded, this method will cause the Gradle runtime at the given URI to be downloaded.
     * The download will be cached beneath the Gradle User Home directory, the location of which is determined by the following in order of precedence:
     * <ol>
     * <li>The system property {@code "gradle.user.home"}</li>
     * <li>The environment variable {@code "GRADLE_USER_HOME"}</li>
     * </ol>
     * <p>
     * If neither are present, {@code "~/.gradle"} will be used, where {@code "~"} is the value advertised by the JVM's {@code "user.dir"} system property.
     * The system property and environment variable are read in the process using the runner, not the build process.
     *
     * @param distribution a URI pointing at a valid Gradle distribution zip file
     * @return this
     * @since 2.9
     * @see #withGradleVersion(String)
     * @see #withGradleInstallation(File)
     */
    public abstract GradleRunner withGradleDistribution(URI distribution);

    /**
     * Sets the directory to use for TestKit's working storage needs.
     * <p>
     * This directory is used internally to store various files required by the runner.
     * If no explicit Gradle user home is specified via the build arguments (i.e. the {@code -g «dir»} option}),
     * this directory will also be used for the Gradle user home for the test build.
     * <p>
     * If no value has been specified when the build is initiated, a directory will be created within a temporary directory.
     * <ul>
     * <li>When executed from a Gradle Test task, the Test task's temporary directory is used (see {@link org.gradle.api.Task#getTemporaryDir()}).</li>
     * <li>When executed from somewhere else, the system's temporary directory is used (based on {@code java.io.tmpdir}).</li>
     * </ul>
     * <p>
     * This directory is not deleted by the runner after the test build.
     * <p>
     * You may wish to specify a location that is within your project and regularly cleaned, such as the project's build directory.
     * <p>
     * It can be set using the system property {@code org.gradle.testkit.dir} for the test process,
     * <p>
     * The actual contents of this directory are an internal implementation detail and may change at any time.
     *
     * @param testKitDir the TestKit directory
     * @return {@code this}
     * @since 2.7
     */
    public abstract GradleRunner withTestKitDir(File testKitDir);

    /**
     * The directory that the build will be executed in.
     * <p>
     * This is analogous to the current directory when executing Gradle from the command line.
     *
     * @return the directory to execute the build in
     */
    public abstract File getProjectDir();

    /**
     * Sets the directory that the Gradle will be executed in.
     * <p>
     * This is typically set to the root project of the build under test.
     * <p>
     * A project directory must be set.
     * This method must be called before {@link #build()} or {@link #buildAndFail()}.
     * <p>
     * All builds executed with the runner effectively do not search parent directories for a {@code settings.gradle} file.
     * This suppresses Gradle's default behaviour of searching upwards through the file system in order to find the root of the current project tree.
     * This default behaviour is often utilised when focusing on a particular build within a multi-project build.
     * This behaviour is suppressed due to test builds being executed potentially being created within a “real build”
     * (e.g. under the {@code /build} directory of the plugin's project directory).
     *
     * @param projectDir the project directory
     * @return {@code this}
     * @see #getProjectDir()
     */
    public abstract GradleRunner withProjectDir(File projectDir);

    /**
     * The build arguments.
     * <p>
     * Effectively, the command line arguments to Gradle.
     * This includes all tasks, flags, properties etc.
     * <p>
     * The returned list is immutable.
     *
     * @return the build arguments
     */
    public abstract List<String> getArguments();

    /**
     * Sets the build arguments.
     *
     * @param arguments the build arguments
     * @return this
     * @see #getArguments()
     */
    public abstract GradleRunner withArguments(List<String> arguments);

    /**
     * Sets the build arguments.
     *
     * @param arguments the build arguments
     * @return this
     * @see #getArguments()
     */
    public abstract GradleRunner withArguments(String... arguments);

    /**
     * The injected plugin classpath for the build.
     * <p>
     * The returned list is immutable.
     * Returns an empty list if no classpath was provided with {@link #withPluginClasspath(Iterable)}.
     *
     * @return the classpath of plugins to make available to the build under test
     * @since 2.8
     */
    public abstract List<? extends File> getPluginClasspath();

    /**
     * Sets the plugin classpath based on the Gradle plugin development plugin conventions.
     * <p>
     * The 'java-gradle-plugin' generates a file describing the plugin under test and makes it available to the test runtime.
     * This method configures the runner to use this file.
     * Please consult the Gradle documentation of this plugin for more information.
     * <p>
     * This method looks for a file named {@code plugin-under-test-metadata.properties} on the runtime classpath,
     * and uses the {@code implementation-classpath} as the classpath, which is expected to a {@link File#pathSeparatorChar} joined string.
     * If the plugin metadata file cannot be resolved an {@link InvalidPluginMetadataException} is thrown.
     * <p>
     * Plugins from classpath are able to be resolved using the <code>plugins { }</code> syntax in the build under test.
     * Please consult the TestKit Gradle User Manual chapter for more information and usage examples.
     * <p>
     * Calling this method will replace any previous classpath specified via {@link #withPluginClasspath(Iterable)} and vice versa.
     * <p>
     * <b>Note:</b> this method will cause an {@link InvalidRunnerConfigurationException} to be emitted when the build is executed,
     * if the version of Gradle executing the build (i.e. not the version of the runner) is earlier than Gradle 2.8 as those versions do not support this feature.
     * Please consult the TestKit Gradle User Manual chapter alternative strategies that can be used for older Gradle versions.
     *
     * @return this
     * @see #withPluginClasspath(Iterable)
     * @see #getPluginClasspath()
     * @since 2.13
     */
    public abstract GradleRunner withPluginClasspath() throws InvalidPluginMetadataException;

    /**
     * Sets the injected plugin classpath for the build.
     * <p>
     * Plugins from the given classpath are able to be resolved using the <code>plugins { }</code> syntax in the build under test.
     * Please consult the TestKit Gradle User Manual chapter for more information and usage examples.
     * <p>
     * <b>Note:</b> this method will cause an {@link InvalidRunnerConfigurationException} to be emitted when the build is executed,
     * if the version of Gradle executing the build (i.e. not the version of the runner) is earlier than Gradle 2.8 as those versions do not support this feature.
     * Please consult the TestKit Gradle User Manual chapter alternative strategies that can be used for older Gradle versions.
     *
     * @param classpath the classpath of plugins to make available to the build under test
     * @return this
     * @see #getPluginClasspath()
     * @since 2.8
     */
    public abstract GradleRunner withPluginClasspath(Iterable<? extends File> classpath);

    /**
     * Indicates whether the build should be executed “in process” so that it is debuggable.
     * <p>
     * If debug support is not enabled, the build will be executed in an entirely separate process.
     * This means that any debugger that is attached to the test execution process will not be attached to the build process.
     * When debug support is enabled, the build is executed in the same process that is using the Gradle Runner, allowing the build to be debugged.
     * <p>
     * Debug support is off (i.e. {@code false}) by default.
     * It can be enabled by setting the system property {@code org.gradle.testkit.debug} to {@code true} for the test process,
     * or by using the {@link #withDebug(boolean)} method.
     * <p>
     * When {@link #withEnvironment(Map)} is specified, running with debug is not allowed.
     * Debug mode runs "in process" and we need to fork a separate process to pass environment variables.
     *
     * @return whether the build should be executed in the same process
     * @since 2.9
     */
    public abstract boolean isDebug();

    /**
     * Sets whether debugging support is enabled.
     *
     * @see #isDebug()
     * @param flag the debug flag
     * @return this
     * @since 2.9
     */
    public abstract GradleRunner withDebug(boolean flag);

    /**
     * Environment variables for the build.
     * {@code null} is valid and indicates the build will use the system environment.
     *
     * @return environment variables
     * @since 5.2
     */
    @Nullable
    public abstract Map<String, String> getEnvironment();

    /**
     * Sets the environment variables for the build.
     * {@code null} is permitted and will make the build use the system environment.
     * <p>
     * When environment is specified, running with {@link #isDebug()} is not allowed.
     * Debug mode runs in-process and TestKit must fork a separate process to pass environment variables.
     *
     * @param environmentVariables the variables to use, {@code null} is allowed.
     * @return this
     * @since 5.2
     */
    public abstract GradleRunner withEnvironment(@Nullable Map<String, String> environmentVariables);

    /**
     * Configures the runner to forward standard output from builds to the given writer.
     * <p>
     * The output of the build is always available via {@link BuildResult#getOutput()}.
     * This method can be used to additionally capture the output.
     * <p>
     * Calling this method will negate the effect of previously calling {@link #forwardOutput()}.
     * <p>
     * The given writer will not be closed by the runner.
     * <p>
     * When executing builds with Gradle versions earlier than 2.9 <b>in debug mode</b>, any output produced by the build that
     * was written directly to {@code System.out} or {@code System.err} will not be represented in {@link BuildResult#getOutput()}.
     * This is due to a defect that was fixed in Gradle 2.9.
     *
     * @param writer the writer that build standard output should be forwarded to
     * @return this
     * @since 2.9
     * @see #forwardOutput()
     * @see #forwardStdError(Writer)
     */
    public abstract GradleRunner forwardStdOutput(Writer writer);

    /**
     * Configures the runner to forward standard error output from builds to the given writer.
     * <p>
     * The output of the build is always available via {@link BuildResult#getOutput()}.
     * This method can be used to additionally capture the error output.
     * <p>
     * Calling this method will negate the effect of previously calling {@link #forwardOutput()}.
     * <p>
     * The given writer will not be closed by the runner.
     *
     * @param writer the writer that build standard error output should be forwarded to
     * @return this
     * @since 2.9
     * @see #forwardOutput()
     * @see #forwardStdOutput(Writer)
     */
    public abstract GradleRunner forwardStdError(Writer writer);

    /**
     * Forwards the output of executed builds to the {@link System#out System.out} stream.
     * <p>
     * The output of the build is always available via {@link BuildResult#getOutput()}.
     * This method can be used to additionally forward the output to {@code System.out} of the process using the runner.
     * <p>
     * This method does not separate the standard output and error output.
     * The two streams will be merged as they typically are when using Gradle from a command line interface.
     * If you require separation of the streams, you can use {@link #forwardStdOutput(Writer)} and {@link #forwardStdError(Writer)} directly.
     * <p>
     * Calling this method will negate the effect of previously calling {@link #forwardStdOutput(Writer)} and/or {@link #forwardStdError(Writer)}.
     *
     * @return this
     * @since 2.9
     * @see #forwardStdOutput(Writer)
     * @see #forwardStdError(Writer)
     */
    public abstract GradleRunner forwardOutput();

    /**
     * Executes a build, expecting it to complete without failure.
     *
     * @throws InvalidRunnerConfigurationException if the configuration of this runner is invalid (e.g. project directory not set)
     * @throws UnexpectedBuildFailure if the build does not succeed
     * @return the build result
     */
    public abstract BuildResult build() throws InvalidRunnerConfigurationException, UnexpectedBuildFailure;

    /**
     * Executes a build, expecting it to complete with failure.
     *
     * @throws InvalidRunnerConfigurationException if the configuration of this runner is invalid (e.g. project directory not set)
     * @throws UnexpectedBuildSuccess if the build succeeds
     * @return the build result
     */
    public abstract BuildResult buildAndFail() throws InvalidRunnerConfigurationException, UnexpectedBuildSuccess;

}
