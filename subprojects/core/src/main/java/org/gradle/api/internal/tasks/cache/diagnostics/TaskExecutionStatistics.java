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

package org.gradle.api.internal.tasks.cache.diagnostics;

import java.util.ArrayList;
import java.util.List;

public class TaskExecutionStatistics {
    private final List<TaskExecutionEvent> events = new ArrayList<TaskExecutionEvent>();
    private int executedTasksCount = 0;
    private int cachedTasksCount = 0;
    private int cacheableTasksCount = 0;

    public void event(TaskExecutionEvent event) {
        events.add(event);
        executedTasksCount++;
        if (event.isCached()) {
            cachedTasksCount++;
        }
        if (event.isTaskCacheable()) {
            cacheableTasksCount++;
        }
    }

    public int getExecutedTasksCount() {
        return executedTasksCount;
    }

    public int getCachedTasksCount() {
        return cachedTasksCount;
    }

    public int getCacheableTasksCount() {
        return cacheableTasksCount;
    }
}
