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

package org.gradle.api.internal.changedetection.state;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.cache.AsyncCacheAccess;
import org.gradle.cache.CacheDecorator;
import org.gradle.cache.CrossProcessCacheAccess;
import org.gradle.cache.MultiProcessSafePersistentIndexedCache;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.internal.resource.local.FileAccessTimeJournal;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER;
import static org.gradle.internal.serialize.BaseSerializerFactory.LONG_SERIALIZER;

public class IndexedCacheBackedFileAccessTimeJournal implements FileAccessTimeJournal {

    public static final String CACHE_NAME = "file-access-journal";

    public static IndexedCacheBackedFileAccessTimeJournal create(String namePrefix, PersistentCache persistentCache, InMemoryCacheDecoratorFactory cacheDecoratorFactory) {
        final CacheDecorator decorator = cacheDecoratorFactory.decorator(1000, false);
        final AtomicReference<PersistentIndexedCache<File, Long>> syncCache = new AtomicReference<PersistentIndexedCache<File, Long>>();
        PersistentIndexedCache<File, Long> asyncCache = persistentCache.createCache(baseCacheParameters(namePrefix)
            .cacheDecorator(new CacheDecorator() {
                @Override
                public <K, V> MultiProcessSafePersistentIndexedCache<K, V> decorate(String cacheId, String cacheName, MultiProcessSafePersistentIndexedCache<K, V> persistentCache, CrossProcessCacheAccess crossProcessCacheAccess, AsyncCacheAccess asyncCacheAccess) {
                    PersistentIndexedCache<File, Long> decoratedCache = uncheckedCast(persistentCache);
                    syncCache.set(decoratedCache);
                    return decorator.decorate(cacheId, cacheName, persistentCache, crossProcessCacheAccess, asyncCacheAccess);
                }
            }));
        syncCache.compareAndSet(null, asyncCache);
        return new IndexedCacheBackedFileAccessTimeJournal(asyncCache, syncCache.get());
    }

    @VisibleForTesting
    static PersistentIndexedCacheParameters<File, Long> baseCacheParameters(String namePrefix) {
        return PersistentIndexedCacheParameters.of(namePrefix + CACHE_NAME, FILE_SERIALIZER, LONG_SERIALIZER);
    }

    private final PersistentIndexedCache<File, Long> asyncCacheForWritingWhileCacheIsBeingAccessed;
    private final PersistentIndexedCache<File, Long> syncCacheForReadingDuringCleanup;

    @VisibleForTesting
    IndexedCacheBackedFileAccessTimeJournal(PersistentIndexedCache<File, Long> asyncCacheForWritingWhileCacheIsBeingAccessed, PersistentIndexedCache<File, Long> syncCacheForReadingDuringCleanup) {
        this.asyncCacheForWritingWhileCacheIsBeingAccessed = asyncCacheForWritingWhileCacheIsBeingAccessed;
        this.syncCacheForReadingDuringCleanup = syncCacheForReadingDuringCleanup;
    }

    @Override
    public void setLastAccessTime(File file, long millis) {
        asyncCacheForWritingWhileCacheIsBeingAccessed.put(file, millis);
    }

    @Override
    public long getLastAccessTime(File file) {
        Long value = syncCacheForReadingDuringCleanup.get(file);
        return value != null ? value : file.lastModified();
    }
}
