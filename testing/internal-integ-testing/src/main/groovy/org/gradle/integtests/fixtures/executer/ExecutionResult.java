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

public interface ExecutionResult {
    /**
     * Returns a copy of this result that ignores `buildSrc` tasks.
     */
    ExecutionResult getIgnoreBuildSrc();

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
     *     <li>Removes warning about running using configure on demand or parallel execution.</li>
     *     <li>Removes notice about starting or stopping the daemon.</li>
     *     <li>Normalizes build time to 0 seconds.
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
     * Retrieves the first output line that contains the passed in text.
     *
     * Fails with an assertion if no output line contains the given text.
     *
     * @param text the text to match
     */
    String getOutputLineThatContains(String text);

    /**
     * Retrieves the first output line in the post build output that contains the passed in text.
     *
     * Fails with an assertion if no post build output line contains the given text.
     *
     * @param text the text to match
     */
    String getPostBuildOutputLineThatContains(String text);

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
     * Assert that the given message does not appear after the build result message.
     *
     * @param expectedOutput The expected log message, with line endings normalized to a newline character.
     */
    ExecutionResult assertNotPostBuildOutput(String expectedOutput);

    /**
     * Asserts that exactly the given set of tasks have been executed in the given order.
     * Each task path can be either a String or a {@link TaskOrderSpec}.  See {@link TaskOrderSpecs} for common assertions
     * and an explanation of their usage.  Defaults to a {@link TaskOrderSpecs#exact(Object[])} assertion.
     */
    ExecutionResult assertTasksExecutedInOrder(Object... taskPaths);

    /**
     * Asserts that exactly the given set of tasks have been executed in any order.
     */
    ExecutionResult assertTasksExecuted(Object... taskPaths);

    /**
     * Asserts that the given task has been executed.
     */
    ExecutionResult assertTaskExecuted(String taskPath);

    /**
     * Asserts that exactly the given set of tasks have been executed in any order and none of the tasks were skipped.
     */
    ExecutionResult assertTasksExecutedAndNotSkipped(Object... taskPaths);

    /**
     * Asserts that the given task has not been executed.
     */
    ExecutionResult assertTaskNotExecuted(String taskPath);

    /**
     * Asserts that the provided tasks were executed in the given order.  Each task path can be either a String
     * or a {@link TaskOrderSpec}.  See {@link TaskOrderSpecs} for common assertions and an explanation of their usage.
     * Defaults to a {@link TaskOrderSpecs#exact(Object[])} assertion.
     */
    ExecutionResult assertTaskOrder(Object... taskPaths);

    /**
     * Asserts that exactly the given set of tasks have been skipped.
     */
    ExecutionResult assertTasksSkipped(Object... taskPaths);

    /**
     * Asserts the given task has been skipped.
     */
    ExecutionResult assertTaskSkipped(String taskPath);

    /**
     * Asserts that exactly the given set of tasks have not been skipped.
     */
    ExecutionResult assertTasksNotSkipped(Object... taskPaths);

    /**
     * Asserts that the given task has not been skipped.
     */
    ExecutionResult assertTaskNotSkipped(String taskPath);

    /**
     * Asserts that the important information from this result has been verified by the test.
     */
    void assertResultVisited();
}
