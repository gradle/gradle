/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.cache.ExclusiveCacheAccessCoordinator;
import org.gradle.cache.IndexedCache;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Provides synchronized access to the artifact cache.
 */
@ThreadSafe
public interface ArtifactCacheLockingAccessCoordinator extends ExclusiveCacheAccessCoordinator {
    /**
     * Creates a cache implementation that is managed by this locking manager. This method may be used at any time.
     *
     * <p>The returned cache may only be used by an action being run from {@link #useCache(org.gradle.internal.Factory)}.
     * In this instance, an exclusive lock will be held on the cache.
     *
     */
    <K, V> IndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer);
}
