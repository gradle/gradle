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

import java.util.List;
import java.util.Set;

public interface ExecutionResult {
    String getOutput();

    String getError();

    ExecutionResult assertOutputEquals(String expectedOutput, boolean ignoreExtraLines, boolean ignoreLineOrder);

    /**
     * Returns the tasks have been executed in order (includes tasks that were skipped). Note: ignores buildSrc tasks.
     */
    List<String> getExecutedTasks();
    
    /**
     * Asserts that exactly the given set of tasks have been executed in the given order. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTasksExecuted(String... taskPaths);

    /**
     * Returns the tasks that were skipped, in an undefined order. Note: ignores buildSrc tasks.
     */
    Set<String> getSkippedTasks();
    
    /**
     * Asserts that exactly the given set of tasks have been skipped. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTasksSkipped(String... taskPaths);

    /**
     * Asserts the given task has been skipped. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTaskSkipped(String taskPath);

    /**
     * Asserts that exactly the given set of tasks have not been skipped. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTasksNotSkipped(String... taskPaths);

    /**
     * Asserts that the given task has not been skipped. Note: ignores buildSrc tasks.
     */
    ExecutionResult assertTaskNotSkipped(String taskPath);
}
