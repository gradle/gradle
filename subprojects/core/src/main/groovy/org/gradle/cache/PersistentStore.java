/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.messaging.serialize.Serializer;

/**
 * Represents some persistent store.
 *
 * <p>Use can use</p>
 *
 * <p>You can use {@link #useCache(String, org.gradle.internal.Factory)} to perform some action on the store while holding an exclusive
 * lock on the store.</p>
 */
public interface PersistentStore extends CacheAccess {
    /**
     * Creates an indexed cache implementation that is contained within this store. This method may be used at any time.
     *
     * <p>The returned cache may only be used by an action being run from {@link #useCache(String, org.gradle.internal.Factory)}.
     * In this instance, an exclusive lock will be held on the cache.
     *
     * <p>The returned cache may not be used by an action being run from {@link #longRunningOperation(String, org.gradle.internal.Factory)}.
     */
    <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer);
}
