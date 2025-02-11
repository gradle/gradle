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
import org.gradle.api.JavaVersion;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.integtests.fixtures.RichConsoleStyling;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.jvm.Jvm;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.TextUtil;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
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
     * Sets the additional environment variables to use when executing the build.
     * <p>
     * The provided environment is added to the environment variables of this process, so it is only possible to add new variables or modify values of existing ones.
     * Not propagating a variable of this process to the executed build at all is not supported.
     * <p>
     * Setting "JAVA_HOME" this way is not supported.
     */
    GradleExecuter withEnvironmentVars(Map<String, ?> environment);

    @Deprecated
    GradleExecuter usingSettingsFile(File settingsFile);

    GradleExecuter usingInitScript(File initScript);

    /**
     * Uses the given project directory
     */
    GradleExecuter usingProjectDirectory(File projectDir);

    /**
     * Uses the given build script
     */
    @Deprecated
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
     * Sets the Gradle version for executing Gradle.
     *
     * This does not actually use a different gradle version,
     * it just modifies result of DefaultGradleVersion.current() for the Gradle that is run by the executer.
     */
    GradleExecuter withGradleVersionOverride(GradleVersion gradleVersion);

    /**
     * Sets the java home dir. Replaces any value set by {@link #withJvm(Jvm)}.
     * <p>
     * In general, prefer using {@link #withJvm(Jvm)} over this method. This method should be used
     * when testing non-standard JVMs, like embedded JREs, or those not provided by
     * {@link org.gradle.integtests.fixtures.AvailableJavaHomes}.
     */
    GradleExecuter withJavaHome(String userHomeDir);

    /**
     * Sets the JVM to execute Gradle with. Replaces any value set by {@link #withJavaHome(String)}.
     *
     * @throws IllegalArgumentException If the given JVM is not probed, for example JVMs created by {@link Jvm#forHome(File)}
     */
    GradleExecuter withJvm(Jvm jvm);

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

    default GradleExecuter withStdIn(String input) {
        return withStdinPipe(new PipedOutputStream() {
            @Override
            public void connect(PipedInputStream snk) throws IOException {
                super.connect(snk);
                write(TextUtil.toPlatformLineSeparators(input).getBytes());
            }
        });
    }

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
     * Don't set native services dir explicitly.
     */
    GradleExecuter withNoExplicitNativeServicesDir();

    /**
     * Enables the rendering of stack traces for deprecation logging.
     */
    GradleExecuter withFullDeprecationStackTraceEnabled();

    GradleExecuter withoutInternalDeprecationStackTraceFlag();

    /**
     * Downloads and sets up the JVM arguments for running the Gradle daemon with the file leak detector: https://github.com/jenkinsci/lib-file-leak-detector
     *
     * NOTE: This requires running the test with at least JDK8 and the forking executer. This will apply the file leak detection version suitable for executor Java version.
     * If your build sets a different Java version you can use {@link #withFileLeakDetection(JavaVersion, String...)} to specify the Java version for which the file leak detection should be enabled.
     *
     * This should not be checked-in on. This is only for local debugging.
     *
     * By default, this starts a HTTP server on port 19999, so you can observe which files are open on http://localhost:19999. Passing any arguments disables this behavior.
     *
     * @param args the arguments to pass the file leak detector java agent
     */
    GradleExecuter withFileLeakDetection(String... args);

    /**
     * Same as {@link #withFileLeakDetection(String...)}, but allows to specify the Java version for which the file leak detection should be enabled.
     */
    GradleExecuter withFileLeakDetection(JavaVersion javaVersion, String... args);

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
     * Sets the path to the read-only dependency cache
     *
     * @param cacheDir the path to the RO dependency cache
     * @return this executer
     */
    GradleExecuter withReadOnlyCacheDir(File cacheDir);

    /**
     * Sets the path to the read-only dependency cache
     *
     * @param cacheDir the path to the RO dependency cache
     * @return this executer
     */
    GradleExecuter withReadOnlyCacheDir(String cacheDir);

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
     * Expects exactly one deprecation warning in the build output. If more than one warning is produced,
     * or no warning is produced at all, the assertion fails.
     *
     * @see #expectDeprecationWarnings(int)
     * @deprecated Use {@link #expectDeprecationWarning(String)} instead.
     */
    @Deprecated
    GradleExecuter expectDeprecationWarning();

    /**
     * Expects exactly the given deprecation warning.
     *
     * This may show up with a strikethrough in IntelliJ as if it were deprecated.  This method is still okay to use.  You can
     * also switch to the more specific {@link #expectDocumentedDeprecationWarning(String)} if the warning includes a documentation
     * link and you don't want to (ironically) see code testing deprecation appearing as if it itself were deprecated.
     */
    default GradleExecuter expectDeprecationWarning(String warning) {
        return expectDeprecationWarning(ExpectedDeprecationWarning.withMessage(warning));
    }

    default GradleExecuter expectDeprecationWarningWithPattern(String pattern) {
        return expectDeprecationWarning(ExpectedDeprecationWarning.withSingleLinePattern(pattern));
    }

    default GradleExecuter expectDeprecationWarningWithMultilinePattern(String pattern) {
        return expectDeprecationWarningWithMultilinePattern(pattern, pattern.split("\n").length);
    }

    default GradleExecuter expectDeprecationWarningWithMultilinePattern(String pattern, int numLines) {
        return expectDeprecationWarning(ExpectedDeprecationWarning.withMultiLinePattern(pattern, numLines));
    }

    GradleExecuter expectDeprecationWarning(ExpectedDeprecationWarning warning);

    /**
     * Expects the given deprecation warning, allowing to pass documentation url with /current/ version and asserting against the actual current version instead.
     */
    GradleExecuter expectDocumentedDeprecationWarning(String warning);

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
     * Disable automatic Java version deprecation warning filtering.
     * <p>
     * By default, the executor will ignore all deprecation warnings related to running a build
     * on a Java version that will no longer be supported in future versions of Gradle. In most
     * cases we do not care about this warning, but when we want to explicitly test that a build
     * does emit this warning, we disable this filter.
     */
    GradleExecuter disableDaemonJavaVersionDeprecationFiltering();

    /**
     * Disable crash daemon checks
     */
    GradleExecuter noDaemonCrashChecks();

    /**
     * Disables asserting that class loaders were not eagerly created, potentially leading to performance problems.
     */
    GradleExecuter withEagerClassLoaderCreationCheckDisabled();

    /**
     * Disables asserting that no unexpected stacktraces are present in the output.
     */
    GradleExecuter withStackTraceChecksDisabled();

    /**
     * Enables checks for warnings emitted by the JDK itself. Including illegal access warnings.
     */
    GradleExecuter withJdkWarningChecksEnabled();

    /**
     * An executer may decide to implicitly bump the logging level, unless this is called.
     */
    GradleExecuter noExtraLogging();

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
     * Use {@link #requireOwnGradleUserHomeDir(String because)} instead.
     */
    @Deprecated
    GradleExecuter requireOwnGradleUserHomeDir();

    /**
     * Configures a unique gradle user home dir for the test.
     *
     * The gradle user home dir used will be underneath the {@link #getTestDirectoryProvider()} directory.
     *
     * This value is persistent across executions by this executer.
     *
     * <p>Note: does not affect the daemon base dir.</p>
     */
    GradleExecuter requireOwnGradleUserHomeDir(String because);

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
     * Where possible, starts the Gradle build process in debug mode with the provided options.
     */
    GradleExecuter startBuildProcessInDebugger(Action<JavaDebugOptionsInternal> action);

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

    /**
     * Starts the launcher JVM (daemon client) in debug mode with the provided options
     */
    GradleExecuter startLauncherInDebugger(Action<JavaDebugOptionsInternal> action);

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
     * Execute the builds with adding the {@code "--stacktrace"} argument.
     */
    GradleExecuter withStacktraceEnabled();

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
    GradleExecuter withPluginRepositoryMirrorDisabled();

    GradleExecuter ignoreMissingSettingsFile();

    GradleExecuter ignoreCleanupAssertions();

    GradleExecuter withToolchainDetectionEnabled();

    GradleExecuter withToolchainDownloadEnabled();

}
