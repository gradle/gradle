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
 * A repository of persistent caches. There are 3 types of caches:
 *
 * <ul>
 *
 * <li>A directory backed cache, represented by {@link PersistentCache}. The caller is responsible for managing the contents of this directory.</li>
 *
 * <li>An indexed cache, essentially a persistent Map, represented by {@link PersistentIndexedCache}.</li>
 *
 * <li>A state cache, a single persistent value, represented by {@link PersistentStateCache}.</li>
 *
 * </ul>
 */
public interface CacheRepository {
    /**
     * Returns a builder for the cache with the given key. Default is a Gradle version-specific cache shared by all
     * builds, though this can be changed using the given builder.
     *
     * @param key The cache key.
     * @return The builder.
     */
    CacheBuilder<PersistentCache> cache(String key);

    /**
     * Returns a builder for the state cache with the given key. Default is a Gradle version-specific cache shared by all
     * builds, though this can be changed using the given builder.
     *
     * @param key The cache key.
     * @return The builder.
     */
    <E> ObjectCacheBuilder<E, PersistentStateCache<E>> stateCache(Class<E> elementType, String key);

    /**
     * Returns a builder for the indexed cache with the given key. Default is a Gradle version-specific cache shared by all
     * builds, though this can be changed using the given builder.
     *
     * @param key The cache key.
     * @return The builder.
     */
    <K, V> ObjectCacheBuilder<V, PersistentIndexedCache<K, V>> indexedCache(Class<K> keyType, Class<V> elementType, String key);
}
