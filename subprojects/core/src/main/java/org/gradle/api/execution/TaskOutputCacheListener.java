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

package org.gradle.api.execution;

import org.gradle.api.Task;

/**
 * Listener for events of the Task output cache
 */
public interface TaskOutputCacheListener {
    /**
     * Called when a task has been found in the cache.
     */
    void fromCache(Task task);
    /**
     * Called when a task has been not been found in the cache.
     */
    void notCached(Task task, NotCachedReason reason);

    /**
     * Why something was not cached
     */
    enum NotCachedReason {
        NOT_IN_CACHE(true),
        ERROR_LOADING_CACHE_ENTRY(true),
        MULTIPLE_OUTPUTS(false),
        NO_OUTPUTS(false),
        NOT_CACHEABLE(false);

        private final boolean taskCacheable;

        NotCachedReason(boolean taskCacheable) {
            this.taskCacheable = taskCacheable;
        }

        public boolean isTaskCacheable() {
            return taskCacheable;
        }
    }
}
