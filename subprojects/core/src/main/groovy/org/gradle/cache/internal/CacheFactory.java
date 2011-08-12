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
package org.gradle.cache.internal;

import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.cache.*;
import org.gradle.cache.internal.FileLockManager.LockMode;

import java.io.File;
import java.util.Map;

public interface CacheFactory {
    enum CrossVersionMode {
        VersionSpecific, CrossVersion
    }

    PersistentCache openStore(File storeDir, LockMode lockMode, CrossVersionMode crossVersionMode, Action<? super PersistentCache> initializer) throws CacheOpenException;

    PersistentCache open(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, CrossVersionMode crossVersionMode,
                         Action<? super PersistentCache> initializer) throws CacheOpenException;

    <E> PersistentStateCache<E> openStateCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, CrossVersionMode crossVersionMode,
                                               Serializer<E> serializer) throws CacheOpenException;

    <K, V> PersistentIndexedCache<K, V> openIndexedCache(File cacheDir, CacheUsage usage, Map<String, ?> properties, LockMode lockMode, CrossVersionMode crossVersionMode,
                                                         Serializer<V> serializer) throws CacheOpenException;
}




