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

package org.gradle.api.internal.tasks.cache.statistics;

import com.google.common.base.Functions;
import com.google.common.collect.Maps;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;

import java.util.Arrays;
import java.util.EnumMap;

public class TaskExecutionStatistics {
    private final EnumMap<TaskExecutionOutcome, Integer> taskCounts = new EnumMap<TaskExecutionOutcome, Integer>(
        Maps.toMap(Arrays.asList(TaskExecutionOutcome.values()), Functions.constant(0))
    );
    private int allTasksCount;
    private int cacheableTasksCount;

    public void taskStatus(TaskExecutionOutcome outcome, boolean cacheable) {
        allTasksCount++;
        taskCounts.put(outcome, taskCounts.get(outcome) + 1);
        if (cacheable) {
            cacheableTasksCount++;
        }
    }

    public int getAllTasksCount() {
        return allTasksCount;
    }

    public int getTasksCount(TaskExecutionOutcome outcome) {
        return taskCounts.get(outcome);
    }

    public int getCacheableTasksCount() {
        return cacheableTasksCount;
    }
}
