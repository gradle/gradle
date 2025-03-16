/*
 * Copyright 2024 the original author or authors.
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

/**
 * Implements all methods of {@link ExecutionResult} by delegating another result instance.
 */
public interface DelegatingExecutionResult extends ExecutionResult {

    /**
     * The delegate result instance.
     */
    ExecutionResult getDelegate();

    @Override
    default String getOutput() {
        return getDelegate().getOutput();
    }

    @Override
    default String getNormalizedOutput() {
        return getDelegate().getNormalizedOutput();
    }

    @Override
    default String getFormattedOutput() {
        return getDelegate().getFormattedOutput();
    }

    @Override
    default String getPlainTextOutput() {
        return getDelegate().getPlainTextOutput();
    }

    @Override
    default GroupedOutputFixture getGroupedOutput() {
        return getDelegate().getGroupedOutput();
    }

    @Override
    default ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder) {
        getDelegate().assertOutputEquals(expectedOutput, ignoreExtraLines, ignoreLineOrder);
        return this;
    }

    @Override
    default ExecutionResult assertNotOutput(String expectedOutput) {
        getDelegate().assertNotOutput(expectedOutput);
        return this;
    }

    @Override
    default ExecutionResult assertOutputContains(String expectedOutput) {
        getDelegate().assertOutputContains(expectedOutput);
        return this;
    }

    @Override
    default ExecutionResult assertContentContains(String content, String expectedOutput, String label) {
        getDelegate().assertContentContains(content, expectedOutput, label);
        return null;
    }

    @Override
    default ExecutionResult assertHasPostBuildOutput(String expectedOutput) {
        getDelegate().assertHasPostBuildOutput(expectedOutput);
        return this;
    }

    @Override
    default ExecutionResult assertNotPostBuildOutput(String expectedOutput) {
        getDelegate().assertNotPostBuildOutput(expectedOutput);
        return this;
    }

    @Override
    default boolean hasErrorOutput(String expectedOutput) {
        return getDelegate().hasErrorOutput(expectedOutput);
    }

    @Override
    default ExecutionResult assertHasErrorOutput(String expectedOutput) {
        getDelegate().assertHasErrorOutput(expectedOutput);
        return this;
    }

    @Override
    default String getError() {
        return getDelegate().getError();
    }

    @Override
    default String getOutputLineThatContains(String text) {
        return getDelegate().getOutputLineThatContains(text);
    }

    @Override
    default String getPostBuildOutputLineThatContains(String text) {
        return getDelegate().getPostBuildOutputLineThatContains(text);
    }

    @Override
    default ExecutionResult getIgnoreBuildSrc() {
        return getDelegate().getIgnoreBuildSrc();
    }

    @Override
    default ExecutionResult assertTasksExecutedInOrder(Object... taskPaths) {
        return getDelegate().assertTasksExecutedInOrder(taskPaths);
    }

    @Override
    default ExecutionResult assertTasksExecuted(Object... taskPaths) {
        return getDelegate().assertTasksExecuted(taskPaths);
    }

    @Override
    default ExecutionResult assertTaskExecuted(String taskPath) {
        return getDelegate().assertTaskExecuted(taskPath);
    }

    @Override
    default ExecutionResult assertTasksExecutedAndNotSkipped(Object... taskPaths) {
        return getDelegate().assertTasksExecutedAndNotSkipped(taskPaths);
    }

    @Override
    default ExecutionResult assertTaskNotExecuted(String taskPath) {
        return getDelegate().assertTaskNotExecuted(taskPath);
    }

    @Override
    default ExecutionResult assertTaskOrder(Object... taskPaths) {
        return getDelegate().assertTaskOrder(taskPaths);
    }

    @Override
    default ExecutionResult assertTasksSkipped(Object... taskPaths) {
        return getDelegate().assertTasksSkipped(taskPaths);
    }

    @Override
    default ExecutionResult assertTaskSkipped(String taskPath) {
        return getDelegate().assertTaskSkipped(taskPath);
    }

    @Override
    default ExecutionResult assertTasksNotSkipped(Object... taskPaths) {
        return getDelegate().assertTasksNotSkipped(taskPaths);
    }

    @Override
    default ExecutionResult assertTaskNotSkipped(String taskPath) {
        return getDelegate().assertTaskNotSkipped(taskPath);
    }

    @Override
    default void assertResultVisited() {
        getDelegate().assertResultVisited();
    }
}
