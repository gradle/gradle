/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.tooling.internal.provider.events.AbstractTaskResult;
import org.gradle.tooling.internal.provider.events.DefaultFailure;
import org.gradle.tooling.internal.provider.events.DefaultTaskDescriptor;
import org.gradle.tooling.internal.provider.events.DefaultTaskFailureResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskFinishedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSkippedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSkippedResult;
import org.gradle.tooling.internal.provider.events.DefaultTaskStartedProgressEvent;
import org.gradle.tooling.internal.provider.events.DefaultTaskSuccessResult;

/**
 * Task listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingTaskListener implements TaskExecutionListener {

    private final BuildEventConsumer eventConsumer;

    ClientForwardingTaskListener(BuildEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }


    private static DefaultTaskDescriptor adapt(Task taskDescriptor) {
        return new DefaultTaskDescriptor(
                taskDescriptor.getPath(),
                taskDescriptor.getName(),
                taskDescriptor.getDescription(),
                taskDescriptor.getProject().getPath()
        );
    }


    private static AbstractTaskResult adaptTaskResult(Task task) {
        TaskState state = task.getState();
        if (isSkipped(state)) {
            return new DefaultTaskSkippedResult(0, 0, !state.getDidWork());
        }
        if (state.getDidWork()) {
            return new DefaultTaskSuccessResult(0L, 0L);
        }
        Throwable failure = state.getFailure();
        return new DefaultTaskFailureResult(0, 0, failure != null ? DefaultFailure.fromThrowable(failure) : null);
    }

    private static boolean isSkipped(TaskState state) {
        return state.getSkipped() || (!state.getDidWork() && state.getExecuted());
    }

    /**
     * This method is called immediately before a task is executed.
     *
     * @param task The task about to be executed. Never null.
     */
    @Override
    public void beforeExecute(Task task) {
        eventConsumer.dispatch(new DefaultTaskStartedProgressEvent(System.currentTimeMillis(), adapt(task)));
    }

    /**
     * This method is call immediately after a task has been executed. It is always called, regardless of whether the task completed successfully, or failed with an exception.
     *
     * @param task The task which was executed. Never null.
     * @param state The task state. If the task failed with an exception, the exception is available in this
     */
    @Override
    public void afterExecute(Task task, TaskState state) {
        long eventTime = System.currentTimeMillis();
        if (isSkipped(state)) {
            eventConsumer.dispatch(new DefaultTaskSkippedProgressEvent(eventTime, adapt(task), adaptTaskResult(task)));
        } else {
            eventConsumer.dispatch(new DefaultTaskFinishedProgressEvent(eventTime, adapt(task), adaptTaskResult(task)));
        }
    }
}
