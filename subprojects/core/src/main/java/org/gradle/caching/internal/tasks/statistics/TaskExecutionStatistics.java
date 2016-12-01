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

package org.gradle.caching.internal.tasks.statistics;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;

import java.util.Map;

public class TaskExecutionStatistics {
    private final Map<TaskExecutionOutcome, Integer> taskCounts;
    private final int allTasksCount;
    private final int cacheMissCount;

    public TaskExecutionStatistics(Map<TaskExecutionOutcome, Integer> taskCounts, int cacheMissCount) {
        this.taskCounts = ImmutableMap.copyOf(taskCounts);
        int allTasksCount = 0;
        for (Integer taskCount : taskCounts.values()) {
            allTasksCount += taskCount;
        }
        this.allTasksCount = allTasksCount;
        this.cacheMissCount = cacheMissCount;
    }

    /**
     * Returns the number of all tasks in the build.
     */
    public int getAllTasksCount() {
        return allTasksCount;
    }

    /**
     * Returns the number of tasks with the given outcome.
     */
    public int getTasksCount(TaskExecutionOutcome outcome) {
        Integer count = taskCounts.get(outcome);
        return count == null ? 0 : count;
    }

    /**
     * Returns the number of tasks that were cacheable and whose outcome was {@link TaskExecutionOutcome#EXECUTED}.
     * These are the tasks that could potentially have been loaded from cache, if we had a cached result for them, but we didn't.
     */
    public int getCacheMissCount() {
        return cacheMissCount;
    }
}
