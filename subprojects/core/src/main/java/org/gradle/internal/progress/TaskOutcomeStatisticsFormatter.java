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
import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskState;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TaskOutcomeStatisticsFormatter {
    private final Map<TaskExecutionOutcome, Integer> taskCounts = Maps.newEnumMap(
        Maps.toMap(Arrays.asList(TaskExecutionOutcome.values()), Functions.constant(0))
    );
    private final int numTotalTasks;
    private int numCompletedTasks;
    private int numFailedTasks;

    private static final String PREFIX = " [";
    private static final String SUFFIX = "]";
    private static final String OUTCOME_SEPARATOR = ", ";
    private static final String TASKS_COMPLETED_MESSAGE = "TASKS";
    private static final String TASKS_FAILED_MESSAGE = "FAILED";

    public TaskOutcomeStatisticsFormatter(int numTotalTasks) {
        this.numTotalTasks = numTotalTasks;
    }

    public String incrementAndGetProgress(TaskState state) {
        recordTaskOutcome(state);

        List<String> listedOutcomes = new LinkedList<String>();
        listedOutcomes.add(numCompletedTasks + "/" + numTotalTasks + " " + TASKS_COMPLETED_MESSAGE);

        if (numFailedTasks > 0) {
            listedOutcomes.add(numFailedTasks + " " + TASKS_FAILED_MESSAGE);
        }
        Integer tasksFromCache = taskCounts.get(TaskExecutionOutcome.FROM_CACHE);
        if (tasksFromCache > 0) {
            listedOutcomes.add(tasksFromCache + " " + TaskExecutionOutcome.FROM_CACHE.getMessage());
        }
        Integer tasksUpToDate = taskCounts.get(TaskExecutionOutcome.UP_TO_DATE);
        if (tasksUpToDate > 0) {
            listedOutcomes.add(tasksUpToDate + " " + TaskExecutionOutcome.UP_TO_DATE.getMessage());
        }

        return PREFIX + Joiner.on(OUTCOME_SEPARATOR).join(listedOutcomes) + SUFFIX;
    }

    private void recordTaskOutcome(final TaskState state) {
        TaskStateInternal stateInternal = (TaskStateInternal) state;
        TaskExecutionOutcome outcome = stateInternal.getOutcome();
        taskCounts.put(outcome, taskCounts.get(outcome) + 1);
        numCompletedTasks++;
        if (state.getFailure() != null) {
            numFailedTasks++;
        }
    }
}
