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

import org.gradle.tooling.events.test.TestOperationDescriptor;

/**
 *
 * <p>A {@code TestLauncher} allows you to execute tests in a Gradle build.</p>
 *
 * <p>If the target Gradle version is &gt;=6.8 then you can use {@code TestLauncher} to execute tests from included builds. Test operation descriptors from included builds work out-of-the-box. You can
 * target tasks from included builds by specifying the task identity path (i.e. {@code ':included-build-name:subproject-name:taskName'}).</p>
 *
 * <p>However, you cannot use the methods with included builds that don't specify the target test tasks (e.g. {@code withJvmTestClasses()} and {@code withJvmTestMethods()}). Those methods configure
 * all test tasks in the root build only.</p>
 *
 * @since 2.6
 */
public interface TestLauncher extends ConfigurableLauncher<TestLauncher> {

    /**
     * Adds tests to be executed by passing test descriptors received from a previous Gradle Run.
     *
     * @param descriptors The OperationDescriptor defining one or more tests.
     * @return this
     * @since 2.6
     */
    TestLauncher withTests(TestOperationDescriptor... descriptors);

    /**
     * Adds tests to be executed by passing test descriptors received from a previous Gradle Run.
     *
     * @param descriptors The OperationDescriptor defining one or more tests.
     * @return this
     * @since 2.6
     */
    TestLauncher withTests(Iterable<? extends TestOperationDescriptor> descriptors);

    /**
     * <p>Adds tests to be executed declared by class name.</p>
     *
     * <p>This method ignores tests defined in included builds.</p>
     *
     * @param testClasses The class names of the tests to be executed.
     * @return this
     * @since 2.6
     */
    TestLauncher withJvmTestClasses(String... testClasses);


    /**
     * <p>Adds tests to be executed declared by class name.</p>
     *
     * <p>This method ignores tests defined in included builds.</p>
     *
     * @param testClasses The class names of the tests to be executed.
     * @return this
     * @since 2.6
     */
    TestLauncher withJvmTestClasses(Iterable<String> testClasses);

    /**
     * <p>Adds tests to be executed declared by class and method name.</p>
     *
     * <p>This method ignores tests defined in included builds.</p>
     *
     * @param testClass The name of the class containing the methods to execute.
     * @param methods The names of the test methods to be executed.
     * @return this
     * @since 2.7
     */
    TestLauncher withJvmTestMethods(String testClass, String... methods);

    /**
     * <p>Adds tests to be executed declared by class and methods name.</p>
     *
     * <p>This method ignores tests defined in included builds.</p>
     *
     * @param testClass The name of the class containing the methods to execute.
     * @param methods The names of the test methods to be executed.
     * @return this
     * @since 2.7
     */
    TestLauncher withJvmTestMethods(String testClass, Iterable<String> methods);

    /**
     * Adds tests to be executed declared by the container task and the class name.
     *
     * <p>Note: These tests are ignored for target Gradle version earlier than 6.1</p>
     *
     * @param task The path of the target task.
     * @param testClasses The class names of the tests to be executed.
     * @return this
     * @since 6.1
     */
    TestLauncher withTaskAndTestClasses(String task, Iterable<String> testClasses);

    /**
     * Adds tests to be executed declared by the container task, class and method name.
     *
     * <p>Note: These tests are ignored for target Gradle version earlier than 6.1</p>
     * @param task The path of the target task.
     * @param testClass The name of the class containing the methods to execute.
     * @param methods The names of the test methods to be executed.
     * @return this
     * @since 6.1
     */
    TestLauncher withTaskAndTestMethods(String task, String testClass, Iterable<String> methods);

    /**
     * Configures test JVM to run in debug mode.
     * <p>
     * When called, the forked test JVM is launched with the following argument:
     * <pre>-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=localhost:&lt;port&gt;</pre>
     * This means the test JVM expects a debugger at the specified port that uses a
     * <a href="https://docs.oracle.com/javase/6/docs/technotes/guides/jpda/conninv.html#Connectors">socket listening connector</a>.
     * If the debugger is not present then the test execution will fail.
     * <p>
     * Invoking this method adjusts the test task to launch only one JVM. More specifically, the parallel execution
     * gets disabled and the {@code forkEvery} property is set to 0.
     *
     * @param port the target port where the test JVM expects the debugger
     * @return this
     *
     * @since 5.6
     */
    TestLauncher debugTestsOn(int port);

    /**
     * Executes the tests, blocking until complete.
     *
     * @throws TestExecutionException when one or more tests fail, or no tests for execution declared or no matching tests can be found.
     * @throws UnsupportedVersionException When the target Gradle version does not support test execution.
     * @throws org.gradle.tooling.exceptions.UnsupportedBuildArgumentException When there is a problem with build arguments provided by {@link #withArguments(String...)}.
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
     *          When the target Gradle version does not support some requested configuration option.
     * @throws BuildException On some failure while executing the tests in the Gradle build.
     * @throws BuildCancelledException When the operation was cancelled before it completed successfully.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 2.6
     */
    void run() throws TestExecutionException;

    /**
     * Starts executing the tests. This method returns immediately, and the result is later passed to the given handler.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)}
     * method is called with the appropriate exception. See {@link #run()} for a description of the various exceptions that the operation may fail with.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 2.6
     */
    void run(ResultHandler<? super Void> handler);
}
