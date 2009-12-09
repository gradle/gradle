/*
 * Copyright 2009 the original author or authors.
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

public interface CacheRepository {
    /**
     * Returns the cache with the given key shared by all builds.
     *
     * @param key the cache key. Uniquely identifies the cache
     * @param properties additional properties for the cache. The cache is treated as invalid if any of the properties
     * do not match the properties used to create the cache.
     * @return The cache.
     */
    PersistentCache getGlobalCache(String key, Map<String, ?> properties);

    /**
     * Returns the cache with the given key shared by all builds.
     *
     * @param key the cache key. Uniquely identifies the cache
     * @return The cache.
     */
    PersistentCache getGlobalCache(String key);

    /**
     * Returns the cache with the given key private to the current build.
     *
     * @param target The target domain object which the cache is for. This might be a task, project, or similar.
     * @param key the cache key. Uniquely identifies the cache
     * @param properties additional properties for the cache. The cache is treated as invalid if any of the properties
     * do not match the properties used to create the cache.
     * @return The cache.
     */
    PersistentCache getCacheFor(Object target, String key, Map<String, ?> properties);

    /**
     * Returns the cache with the given key private to the current build.
     *
     * @param target The target domain object which the cache is for. This might be a task, project, or similar.
     * @param key the cache key. Uniquely identifies the cache
     * @return The cache.
     */
    PersistentCache getCacheFor(Object target, String key);
}
