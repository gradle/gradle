/*
 * Copyright 2018 the original author or authors.
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

public class ErrorsOnStdoutScrapingExecutionResult implements ExecutionResult {
    private final ExecutionResult delegate;

    public ErrorsOnStdoutScrapingExecutionResult(ExecutionResult delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getOutput() {
        return delegate.getOutput();
    }

    @Override
    public String getNormalizedOutput() {
        return delegate.getNormalizedOutput();
    }

    @Override
    public String getFormattedOutput() {
        return delegate.getFormattedOutput();
    }

    @Override
    public String getPlainTextOutput() {
        return delegate.getPlainTextOutput();
    }

    @Override
    public GroupedOutputFixture getGroupedOutput() {
        return delegate.getGroupedOutput();
    }

    @Override
    public String getError() {
        return delegate.getError();
    }

    @Override
    public ExecutionResult assertHasErrorOutput(String expectedOutput) {
        assertContentContains(getOutput(), expectedOutput, "Build output");
        return this;
    }

    @Override
    public boolean hasErrorOutput(String expectedOutput) {
        return getOutput().contains(expectedOutput);
    }

    @Override
    public ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder) {
        delegate.assertOutputEquals(expectedOutput, ignoreExtraLines, ignoreLineOrder);
        return this;
    }

    @Override
    public ExecutionResult assertOutputContains(String expectedOutput) {
        delegate.assertOutputContains(expectedOutput);
        return this;
    }

    @Override
    public ExecutionResult assertContentContains(String content, String expectedOutput, String label) {
        delegate.assertContentContains(content, expectedOutput, label);
        return this;
    }

    @Override
    public ExecutionResult assertNotOutput(String expectedOutput) {
        delegate.assertNotOutput(expectedOutput);
        return this;
    }

    @Override
    public ExecutionResult assertHasPostBuildOutput(String expectedOutput) {
        delegate.assertHasPostBuildOutput(expectedOutput);
        return this;
    }

    @Override
    public List<String> getExecutedTasks() {
        return delegate.getExecutedTasks();
    }

    @Override
    public ExecutionResult assertTasksExecutedInOrder(Object... taskPaths) {
        delegate.assertTasksExecutedInOrder(taskPaths);
        return this;
    }

    @Override
    public ExecutionResult assertTasksExecuted(Object... taskPaths) {
        delegate.assertTasksExecuted(taskPaths);
        return this;
    }

    @Override
    public ExecutionResult assertTasksExecutedAndNotSkipped(Object... taskPaths) {
        delegate.assertTasksExecutedAndNotSkipped(taskPaths);
        return this;
    }

    @Override
    public ExecutionResult assertTaskExecuted(String taskPath) {
        delegate.assertTaskExecuted(taskPath);
        return this;
    }

    @Override
    public ExecutionResult assertTaskNotExecuted(String taskPath) {
        delegate.assertTaskNotExecuted(taskPath);
        return this;
    }

    @Override
    public ExecutionResult assertTaskOrder(Object... taskPaths) {
        delegate.assertTaskOrder(taskPaths);
        return this;
    }

    @Override
    public Set<String> getSkippedTasks() {
        return delegate.getSkippedTasks();
    }

    @Override
    public ExecutionResult assertTasksSkipped(Object... taskPaths) {
        delegate.assertTasksSkipped(taskPaths);
        return this;
    }

    @Override
    public ExecutionResult assertTaskSkipped(String taskPath) {
        delegate.assertTasksSkipped(taskPath);
        return this;
    }

    @Override
    public ExecutionResult assertTasksNotSkipped(Object... taskPaths) {
        delegate.assertTasksNotSkipped(taskPaths);
        return this;
    }

    @Override
    public ExecutionResult assertTaskNotSkipped(String taskPath) {
        delegate.assertTasksNotSkipped(taskPath);
        return this;
    }
}
