/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.cache.IndexedCache
import org.gradle.internal.serialize.Serializer
import org.gradle.testfixtures.internal.TestInMemoryIndexedCache

import java.util.function.Supplier

class ArtifactCacheLockingAccessCoordinatorStub implements ArtifactCacheLockingAccessCoordinator {
    private final Map<String, IndexedCache<?, ?>> caches = [:]

    IndexedCache<?, ?> getCache(String cacheName) {
        caches[cacheName]
    }

    @Override
    <K, V> IndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        def result = new TestInMemoryIndexedCache<>(valueSerializer)
        caches.put(cacheName, result)
        return result
    }

    @Override
    <T> T useCache(Supplier<? extends T> action) {
        action.get()
    }

    @Override
    void useCache(Runnable action) {
        action.run()
    }

    @Override
    <T> T withFileLock(Supplier<? extends T> action) {
        action.get()
    }

    @Override
    void withFileLock(Runnable action) {
        action.run()
    }
}
