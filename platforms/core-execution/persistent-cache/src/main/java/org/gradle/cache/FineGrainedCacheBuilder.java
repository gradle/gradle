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

public interface FineGrainedCacheBuilder {

    /**
     * Specifies the display name for this cache. This display name is used in logging and error messages.
     */
    FineGrainedCacheBuilder withDisplayName(String displayName);

    /**
     * Specifies a least recently used cleanup. An exclusive lock is held while the cleanup is executing, to prevent cross-process access.
     *
     * A {@link FineGrainedLeastRecentlyUsedCacheCleanup} action does a soft cleanup first by marking entries as "stale". And in the next cleanup run it will remove those stale entries if weren't accessed since the last cleanup.
     *
     * TODO: This is a temporary API, and should be replaced by a more general cleanup API in the future.
     */
    FineGrainedCacheBuilder withLeastRecentCleanup(FineGrainedCacheCleanupStrategy cacheCleanupStrategy);

    /**
     * Number of locks to use for the cache. The default is 64, maximum allowed is currently 512.
     * We generally don't expect that many locks to be needed, since number of process is usually low,
     * but this allows for a larger number of locks to be used if needed.
     *
     * The actual number of locks will be the next power of two greater than or equal to this number.
     */
    FineGrainedCacheBuilder withNumberOfLocks(int numberOfLocks);

    /**
     * Opens the cache. It is the caller's responsibility to close the cache when finished with it.
     *
     * The only lock option is {@link org.gradle.cache.FileLockManager.LockMode#OnDemandEagerRelease}
     */
    FineGrainedPersistentCache open() throws CacheOpenException;

}
