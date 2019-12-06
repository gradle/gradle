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

package org.gradle.cache.internal;

import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.vfs.AdditiveCacheLocations;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * A {@link FileContentCacheFactory} that delegates to the global cache for files that are known to be immutable and shared between different builds.
 * All other requests are delegated to the build-local cache. Closing this factory will only close the local delegate, not the global one.
 */
public class SplitFileContentCacheFactory implements FileContentCacheFactory, Closeable {
    private final FileContentCacheFactory globalFactory;
    private final FileContentCacheFactory localFactory;
    private final AdditiveCacheLocations additiveCacheLocations;

    public SplitFileContentCacheFactory(FileContentCacheFactory globalFactory, FileContentCacheFactory localFactory, AdditiveCacheLocations additiveCacheLocations) {
        this.globalFactory = globalFactory;
        this.localFactory = localFactory;
        this.additiveCacheLocations = additiveCacheLocations;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(localFactory).stop();
    }

    @Override
    public <V> FileContentCache<V> newCache(String name, int normalizedCacheSize, Calculator<? extends V> calculator, Serializer<V> serializer) {
        FileContentCache<V> globalCache = globalFactory.newCache(name, normalizedCacheSize, calculator, serializer);
        FileContentCache<V> localCache = localFactory.newCache(name, normalizedCacheSize, calculator, serializer);
        return new SplitFileContentCache<>(globalCache, localCache, additiveCacheLocations);
    }

    private static final class SplitFileContentCache<V> implements FileContentCache<V> {
        private final FileContentCache<V> globalCache;
        private final FileContentCache<V> localCache;
        private final AdditiveCacheLocations additiveCacheLocations;

        private SplitFileContentCache(FileContentCache<V> globalCache, FileContentCache<V> localCache, AdditiveCacheLocations additiveCacheLocations) {
            this.globalCache = globalCache;
            this.localCache = localCache;
            this.additiveCacheLocations = additiveCacheLocations;
        }

        @Override
        public V get(File file) {
            if (additiveCacheLocations.isInsideAdditiveCache(file.getPath())) {
                return globalCache.get(file);
            } else {
                return localCache.get(file);
            }
        }
    }
}
