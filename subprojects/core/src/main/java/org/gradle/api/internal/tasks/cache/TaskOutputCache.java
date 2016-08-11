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

package org.gradle.api.internal.tasks.cache;

import java.io.IOException;

/**
 * Cache protocol interface to be implemented by task output cache backends.
 */
public interface TaskOutputCache {
    /**
     * Load the cached task output corresponding to the given task cache key. The {@code reader} will be called if an entry is found in the cache.
     * @param key the cache key.
     * @param reader the reader to read the data corresponding to the cache key.
     * @return {@code true} if an entry was found, {@code false} otherwise.
     * @throws IOException if an I/O error occurs.
     */
    boolean load(TaskCacheKey key, TaskOutputReader reader) throws IOException;

    /**
     * Store the task output with the given cache key. The {@code writer} will be called to actually write the data.
     * @param key the cache key.
     * @param writer the writer to write the data corresponding to the cache key.
     * @throws IOException if an I/O error occurs.
     */
    void store(TaskCacheKey key, TaskOutputWriter output) throws IOException;

    /**
     * Returns a description for the cache.
     */
    String getDescription();
}
