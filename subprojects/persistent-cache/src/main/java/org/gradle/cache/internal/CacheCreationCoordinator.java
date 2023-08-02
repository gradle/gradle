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

package org.gradle.cache.internal;

import org.gradle.cache.HasCleanupAction;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;

import java.io.Closeable;

public interface CacheCreationCoordinator extends Closeable, HasCleanupAction {
    void open();

    /**
     * Closes the cache, blocking until all operations have completed.
     */
    @Override
    void close();

    <K, V> IndexedCache<K, V> newCache(IndexedCacheParameters<K, V> parameters);

    <K, V> boolean cacheExists(IndexedCacheParameters<K, V> parameters);

}
