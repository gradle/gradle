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

import java.util.Arrays;
import java.util.Map;

public class TaskExecutionStatistics {
    private final Map<TaskExecutionOutcome, Integer> taskCounts = Maps.newHashMap(Maps.toMap(Arrays.asList(TaskExecutionOutcome.values()), Functions.constant(0)));
    private int allTasksCount;
    private int cacheableTasksCount;

    public void event(TaskExecutionOutcome outcome) {
        allTasksCount++;
        taskCounts.put(outcome, taskCounts.get(outcome) + 1);
    }

    public void taskCacheable(boolean cacheable) {
        if (cacheable) {
            cacheableTasksCount++;
        }
    }

    public int getAllTasksCount() {
        return allTasksCount;
    }

    public int getCacheableTasksCount() {
        return cacheableTasksCount;
    }

    public int getUpToDateTasksCount() {
        return taskCounts.get(TaskExecutionOutcome.UP_TO_DATE);
    }

    public int getFromCacheTasksCount() {
        return taskCounts.get(TaskExecutionOutcome.FROM_CACHE);
    }

    public int getSkippedTasksCount() {
        return taskCounts.get(TaskExecutionOutcome.SKIPPED);
    }

    public int getExecutedTasksCount() {
        return taskCounts.get(TaskExecutionOutcome.EXECUTED);
    }
}
