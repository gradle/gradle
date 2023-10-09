/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action;

import java.util.Map;

public interface CacheBuilder {
    enum LockTarget {
        /**
         * Use the cache properties file as the lock target, for backwards compatibility with old Gradle versions.
         */
        CachePropertiesFile,
        /**
         * Use the cache directory as the lock target, for backwards compatibility with old Gradle versions.
         */
        CacheDirectory,
        /**
         * Use the default target.
         */
        DefaultTarget,
    }

    /**
     * Specifies the additional key properties for the cache. The cache is treated as invalid if any of the properties do not match the properties used to create the cache. The default for this is an
     * empty map.
     *
     * @param properties additional properties for the cache.
     * @return this
     */
    CacheBuilder withProperties(Map<String, ?> properties);

    /**
     * Specifies that the cache should be shared by all versions of Gradle. The default is to use a Gradle version specific cache.
     * @param lockTarget The lock target, used for backwards compatibility with older Gradle versions. Use {@link LockTarget#DefaultTarget} when no preference.
     * @return this
     */
    CacheBuilder withCrossVersionCache(LockTarget lockTarget);

    /**
     * Specifies the display name for this cache. This display name is used in logging and error messages.
     */
    CacheBuilder withDisplayName(String displayName);

    /**
     * Specifies the <em>initial</em> lock options to use. See {@link PersistentCache} for details.
     *
     * Note that not every combination of cache type and lock options is supported.
     */
    CacheBuilder withLockOptions(LockOptions lockOptions);

    /**
     * Specifies an action to execute to initialize the cache contents, if the cache does not exist or is invalid. An exclusive lock is held while the initializer is executing, to prevent
     * cross-process access.
     */
    CacheBuilder withInitializer(Action<? super PersistentCache> initializer);

    /**
     * Specifies an action to execute when the cache needs to be cleaned up. An exclusive lock is held while the cleanup is executing, to prevent cross-process access.
     *
     * A clean-up action is run when a cache is closed, but only after the interval specified by the provided {@link CacheCleanupStrategy}.
     */
    CacheBuilder withCleanupStrategy(CacheCleanupStrategy cleanup);

    /**
     * Opens the cache. It is the caller's responsibility to close the cache when finished with it.
     *
     * <p>
     *     NOTE: The <em>initial</em> lock option is {@link org.gradle.cache.FileLockManager.LockMode#Shared}.
     * </p>
     * <ul>
     *     <li>Using {@link org.gradle.cache.FileLockManager.LockMode#Exclusive} will lock the cache on open() in the exclusive mode and keep it locked until {@link PersistentCache#close()} is called.</li>
     *     <li>Using {@link org.gradle.cache.FileLockManager.LockMode#Shared} will lock the cache on open() in the shared mode and keep it locked until {@link PersistentCache#close()} is called.</li>
     *     <li>Using {@link org.gradle.cache.FileLockManager.LockMode#OnDemandExclusive} or {@link org.gradle.cache.FileLockManager.LockMode#OnDemandShared} will <em>not</em> lock the cache on open().</li>
     * </ul>
     *
     * @return The cache.
     */
    PersistentCache open() throws CacheOpenException;
}
