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

import org.jspecify.annotations.NullMarked;

import java.io.Closeable;
import java.util.function.Supplier;

/**
 * A persistent cache that locks fine-grained: it have one lock per key instead of one lock for the whole cache.
 *
 * Cache will always use {@link FileLockManager.LockMode#OnDemandEagerRelease} lock mode for key locks.
 */
@NullMarked
public interface FineGrainedPersistentCache extends Closeable, CleanableStore, HasCleanupAction {

    /**
     * Opens this cache and returns self.
     */
    FineGrainedPersistentCache open();

    /**
     * Performs some work against the cache.
     *
     * Acquires exclusive locks on the appropriate resources, so that the given action is the only action to execute across all processes (including this one).
     * Releases the locks and all resources at the end of the action.
     */
    <T> T useCache(String key, Supplier<? extends T> action);

    /**
     * Performs some work against the cache key.
     *
     * Acquires exclusive locks on the appropriate resources, so that the given action is the only action to execute across all processes (including this one).
     * Releases the locks and all resources at the end of the action.
     */
    void useCache(String key, Runnable action);

    /**
     * Closes this cache, blocking until all operations are complete.
     */
    @Override
    void close();
}
