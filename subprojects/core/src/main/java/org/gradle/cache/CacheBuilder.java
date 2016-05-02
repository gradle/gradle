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
import org.gradle.cache.internal.filelock.LockOptions;

import java.util.Map;

public interface CacheBuilder {
    enum VersionStrategy {
        /**
         * A separate cache instance for each Gradle version. This is the default.
         */
        CachePerVersion,
        /**
         * A single cache instance shared by all Gradle versions. It is the caller's responsibility to make sure that this is shared only with
         * those versions of Gradle that are compatible with the cache implementation and contents.
         */
        SharedCache
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
     * @return this
     */
    CacheBuilder withCrossVersionCache();

    /**
     * Specifies a cache validator for this cache. If {@link CacheValidator#isValid()} results in false, the Cache is considered as invalid.
     *
     * @param validator The validator
     * @return this
     */
    CacheBuilder withValidator(CacheValidator validator);

    /**
     * Specifies the display name for this cache. This display name is used in logging and error messages.
     */
    CacheBuilder withDisplayName(String displayName);

    /**
     * Specifies the <em>initial</em> lock options to use. See {@link PersistentCache} for details.
     *
     * <p>Note that not every combination of cache type and lock options is supported.
     */
    CacheBuilder withLockOptions(LockOptions lockOptions);

    /**
     * Specifies an action to execute to initialize the cache contents, if the cache does not exist or is invalid. An exclusive lock is held while the initializer is executing, to prevent
     * cross-process access.
     */
    CacheBuilder withInitializer(Action<? super PersistentCache> initializer);

    /**
     * Opens the cache. It is the caller's responsibility to close the cache when finished with it.
     *
     * @return The cache.
     */
    PersistentCache open() throws CacheOpenException;
}
