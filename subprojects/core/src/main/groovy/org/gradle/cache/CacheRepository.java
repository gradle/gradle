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

/**
 * A repository of persistent caches and stores. A <em>store</em> is a store for persistent data. A <em>cache</em> is a store for persistent
 * cache data. The only real difference between the two is that a store cannot be invalidated, whereas a cache can be invalidated when things
 * change. For example, running with {@code --cache rebuild} will invalidate the contents of all caches, but not the contents of any stores.
 */
public interface CacheRepository {
    /**
     * Returns a builder for the store with the given key. Default is a Gradle version-specific store shared by all builds, though this can be
     * changed using the given builder.
     *
     * <p>A store is always opened with a shared lock, so that it can be accessed by multiple processes. It is the caller's responsibility to
     * coordinate access to the cache.</p>
     *
     * @param key The cache key.
     * @return The builder.
     */
    CacheBuilder store(String key);

    /**
     * Returns a builder for the cache with the given key. Default is a Gradle version-specific cache shared by all builds, though this can be
     * changed using the given builder.
     *
     * <p>A state cache is always opened with a shared lock, so that it can be accessed by multiple processes. It is the caller's responsibility
     * to coordinate access to the cache.</p>
     *
     * @param key The cache key.
     * @return The builder.
     */
    CacheBuilder cache(String key);
}
