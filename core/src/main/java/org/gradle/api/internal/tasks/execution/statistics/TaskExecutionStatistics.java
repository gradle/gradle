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
package org.gradle.api.internal.tasks.execution.statistics;

import static com.google.common.base.Preconditions.checkArgument;

public class TaskExecutionStatistics {
    private final int executedTasksCount;
    private final int fromCacheTaskCount;
    private final int upToDateTaskCount;

    public TaskExecutionStatistics(int executedTasksCount, int fromCacheTaskCount, int upToDateTaskCount) {
        checkArgument(executedTasksCount >= 0, "executedTasksCount must be non-negative");
        checkArgument(fromCacheTaskCount >= 0, "fromCacheTaskCount must be non-negative");
        checkArgument(upToDateTaskCount >= 0, "upToDateTaskCount must be non-negative");
        this.executedTasksCount = executedTasksCount;
        this.fromCacheTaskCount = fromCacheTaskCount;
        this.upToDateTaskCount = upToDateTaskCount;
    }

    public int getExecutedTasksCount() {
        return executedTasksCount;
    }

    public int getFromCacheTaskCount() {
        return fromCacheTaskCount;
    }

    public int getUpToDateTaskCount() {
        return upToDateTaskCount;
    }

    public int getTotalTaskCount() {
        return executedTasksCount + fromCacheTaskCount + upToDateTaskCount;
    }
}
