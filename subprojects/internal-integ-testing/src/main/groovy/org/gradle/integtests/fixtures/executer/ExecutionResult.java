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

import org.gradle.integtests.fixtures.logging.GroupedOutputFixture;

import java.util.List;
import java.util.Set;

public interface ExecutionResult {
    /**
     * Stdout of the Gradle execution, normalized to use new-line char as line separator.
     */
    String getOutput();

    /**
     * Stdout of the Gradle execution, normalized to use new-line char as line separator. Excludes warnings about deprecated or incubating features used to run the build.
     *
     * <ul>
     *     <li>Removes warning about running on Java 7.</li>
     *     <li>Removes warning about running using configure on demand or parallel execution.</li>
     *     <li>Removes notice about starting the daemon.</li>
     *     <li>Normalizes build time to 1 second.
     * </ul>
     */
    String getNormalizedOutput();

    /**
     * Returns a fixture that parses the output and forms them into the expected groups
     *
     * <b>NOTE:</b> this is only supported when using {@link org.gradle.api.logging.configuration.ConsoleOutput#Rich}
     */
    GroupedOutputFixture getGroupedOutput();

    /**
     * Stderr of the Gradle execution, normalized to use new-line char as line separator.
     */
    String getError();

    ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder);

    ExecutionResult assertOutputContains(String expectedOutput);

    /**
     * Returns the tasks have been executed in order (includes tasks that were skipped). Note: ignores buildSrc tasks.
     */
    List<String> getExecutedTasks();

    /**
     * Asserts that exactly the given set of tasks have been executed in the given order. Note: ignores buildSrc tasks.
     * Each task path can be either a String or a {@link TaskOrderSpec}.  See {@link TaskOrderSpecs} for common assertions
     * and an explanation of their usage.  Defaults to a {@link TaskOrderSpecs#exact(Object[])} assertion.
     */
    ExecutionResult assertTasksExecutedInOrder(Object... taskPaths);

    /**
     * Asserts that exactly the given set of tasks have been executed in any order. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTasksExecuted(Object... taskPaths);

    /**
     * Asserts that the provided tasks were executed in the given order.  Each task path can be either a String
     * or a {@link TaskOrderSpec}.  See {@link TaskOrderSpecs} for common assertions and an explanation of their usage.
     * Defaults to a {@link TaskOrderSpecs#exact(Object[])} assertion.
     */
    ExecutionResult assertTaskOrder(Object... taskPaths);

    /**
     * Returns the tasks that were skipped, in an undefined order. Note: ignores buildSrc tasks.
     */
    Set<String> getSkippedTasks();

    /**
     * Asserts that exactly the given set of tasks have been skipped. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTasksSkipped(Object... taskPaths);

    /**
     * Asserts the given task has been skipped. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTaskSkipped(String taskPath);

    /**
     * Asserts that exactly the given set of tasks have not been skipped. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTasksNotSkipped(Object... taskPaths);

    /**
     * Asserts that the given task has not been skipped. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTaskNotSkipped(String taskPath);
}
