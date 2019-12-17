/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests.fixtures.executer;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.integtests.fixtures.RichConsoleStyling;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface GradleExecuter extends Stoppable {
    /**
     * Sets the working directory to use. Defaults to the test's temporary directory.
     */
    GradleExecuter inDirectory(File directory);

    /**
     * Sets the task names to execute. Defaults to an empty list.
     */
    GradleExecuter withTasks(String... names);

    /**
     * Sets the task names to execute. Defaults to an empty list.
     */
    GradleExecuter withTasks(List<String> names);

    GradleExecuter withTaskList();

    GradleExecuter withDependencyList();

    GradleExecuter withQuietLogging();

    /**
     * Sets the additional command-line arguments to use when executing the build. Defaults to an empty list.
     */
    GradleExecuter withArguments(String... args);

    /**
     * Sets the additional command-line arguments to use when executing the build. Defaults to an empty list.
     */
    GradleExecuter withArguments(List<String> args);

    /**
     * Adds an additional command-line argument to use when executing the build.
     */
    GradleExecuter withArgument(String arg);

    /**
     * Sets the environment variables to use when executing the build. Defaults to the environment of this process.
     */
    GradleExecuter withEnvironmentVars(Map<String, ?> environment);

    GradleExecuter usingSettingsFile(File settingsFile);

    GradleExecuter usingInitScript(File initScript);

    /**
     * Uses the given project directory
     */
    GradleExecuter usingProjectDirectory(File projectDir);

    /**
     * Uses the given build script
     */
    GradleExecuter usingBuildScript(File buildScript);

    /**
     * Sets the user's home dir to use when running the build. Implementations are not 100% accurate.
     */
    GradleExecuter withUserHomeDir(File userHomeDir);

    /**
     * Sets the <em>Gradle</em> user home dir. Setting to null requests that the executer use the real default Gradle user home dir rather than the default used for testing.
     *
     * This value is persistent across executions by this executer.
     *
     * <p>Note: does not affect the daemon base dir.</p>
     */
    GradleExecuter withGradleUserHomeDir(File userHomeDir);

    /**
     * Sets the java home dir. Setting to null requests that the executer use the real default java home dir rather than the default used for testing.
     */
    GradleExecuter withJavaHome(File userHomeDir);

    /**
     * Sets the executable to use. Set to null to use the real default executable (if any) rather than the default used for testing.
     */
    GradleExecuter usingExecutable(String script);

    /**
     * Sets a stream to use for writing to stdin which can be retrieved with getStdinPipe().
     */
    GradleExecuter withStdinPipe();

    /**
     * Sets a stream to use for writing to stdin.
     */
    GradleExecuter withStdinPipe(PipedOutputStream stdInPipe);

    /**
     * Executes the requested build, asserting that the build succeeds. Resets the configuration of this executer.
     *
     * @return The result.
     */
    ExecutionResult run();

    /**
     * Executes the requested build, asserting that the build fails. Resets the configuration of this executer.
     *
     * @return The result.
     */
    ExecutionFailure runWithFailure();

    /**
     * Starts executing the build asynchronously.
     *
     * @return the handle, never null.
     */
    GradleHandle start();

    /**
     * Adds JVM args that should be used to start any command-line `gradle` executable used to run the build. Note that this may be different to the build JVM, for example the build may run in a
     * daemon process. You should prefer using {@link #withBuildJvmOpts(String...)} over this method.
     */
    GradleExecuter withCommandLineGradleOpts(String... jvmOpts);

    /**
     * See {@link #withCommandLineGradleOpts(String...)}.
     */
    GradleExecuter withCommandLineGradleOpts(Iterable<String> jvmOpts);

    /**
     * Adds JVM args that should be used by the build JVM. Does not necessarily imply that the build will be run in a separate process, or that a new build JVM will be started, only that the build
     * will run in a JVM that was started with the specified args.
     *
     * @param jvmOpts the JVM opts
     * @return this executer
     */
    GradleExecuter withBuildJvmOpts(String... jvmOpts);

    /**
     * See {@link #withBuildJvmOpts(String...)}.
     */
    GradleExecuter withBuildJvmOpts(Iterable<String> jvmOpts);

    /**
     * Activates the build cache
     *
     * @return this executer
     */
    GradleExecuter withBuildCacheEnabled();

    /**
     * Don't set temp folder explicitly.
     */
    GradleExecuter withNoExplicitTmpDir();

    /**
     * Don't set native services dir explicitly.
     */
    GradleExecuter withNoExplicitNativeServicesDir();

    /**
     * Disables the rendering of stack traces for deprecation logging.
     */
    GradleExecuter withFullDeprecationStackTraceDisabled();

    /**
     * Specifies that the executer should only those JVM args explicitly requested using {@link #withBuildJvmOpts(String...)} and {@link #withCommandLineGradleOpts(String...)} (where appropriate) for
     * the build JVM and not attempt to provide any others.
     */
    GradleExecuter useOnlyRequestedJvmOpts();

    /**
     * Sets the default character encoding to use.
     *
     * Only makes sense for forking executers.
     *
     * @return this executer
     */
    GradleExecuter withDefaultCharacterEncoding(String defaultCharacterEncoding);

    /**
     * Sets the default locale to use.
     *
     * Only makes sense for forking executers.
     *
     * @return this executer
     */
    GradleExecuter withDefaultLocale(Locale defaultLocale);

    /**
     * Set the number of seconds an idle daemon should live for.
     *
     * @return this executer
     */
    GradleExecuter withDaemonIdleTimeoutSecs(int secs);

    /**
     * Set the working space for any daemons used by the builds.
     *
     * This value is persistent across executions by this executer.
     *
     * <p>Note: this does not affect the Gradle user home directory.</p>
     *
     * @return this executer
     */
    GradleExecuter withDaemonBaseDir(File baseDir);

    /**
     * Returns the working space for any daemons used by the builds.
     */
    File getDaemonBaseDir();

    /**
     * Requires that the build run in a separate daemon process.
     */
    GradleExecuter requireDaemon();

    /**
     * Asserts that this executer will be able to run a build, given its current configuration.
     *
     * @throws AssertionError When this executer will not be able to run a build.
     */
    void assertCanExecute() throws AssertionError;

    /**
     * Adds an action to be called immediately before execution, to allow extra configuration to be injected.
     */
    void beforeExecute(Action<? super GradleExecuter> action);

    /**
     * Adds an action to be called immediately before execution, to allow extra configuration to be injected.
     */
    void beforeExecute(@DelegatesTo(GradleExecuter.class) Closure action);

    /**
     * Adds an action to be called immediately after execution
     */
    void afterExecute(Action<? super GradleExecuter> action);

    /**
     * Adds an action to be called immediately after execution
     */
    void afterExecute(@DelegatesTo(GradleExecuter.class) Closure action);

    /**
     * The directory that the executer will use for any test specific storage.
     *
     * May or may not be the same directory as the build to be run.
     */
    TestDirectoryProvider getTestDirectoryProvider();

    /**
     * Default is enabled = true.
     *
     * All our tests should work with partial VFS invalidation.
     * As soon as partial invalidation is enabled by default, we can remove this method and the field again.
     */
    GradleExecuter withPartialVfsInvalidation(boolean enabled);

    /**
     * Expects exactly one deprecation warning in the build output. If more than one warning is produced,
     * or no warning is produced at all, the assertion fails.
     *
     * @see #expectDeprecationWarnings(int)
     *
     * @deprecated Use {@link #expectDeprecationWarning(String)} instead.
     */
    @Deprecated
    GradleExecuter expectDeprecationWarning();

    /**
     * Expects exactly the given deprecation warning.
     */
    GradleExecuter expectDeprecationWarning(String warning);

    /**
     * Expects exactly the given number of deprecation warnings. If fewer or more warnings are produced during
     * the execution, the assertion fails.
     *
     * @deprecated Use {@link #expectDeprecationWarning(String)} instead.
     */
    @Deprecated
    GradleExecuter expectDeprecationWarnings(int count);

    /**
     * Disable deprecation warning checks.
     */
    GradleExecuter noDeprecationChecks();

    /**
     * Disables asserting that class loaders were not eagerly created, potentially leading to performance problems.
     */
    GradleExecuter withEagerClassLoaderCreationCheckDisabled();

    /**
     * Disables asserting that no unexpected stacktraces are present in the output.
     */
    GradleExecuter withStackTraceChecksDisabled();

    /**
     * An executer may decide to implicitly bump the logging level, unless this is called.
     */
    GradleExecuter noExtraLogging();

    /**
     * Requires that there is a real gradle distribution for the execution, which in-process execution does not.
     *
     * <p>Note: try to avoid using this method. It has some major drawbacks when it comes to development: 1. It requires a Gradle distribution or installation, and this will need to be rebuilt after
     * each change in order to use the test, and 2. it requires that the build run in a different JVM, which makes it very difficult to debug.</p>
     */
    GradleExecuter requireGradleDistribution();

    /**
     * Configures that any daemons used by the execution are unique to the test.
     *
     * This value is persistent across executions by this executer.
     *
     * <p>Note: this does not affect the Gradle user home directory.</p>
     */
    GradleExecuter requireIsolatedDaemons();

    /**
     * Disable worker daemons expiration.
     */
    GradleExecuter withWorkerDaemonsExpirationDisabled();

    /**
     * Returns true if this executer will share daemons with other executers.
     */
    boolean usesSharedDaemons();

    /**
     * Configures a unique gradle user home dir for the test.
     *
     * The gradle user home dir used will be underneath the {@link #getTestDirectoryProvider()} directory.
     *
     * This value is persistent across executions by this executer.
     *
     * <p>Note: does not affect the daemon base dir.</p>
     */
    GradleExecuter requireOwnGradleUserHomeDir();

    /**
     * The Gradle user home dir that will be used for executions.
     */
    TestFile getGradleUserHomeDir();

    /**
     * The distribution used to execute.
     */
    GradleDistribution getDistribution();

    /**
     * Copies the settings from this executer to the given executer.
     *
     * @param executer The executer to copy to
     * @return The passed in executer
     */
    GradleExecuter copyTo(GradleExecuter executer);

    /**
     * Where possible, starts the Gradle build process in suspended debug mode.
     */
    GradleExecuter startBuildProcessInDebugger(boolean flag);

    GradleExecuter withProfiler(String profilerArg);

    /**
     * Forces Gradle to consider the build to be interactive
     */
    GradleExecuter withForceInteractive(boolean flag);

    boolean isDebug();

    boolean isProfile();

    /**
     * Starts the launcher JVM (daemon client) in suspended debug mode
     */
    GradleExecuter startLauncherInDebugger(boolean debugLauncher);

    boolean isDebugLauncher();

    /**
     * Clears previous settings so that instance can be reused
     */
    GradleExecuter reset();

    /**
     * Measures the duration of the execution
     */
    GradleExecuter withDurationMeasurement(DurationMeasurement durationMeasurement);

    /**
     * Returns true if this executer uses a daemon
     */
    boolean isUseDaemon();

    /**
     * Configures that user home services should not be reused across multiple invocations.
     *
     * <p>
     * Note: You will want to call this method if the test case defines a custom Gradle user home directory
     * so the services can be shut down after test execution in
     * {@link org.gradle.internal.service.scopes.DefaultGradleUserHomeScopeServiceRegistry#release(org.gradle.internal.service.ServiceRegistry)}.
     * Not calling the method in those situations will result in the inability to delete a file lock.
     * </p>
     */
    GradleExecuter withOwnUserHomeServices();

    /**
     * Executes the build with {@code "--console=rich, auto, verbose"} argument.
     *
     * @see RichConsoleStyling
     */
    GradleExecuter withConsole(ConsoleOutput consoleOutput);

    /**
     * Executes the build with {@code "--warning-mode=none, summary, fail, all"} argument.
     *
     * @see WarningMode
     */
    GradleExecuter withWarningMode(WarningMode warningMode);

    /**
     * Execute the builds without adding the {@code "--stacktrace"} argument.
     */
    GradleExecuter withStacktraceDisabled();

    /**
     * Renders the welcome message users see upon first invocation of a Gradle distribution with a given Gradle user home directory.
     * By default the message is never rendered.
     */
    GradleExecuter withWelcomeMessageEnabled();

    /**
     * Specifies we should use a test console that has both stdout and stderr attached.
     */
    GradleExecuter withTestConsoleAttached();

    /**
     * Specifies we should use a test console that only has stdout attached.
     */
    GradleExecuter withTestConsoleAttached(ConsoleAttachment consoleAttachment);

    /**
     * Apply an init script which replaces all external repositories with inner mirrors.
     * Note this doesn't work for buildSrc and composite build.
     *
     * @see org.gradle.integtests.fixtures.RepoScriptBlockUtil
     */
    GradleExecuter withRepositoryMirrors();

    /**
     * Requires an isolated gradle user home and put an init script which replaces all external repositories with inner mirrors.
     * This works for all scenarios.
     *
     * @see org.gradle.integtests.fixtures.RepoScriptBlockUtil
     */
    GradleExecuter withGlobalRepositoryMirrors();

    /**
     * Start the build with {@link org.gradle.api.internal.artifacts.BaseRepositoryFactory#PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY}
     * set to our inner mirror.
     *
     * @see org.gradle.integtests.fixtures.RepoScriptBlockUtil
     */
    GradleExecuter withPluginRepositoryMirror();

    GradleExecuter ignoreMissingSettingsFile();
}
