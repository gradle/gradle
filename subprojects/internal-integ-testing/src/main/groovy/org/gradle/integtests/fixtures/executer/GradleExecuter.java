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
import org.gradle.api.Action;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface GradleExecuter {
    /**
     * Sets the working directory to use. Defaults to the test's temporary directory.
     */
    GradleExecuter inDirectory(File directory);

    /**
     * Enables search upwards. Defaults to false.
     */
    GradleExecuter withSearchUpwards();

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
     * Sets the <em>Gradle</em> user home dir. Setting to null requests that the executer use the real default Gradle user home dir rather than the
     * default used for testing.
     */
    GradleExecuter withGradleUserHomeDir(File userHomeDir);

    /**
     * Sets the java home dir. Setting to null requests that the executer use the real default java home dir rather than the default used for testing.
     */
    GradleExecuter withJavaHome(File userHomeDir);

    /**
     * Sets the executable to use. Set to null to use the read default executable (if any) rather than the default used for testing.
     */
    GradleExecuter usingExecutable(String script);

    /**
     * Sets the stdin to use for the build. Defaults to an empty string.
     */
    GradleExecuter withStdIn(String text);

    /**
     * Sets the stdin to use for the build. Defaults to an empty string.
     */
    GradleExecuter withStdIn(InputStream stdin);

    /**
     * Specifies that the executer should not set any default jvm args.
     */
    GradleExecuter withNoDefaultJvmArgs();

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
     * Provides a daemon registry for any daemons started by this executer, which may be none.
     *
     * @return the daemon registry, never null.
     */
    DaemonRegistry getDaemonRegistry();

    /**
     * Starts executing the build asynchronously.
     *
     * @return the handle, never null.
     */
    GradleHandle start();

    /**
     * Adds options that should be used to start the JVM, if a JVM is to be started. Ignored if not.
     *
     * @param gradleOpts the jvm opts
     *
     * @return this executer
     */
    GradleExecuter withGradleOpts(String ... gradleOpts);

    /**
     * Sets the default character encoding to use.
     *
     * Only makes sense for forking executers.
     *
     * @return this executer
     */
    GradleExecuter withDefaultCharacterEncoding(String defaultCharacterEncoding);

    /**
     * Set the number of seconds an idle daemon should live for.
     *
     * @param secs
     *
     * @return this executer
     */
    GradleExecuter withDaemonIdleTimeoutSecs(int secs);

    /**
     * Set the working space for the daemon and launched daemons
     *
     * @param baseDir
     *
     * @return this executer
     */
    GradleExecuter withDaemonBaseDir(File baseDir);

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
    void beforeExecute(Closure action);

    /**
     * Adds an action to be called immediately after execution
     */
    void afterExecute(Action<? super GradleExecuter> action);

    /**
     * Adds an action to be called immediately after execution
     */
    void afterExecute(Closure action);

    /**
     * The directory that the executer will use for any test specific storage.
     *
     * May or may not be the same directory as the build to be run.
     */
    TestDirectoryProvider getTestDirectoryProvider();

    /**
     * Disables asserting that the execution did not trigger any deprecation warnings.
     */
    GradleExecuter withDeprecationChecksDisabled();

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
     * Requires that there is a gradle home for the execution, which in process execution does not.
     */
    GradleExecuter requireGradleHome();

    /**
     * Configures that any daemons launched by or during the execution are unique to the test.
     *
     * This value is persistent across executions in the same test.
     */
    GradleExecuter requireIsolatedDaemons();

    /**
     * Configures a unique gradle user home dir for the test.
     *
     * The gradle user home dir used will be underneath the {@link #getTestDirectoryProvider()} directory.
     *
     * This value is persistent across executions in the same test.
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
}
