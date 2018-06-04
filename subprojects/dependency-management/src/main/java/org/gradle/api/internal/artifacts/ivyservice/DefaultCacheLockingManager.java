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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.gradle.api.Transformer;
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore;
import org.gradle.cache.CacheAccess;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CompositeCleanupAction;
import org.gradle.cache.internal.IndexedCacheBackedFileAccessJournal;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.internal.Factory;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;
import org.gradle.internal.resource.local.FileAccessJournal;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;

import static org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_EXTERNAL_CACHE_ENTRIES;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DefaultCacheLockingManager implements CacheLockingManager, Closeable {
    private final PersistentCache cache;
    private final FileAccessJournal fileAccessJournal;

    public DefaultCacheLockingManager(CacheRepository cacheRepository, ArtifactCacheMetadata cacheMetaData) {
        cache = cacheRepository
                .cache(cacheMetaData.getCacheDir())
                .withCrossVersionCache(CacheBuilder.LockTarget.CacheDirectory)
                .withDisplayName("artifact cache")
                .withLockOptions(mode(FileLockManager.LockMode.None)) // Don't need to lock anything until we use the caches
                .withCleanup(createCleanupAction(cacheMetaData))
                .open();
        fileAccessJournal = new IndexedCacheBackedFileAccessJournal(new IndexedCacheBackedFileAccessJournal.PersistentIndexedCacheFactory() {
            @Override
            public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
                return DefaultCacheLockingManager.this.createCache(cacheName, keySerializer, valueSerializer);
            }
        });
    }

    private CleanupAction createCleanupAction(ArtifactCacheMetadata cacheMetaData) {
        FileAccessJournal fileAccessJournal = getFileAccessJournalForCleanup();
        long maxAgeInDays = DEFAULT_MAX_AGE_IN_DAYS_FOR_EXTERNAL_CACHE_ENTRIES;
        return CompositeCleanupAction.builder()
                .add(cacheMetaData.getExternalResourcesStoreDirectory(),
                    new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(ExternalResourceFileStore.FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessJournal, maxAgeInDays))
                .add(cacheMetaData.getFileStoreDirectory(),
                    new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(ArtifactIdentifierFileStore.FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessJournal, maxAgeInDays))
                .build();
    }

    private FileAccessJournal getFileAccessJournalForCleanup() {
        final Supplier<FileAccessJournal> lockFreeFileAccessJournalSupplier = Suppliers.memoize(new Supplier<FileAccessJournal>() {
            @Override
            public FileAccessJournal get() {
                return new IndexedCacheBackedFileAccessJournal(new IndexedCacheBackedFileAccessJournal.PersistentIndexedCacheFactory() {
                    @Override
                    public <K, V> PersistentIndexedCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
                        return DefaultCacheLockingManager.this.createCache(cacheName, keySerializer, valueSerializer, CacheUsage.FILE_LOCK);
                    }
                });
            }
        });
        return new FileAccessJournal() {
            @Override
            public void setLastAccessTime(File file, long millis) {
                lockFreeFileAccessJournalSupplier.get().setLastAccessTime(file, millis);
            }

            @Override
            public long getLastAccessTime(File file) {
                return lockFreeFileAccessJournalSupplier.get().getLastAccessTime(file);
            }
        };
    }

    @Override
    public void close() {
        cache.close();
    }

    @Override
    public <T> T withFileLock(Factory<? extends T> action) {
        return cache.withFileLock(action);
    }

    @Override
    public void withFileLock(Runnable action) {
        cache.withFileLock(action);
    }

    @Override
    public <T> T useCache(Factory<? extends T> action) {
        return cache.useCache(action);
    }

    @Override
    public void useCache(Runnable action) {
        cache.useCache(action);
    }

    @Override
    public FileAccessJournal getFileAccessJournal() {
        return fileAccessJournal;
    }

    @Override
    public <K, V> CacheLockingPersistentCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        return createCache(cacheName, keySerializer, valueSerializer, CacheUsage.EXCLUSIVE);
    }

    private <K, V> CacheLockingPersistentCache<K, V> createCache(String cacheName, Serializer<K> keySerializer, Serializer<V> valueSerializer, CacheUsage usage) {
        String cacheFileInMetaDataStore = CacheLayout.META_DATA.getKey() + "/" + cacheName;
        final PersistentIndexedCache<K, V> persistentCache = cache.createCache(new PersistentIndexedCacheParameters<K, V>(cacheFileInMetaDataStore, keySerializer, valueSerializer));
        return new CacheLockingPersistentCache<K, V>(persistentCache, usage);
    }

    private class CacheLockingPersistentCache<K, V> implements PersistentIndexedCache<K, V> {
        private final PersistentIndexedCache<K, V> persistentCache;
        private final CacheUsage usage;

        CacheLockingPersistentCache(PersistentIndexedCache<K, V> persistentCache, CacheUsage usage) {
            this.persistentCache = persistentCache;
            this.usage = usage;
        }

        @Nullable
        @Override
        public V get(final K key) {
            return usage.execute(cache, new Factory<V>() {
                @Override
                public V create() {
                    return persistentCache.get(key);
                }
            });
        }

        @Override
        public V get(final K key, final Transformer<? extends V, ? super K> producer) {
            return usage.execute(cache, new Factory<V>() {
                @Override
                public V create() {
                    return persistentCache.get(key, producer);
                }
            });
        }

        @Override
        public void put(final K key, final V value) {
            usage.execute(cache, new Runnable() {
                @Override
                public void run() {
                    persistentCache.put(key, value);
                }
            });
        }

        @Override
        public void remove(final K key) {
            usage.execute(cache, new Runnable() {
                @Override
                public void run() {
                    persistentCache.remove(key);
                }
            });
        }
    }

    private enum CacheUsage {

        FILE_LOCK {
            @Override
            <T> T execute(CacheAccess cacheAccess, Factory<? extends T> action) {
                return cacheAccess.withFileLock(action);
            }

            @Override
            void execute(CacheAccess cacheAccess, Runnable action) {
                cacheAccess.withFileLock(action);
            }
        },

        EXCLUSIVE {
            @Override
            <T> T execute(CacheAccess cacheAccess, Factory<? extends T> action) {
                return cacheAccess.useCache(action);
            }

            @Override
            void execute(CacheAccess cacheAccess, Runnable action) {
                cacheAccess.useCache(action);
            }
        };

        abstract <T> T execute(CacheAccess cacheAccess, Factory<? extends T> action);

        abstract void execute(CacheAccess cacheAccess, Runnable action);

    }
}
