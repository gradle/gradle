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

import org.gradle.api.execution.TaskExecutionResult;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.Task;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.GradleScriptException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.StartParameter;

public class DefaultTaskExecuter implements TaskExecuter {
    private static Logger logger = Logging.getLogger(DefaultTaskExecuter.class);
    private final StartParameter startParameter;

    public DefaultTaskExecuter(StartParameter startParameter) {
        this.startParameter = startParameter;
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
            String skipMessage = "SKIPPED";
            logger.lifecycle("{} {}", task.getPath(), skipMessage);
            return new TaskExecutionResultImpl(task, null, skipMessage);
        }

        if (!startParameter.isNoOpt()) {
            boolean skip;
            try {
                skip = !task.getOnlyIf().isSatisfiedBy(task);
            } catch (Throwable t) {
                Throwable failure = new GradleScriptException(String.format("Could not evaluate onlyIf predicate for %s.", task), t,
                        ((ProjectInternal) task.getProject()).getBuildScriptSource());
                return new TaskExecutionResultImpl(task, failure, null);
            }

            if (skip) {
                String skipMessage = "SKIPPED as onlyIf is false";
                logger.lifecycle("{} {}", task.getPath(), skipMessage);
                return new TaskExecutionResultImpl(task, null, skipMessage);
            }
        }

        logger.lifecycle(task.getPath());
        state.setExecuting(true);
        try {
            GradleException failure = executeActions(task, state);
            return new TaskExecutionResultImpl(task, failure, null);
        } finally {
            state.setExecuting(false);
        }
    }

    private GradleException executeActions(TaskInternal task, TaskState state) {
        for (Action<? super Task> action : task.getActions()) {
            state.setDidWork(true);
            task.getStandardOutputCapture().start();
            try {
                action.execute(task);
            } catch (StopActionException e) {
                // Ignore
                logger.debug("Action stopped by some action with message: {}", e.getMessage());
            } catch (StopExecutionException e) {
                logger.info("Execution stopped by some action with message: {}", e.getMessage());
                break;
            } catch (Throwable t) {
                return new GradleScriptException(String.format("Execution failed for %s.", task), t,
                        ((ProjectInternal) task.getProject()).getBuildScriptSource());
            }
            finally {
                task.getStandardOutputCapture().stop();
            }
        }
        return null;
    }

    private static class TaskExecutionResultImpl implements TaskExecutionResult {
        private final Task task;
        private final Throwable failure;
        private final String skipMessage;

        public TaskExecutionResultImpl(Task task, Throwable failure, String skipMessage) {
            this.task = task;
            this.failure = failure;
            this.skipMessage = skipMessage;
        }

        public Throwable getFailure() {
            return failure;
        }

        public void rethrowFailure() {
            if (failure == null) {
                return;
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            throw new GradleException(String.format("Task %s failed with an exception.", task), failure);
        }

        public String getSkipMessage() {
            return skipMessage;
        }
    }
}
