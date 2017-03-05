/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.progress;

import com.google.common.base.Functions;
import com.google.common.collect.Maps;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskState;

import java.util.Arrays;
import java.util.Map;

public class TaskOutcomeStatisticsFormatter {
    private final Map<TaskExecutionOutcome, Integer> taskCounts = Maps.newEnumMap(
        Maps.toMap(Arrays.asList(TaskExecutionOutcome.values()), Functions.constant(0))
    );
    private int allTasksCount;

    public String incrementAndGetProgress(TaskState state) {
        recordTaskOutcome(state);

        int tasksAvoided = 0;
        int tasksExecuted = 0;
        for (TaskExecutionOutcome outcome : TaskExecutionOutcome.values()) {
            switch (outcome) {
                case EXECUTED: tasksExecuted += taskCounts.get(outcome); break;
                default: tasksAvoided += taskCounts.get(outcome);
            }
        }
        return " [" + formatPercentage(tasksAvoided, allTasksCount) + " AVOIDED, " + formatPercentage(tasksExecuted, allTasksCount) + " EXECUTED]";
    }

    private String formatPercentage(int num, int total) {
        return String.valueOf(Math.round(num * 100.0 / total)) + '%';
    }

    private void recordTaskOutcome(final TaskState state) {
        TaskStateInternal stateInternal = (TaskStateInternal) state;
        TaskExecutionOutcome outcome = stateInternal.getOutcome();
        taskCounts.put(outcome, taskCounts.get(outcome) + 1);
        allTasksCount++;
    }
}
