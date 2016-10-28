/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.cache.internal;

/**
 * Participates in a unit of work that accesses the cache. Implementations do not need to be thread-safe and are accessed by a single thread at a time.
 */
public interface UnitOfWorkParticipant {
    /**
     * Called just after the cache is locked. Called before any work is performed by other threads. This method may access the cache files.
     *
     * @param currentCacheState the current cache state.
     */
    void afterLockAcquire(FileLock.State currentCacheState);

    /**
     * Called when the cache is due to be unlocked. Call after other threads have completed work. This method may access the cache files.
     */
    void finishWork();

    /**
     * Called just before the cache is to be unlocked. Called after all work has been completed, and after {@link #finishWork()} has completed.
     * This method should not modify the cache files.
     */
    void beforeLockRelease(FileLock.State currentCacheState);
}
