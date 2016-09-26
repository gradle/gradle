/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.cache;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.cache.statistics.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.cache.statistics.TaskExecutionStatistics;
import org.gradle.api.internal.tasks.cache.statistics.TaskExecutionStatisticsListener;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.BuildCompletionListener;

public class TaskExecutionEventAdapter implements BuildCompletionListener, TaskExecutionListener {
    private final TaskExecutionStatistics statistics;
    private final TaskExecutionStatisticsListener listener;

    public TaskExecutionEventAdapter(TaskExecutionStatisticsListener listener) {
        this.listener = listener;
        statistics = new TaskExecutionStatistics();
    }

    @Override
    public void completed() {
        listener.buildFinished(statistics);
    }

    @Override
    public void beforeExecute(Task task) {
        // do nothing
    }

    @Override
    public void afterExecute(Task task, TaskState state) {
        if (state.getUpToDate()) {
            if ("FROM-CACHE".equals(state.getSkipMessage())) {
                statistics.event(TaskExecutionOutcome.FROM_CACHE);
            } else {
                statistics.event(TaskExecutionOutcome.UP_TO_DATE);
            }
        } else {
            if (state.getSkipped()) {
                statistics.event(TaskExecutionOutcome.SKIPPED);
            } else {
                statistics.event(TaskExecutionOutcome.EXECUTED);
            }
        }
        statistics.taskCacheable(((TaskStateInternal) state).isCacheable());
    }
}
