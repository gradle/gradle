/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskExecutionException;

public class DefaultTaskExecuter implements TaskExecuter {
    private static Logger logger = Logging.getLogger(DefaultTaskExecuter.class);
    private final TaskActionListener listener;

    public DefaultTaskExecuter(TaskActionListener listener) {
        this.listener = listener;
    }

    public void execute(TaskInternal task, TaskStateInternal state) {
        listener.beforeActions(task);
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(task.getClass().getClassLoader());
        state.setExecuting(true);
        try {
            GradleException failure = executeActions(task, state);
            state.executed(failure);
        } finally {
            state.setExecuting(false);
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            listener.afterActions(task);
        }
    }

    private GradleException executeActions(TaskInternal task, TaskStateInternal state) {
        logger.debug("Executing actions for {}.", task);
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
                return new TaskExecutionException(task, t);
            } finally {
                task.getStandardOutputCapture().stop();
            }
        }
        return null;
    }
}
