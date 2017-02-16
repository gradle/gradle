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

package org.gradle.caching.internal.tasks;

import com.google.common.base.Functions;
import com.google.common.collect.Maps;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskState;
import org.gradle.caching.internal.tasks.statistics.TaskExecutionStatistics;
import org.gradle.caching.internal.tasks.statistics.TaskExecutionStatisticsListener;
import org.gradle.initialization.BuildCompletionListener;

import java.util.Arrays;
import java.util.Map;

public class TaskExecutionStatisticsEventAdapter implements BuildCompletionListener, TaskExecutionListener {
    private final TaskExecutionStatisticsListener listener;
    private final Map<TaskExecutionOutcome, Integer> taskCounts = Maps.newEnumMap(
        Maps.toMap(Arrays.asList(TaskExecutionOutcome.values()), Functions.constant(0))
    );
    private int cacheMissCount;

    public TaskExecutionStatisticsEventAdapter(TaskExecutionStatisticsListener listener) {
        this.listener = listener;
    }

    @Override
    public void completed() {
        listener.buildFinished(new TaskExecutionStatistics(taskCounts, cacheMissCount));
    }

    @Override
    public void beforeExecute(Task task) {
        // do nothing
    }

    @Override
    public void afterExecute(Task task, TaskState state) {
        TaskStateInternal stateInternal = (TaskStateInternal) state;
        TaskExecutionOutcome outcome = stateInternal.getOutcome();
        taskCounts.put(outcome, taskCounts.get(outcome) + 1);
        if (outcome == TaskExecutionOutcome.EXECUTED && stateInternal.getTaskOutputCaching().isEnabled()) {
            cacheMissCount++;
        }
    }
}
