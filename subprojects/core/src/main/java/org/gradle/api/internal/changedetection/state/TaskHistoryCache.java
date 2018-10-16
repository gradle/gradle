/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.cache.PersistentIndexedCache;

import javax.annotation.Nullable;

public class TaskHistoryCache {
    private final PersistentIndexedCache<String, HistoricalTaskExecution> cache;

    public TaskHistoryCache(TaskHistoryStore cacheAccess, TaskExecutionFingerprintSerializer serializer) {
        this.cache = cacheAccess.createCache(
            "taskHistory",
            String.class,
            serializer,
            10000,
            false
        );
    }

    @Nullable
    public HistoricalTaskExecution get(String key) {
        return cache.get(key);
    }

    public void put(String key, HistoricalTaskExecution value) {
        cache.put(key, value);
    }
}
