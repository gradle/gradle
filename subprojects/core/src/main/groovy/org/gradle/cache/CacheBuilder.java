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

import java.util.Map;

public interface CacheBuilder<T> {
    enum VersionStrategy {
        /**
         * A separate cache instance for each Gradle version. This is the default.
         */
        CachePerVersion,
        /**
         * A single cache instance shared by all Gradle versions. It is the caller's responsibility to make sure that this is shared only with
         * those versions of Gradle that are compatible with the cache implementation and contents.
         */
        SharedCache,
        /**
         * A single cache instance, which is invalidated when the Gradle version changes.
         */
        SharedCacheInvalidateOnVersionChange,
    }

    /**
     * Specifies the additional key properties for the cache. The cache is treated as invalid if any of the properties do not match the properties used to create the cache. The default for this is an
     * empty map.
     *
     * @param properties additional properties for the cache.
     * @return this
     */
    CacheBuilder<T> withProperties(Map<String, ?> properties);

    /**
     * Specifies the target domain object.  This might be a task, project, or similar. The cache is scoped for the given target object. The default is to use a globally-scoped cache.
     *
     * @param target The target domain object which the cache is for.
     * @return this
     */
    CacheBuilder<T> forObject(Object target);

    /**
     * Specifies the versioning strategy for this cache. The default is {@link VersionStrategy#CachePerVersion}.
     *
     * @param strategy The strategy
     * @return this
     */
    CacheBuilder<T> withVersionStrategy(VersionStrategy strategy);

    /**
     * Specifies a cache validator for this cache. If {@link CacheValidator#isValid()} results in false, the Cache is considered as invalid.
     *
     * @param validator The validator
     * @return this
     */
    CacheBuilder<T> withValidator(CacheValidator validator);

    /**
     * Opens the cache.
     *
     * @return The cache.
     */
    T open() throws CacheOpenException;
}
