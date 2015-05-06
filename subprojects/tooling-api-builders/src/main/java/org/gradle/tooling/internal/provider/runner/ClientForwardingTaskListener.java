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
import org.gradle.api.execution.internal.InternalTaskExecutionListener;
import org.gradle.api.execution.internal.TaskOperationInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.tooling.internal.provider.events.*;

import java.util.Collections;

/**
 * Task listener that forwards all receiving events to the client via the provided {@code BuildEventConsumer} instance.
 *
 * @since 2.5
 */
class ClientForwardingTaskListener implements InternalTaskExecutionListener {

    private final BuildEventConsumer eventConsumer;

    ClientForwardingTaskListener(BuildEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void beforeExecute(TaskOperationInternal taskOperation) {
        eventConsumer.dispatch(new DefaultTaskStartedProgressEvent(taskOperation.getState().getStartTime(), toTaskDescriptor(taskOperation)));
    }

    @Override
    public void afterExecute(TaskOperationInternal taskOperation) {
        eventConsumer.dispatch(new DefaultTaskFinishedProgressEvent(taskOperation.getState().getEndTime(), toTaskDescriptor(taskOperation), toTaskResult(taskOperation.getTask())));
    }

    private static DefaultTaskDescriptor toTaskDescriptor(TaskOperationInternal taskOperation) {
        TaskInternal task = taskOperation.getTask();
        return new DefaultTaskDescriptor(
            taskOperation.getId(),
            task.getName(),
            task.getDescription(),
            task.getPath(),
            taskOperation.getParentId());
    }

    private static AbstractTaskResult toTaskResult(Task task) {
        TaskStateInternal state = (TaskStateInternal) task.getState();
        long startTime = state.getStartTime();
        long endTime = state.getEndTime();

        if (state.getUpToDate()) {
            return new DefaultTaskSuccessResult(startTime, endTime, true);
        } else if (state.getSkipped()) {
            return new DefaultTaskSkippedResult(startTime, endTime, state.getSkipMessage());
        } else {
            Throwable failure = state.getFailure();
            if (failure == null) {
                return new DefaultTaskSuccessResult(startTime, endTime, false);
            } else {
                return new DefaultTaskFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)));
            }
        }
    }

}
