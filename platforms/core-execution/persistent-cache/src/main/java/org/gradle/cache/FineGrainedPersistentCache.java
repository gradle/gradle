/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.cache;

import java.io.Closeable;
import java.util.function.Supplier;

/**
 * A persistent cache that locks a cache per key, allowing multiple processes to work with the cache concurrently.
 *
 * Currently, the only supported strategy is eager release of locks, so that the locks are released as soon as the action is complete.
 *
 * Additionally, instead of per key locks we use multiple file locks, similar strategy as it's used for {@link com.google.common.util.concurrent.Striped}.
 */
public interface FineGrainedPersistentCache extends Closeable, CleanableStore, HasCleanupAction {

    FineGrainedPersistentCache open();

    /**
     * Performs some work against the cache. Acquires exclusive locks on the appropriate resources, so that the given action is the only action to execute across all processes (including this one). Releases the locks and all resources at the end of the action.
     */
    <T> T useCache(String key, Supplier<? extends T> action);

    /**
     * Performs some work against the cache key. Acquires exclusive locks on the appropriate resources, so that the given action is the only action to execute across all processes (including this one). Releases the locks and all resources at the end of the action.
     */
    void useCache(String key, Runnable action);

    /**
     * Closes this cache, blocking until all operations are complete.
     */
    @Override
    void close();
}
