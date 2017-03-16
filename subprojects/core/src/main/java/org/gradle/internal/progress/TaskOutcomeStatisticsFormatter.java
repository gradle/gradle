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

import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskState;

public class TaskOutcomeStatisticsFormatter {
    private int executedTasksCount;
    private int allTasksCount;

    public String incrementAndGetProgress(TaskState state) {
        recordTaskOutcome(state);

        final long executedPercentage = Math.round(executedTasksCount * 100.0 / allTasksCount);
        return " [" + (100 - executedPercentage) + "% AVOIDED, " + executedPercentage + "% DONE]";
    }

    private void recordTaskOutcome(final TaskState state) {
        TaskStateInternal stateInternal = (TaskStateInternal) state;
        TaskExecutionOutcome outcome = stateInternal.getOutcome();
        if (outcome == TaskExecutionOutcome.EXECUTED) {
            executedTasksCount++;
        }
        allTasksCount++;
    }
}
