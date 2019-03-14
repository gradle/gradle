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
     *
     * <p>You should avoid using this method as it couples the tests to a particular layout for the console. Instead use the more descriptive assertion methods on this class.</p>
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
     *
     * <p>You should avoid using this method as it couples the tests to a particular layout for the console. Instead use the more descriptive assertion methods.</p>
     */
    String getNormalizedOutput();

    /**
     * Stdout of the Gradle execution, with ANSI characters interpreted and text attributes rendered as plain text.
     */
    String getFormattedOutput();

    /**
     * Stdout of the Gradle execution, with ANSI characters interpreted and text attributes discarded.
     */
    String getPlainTextOutput();

    /**
     * Returns a fixture that parses the output and forms them into the expected groups
     */
    GroupedOutputFixture getGroupedOutput();

    /**
     * Stderr of the Gradle execution, normalized to use new-line char as line separator.
     *
     * <p>You should avoid using this method as it couples the tests to a particular layout for the console. Instead use the more descriptive assertion methods.</p>
     */
    String getError();

    /**
     * Asserts that this result includes the given error log message. Does not consider any text in or following the build result message (use {@link #assertHasPostBuildOutput(String)} instead).
     *
     * @param expectedOutput The expected log message, with line endings normalized to a newline character.
     */
    ExecutionResult assertHasErrorOutput(String expectedOutput);

    /**
     * Returns true when this result includes the given error log message. Does not consider any text in or following the build result message (use {@link #assertHasPostBuildOutput(String)} instead).
     *
     * @param expectedOutput The expected log message, with line endings normalized to a newline character.
     */
    boolean hasErrorOutput(String expectedOutput);

    ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder);

    /**
     * Asserts that this result includes the given non-error log message. Does not consider any text in or following the build result message (use {@link #assertHasPostBuildOutput(String)} instead).
     *
     * @param expectedOutput The expected log message, with line endings normalized to a newline character.
     */
    ExecutionResult assertOutputContains(String expectedOutput);

    /**
     * Asserts that the given content includes the given log message.
     *
     * @param content The content to check
     * @param expectedOutput The expected log message, with line endings normalized to a newline character.
     * @param label The label to use when printing a failure
     */
    ExecutionResult assertContentContains(String content, String expectedOutput, String label);

    /**
     * Asserts that this result does not include the given log message anywhere in the build output.
     *
     * @param expectedOutput The expected log message, with line endings normalized to a newline character.
     */
    ExecutionResult assertNotOutput(String expectedOutput);

    /**
     * Assert that the given message appears after the build result message.
     *
     * @param expectedOutput The expected log message, with line endings normalized to a newline character.
     */
    ExecutionResult assertHasPostBuildOutput(String expectedOutput);

    /**
     * Returns the tasks have been executed in order started (includes tasks that were skipped). Asserts that each task appears once only. Note: ignores buildSrc tasks.
     *
     * <p>You should avoid using this method, as as doing so not provide useful context on assertion failure. Instead, use the more descriptive assertion methods
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
     * Asserts that the given task has been executed. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTaskExecuted(String taskPath);

    /**
     * Asserts that exactly the given set of tasks have been executed in any order and none of the tasks were skipped. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTasksExecutedAndNotSkipped(Object... taskPaths);

    /**
     * Asserts that the given task has not been executed. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTaskNotExecuted(String taskPath);

    /**
     * Asserts that the provided tasks were executed in the given order.  Each task path can be either a String
     * or a {@link TaskOrderSpec}.  See {@link TaskOrderSpecs} for common assertions and an explanation of their usage.
     * Defaults to a {@link TaskOrderSpecs#exact(Object[])} assertion.
     */
    ExecutionResult assertTaskOrder(Object... taskPaths);

    /**
     * Returns the tasks that were skipped, in an undefined order. Note: ignores buildSrc tasks.
     *
     * <p>You should avoid using this method, as as doing so not provide useful context on assertion failure. Instead, use the more descriptive assertion methods
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
