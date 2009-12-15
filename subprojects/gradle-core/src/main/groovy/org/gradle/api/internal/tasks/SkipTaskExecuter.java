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
package org.gradle.api.internal.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class SkipTaskExecuter implements TaskExecuter {
    private static Logger logger = Logging.getLogger(SkipTaskExecuter.class);
    private final TaskExecuter executer;

    public SkipTaskExecuter(TaskExecuter executer) {
        this.executer = executer;
    }

    public TaskExecutionResult execute(TaskInternal task, TaskState state) {
        logger.debug("Starting to execute {}", task);
        try {
            return doExecute(task, state);
        } finally {
            state.setExecuted(true);
            logger.debug("Finished executing {}", task);
        }
    }

    private TaskExecutionResult doExecute(TaskInternal task, TaskState state) {
        if (!task.getEnabled()) {
            logger.info("Skipping execution as task is disabled.");
            return new DefaultTaskExecutionResult(task, null, "SKIPPED");
        }

        boolean skip;
        try {
            skip = !task.getOnlyIf().isSatisfiedBy(task);
        } catch (Throwable t) {
            Throwable failure = new GradleException(String.format("Could not evaluate onlyIf predicate for %s.", task), t);
            return new DefaultTaskExecutionResult(task, failure, null);
        }

        if (skip) {
            logger.info("Skipping execution as task onlyIf is false.");
            return new DefaultTaskExecutionResult(task, null, "SKIPPED as onlyIf is false");
        }

        return executer.execute(task, state);
    }
}
