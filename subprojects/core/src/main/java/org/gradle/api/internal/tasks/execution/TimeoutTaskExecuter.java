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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.timeout.Timeout;
import org.gradle.api.internal.tasks.timeout.TimeoutHandler;
import org.gradle.api.provider.Property;

import java.time.Duration;

/**
 * A task executer that interrupts a task if it exceeds its timeout.
 */
public class TimeoutTaskExecuter implements TaskExecuter {
    private final TaskExecuter delegate;
    private final TimeoutHandler timeoutHandler;

    public TimeoutTaskExecuter(TaskExecuter delegate, TimeoutHandler timeoutHandler) {
        this.delegate = delegate;
        this.timeoutHandler = timeoutHandler;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        Property<Duration> timeoutProperty = task.getTimeout();
        if (timeoutProperty.isPresent()) {
            Duration timeout = timeoutProperty.get();
            if (timeout.isNegative()) {
                throw new InvalidUserDataException("Timeout of " + task + " must be positive, but was " + timeout.toString().substring(2));
            } else {
                executeWithTimeout(task, state, context, timeout);
            }
        } else {
            delegate.execute(task, state, context);
        }
    }

    private void executeWithTimeout(TaskInternal task, TaskStateInternal state, TaskExecutionContext context, Duration timeout) {
        Timeout taskTimeout = timeoutHandler.start(Thread.currentThread(), timeout);
        try {
            delegate.execute(task, state, context);
        } finally {
            taskTimeout.stop();
            if (taskTimeout.timedOut()) {
                state.setAborted(task + " exceeded its timeout");
                Thread.interrupted();
            }
        }
    }
}
