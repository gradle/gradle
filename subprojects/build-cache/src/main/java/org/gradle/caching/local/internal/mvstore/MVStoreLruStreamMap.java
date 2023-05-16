/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.local.internal.mvstore;

import com.google.common.util.concurrent.Striped;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.DefaultBuildCacheKey;
import org.gradle.caching.local.internal.mvstore.BlobId.BlobIdType;
import org.gradle.caching.local.internal.mvstore.LruEntry.LruEntryType;
import org.gradle.internal.hash.HashCode;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;
import org.h2.mvstore.StreamStore;
import org.h2.mvstore.type.LongDataType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * A MVStore Stream map that supports LRU eviction.
 *
 * It avoids using MVStore transactions but uses a locks per key for some performance gains, since we anyway use embedded H2.
 * Should not be used with H2 in server mode, we should use transactions there instead.
 *
 * Currently, it only supports BuildCacheKey as a key, but could be extended to support other cases.
 */
public class MVStoreLruStreamMap {

    private static final int MINIMUM_LOCKS = 512;
    private final Striped<Lock> locks;
    private final AtomicLong lruCounter;
    private final MVMap<Long, LruEntry> lruEntries;
    private final MVMap<byte[], BlobId> blobIds;
    private final StreamStore streamStore;
    private final MVStore mvStore;

    public MVStoreLruStreamMap(MVStore mvStore, int maxConcurrency) {
        this.mvStore = mvStore;
        this.lruEntries = mvStore.openMap("lruEntries", new MVMap.Builder<Long, LruEntry>()
            .keyType(LongDataType.INSTANCE)
            .valueType(LruEntryType.INSTANCE)
        );
        // Don't use already used keys
        lruCounter = lruEntries.lastKey() == null ? new AtomicLong() : new AtomicLong(lruEntries.lastKey() + 1);
        this.blobIds = mvStore.openMap("blobIds", new MVMap.Builder<byte[], BlobId>()
            .keyType(ComparableByteArrayType.INSTANCE)
            .valueType(BlobIdType.INSTANCE)
        );
        streamStore = getStreamStore(mvStore);
        int maxConcurrencyTimesTwo = (Integer.MAX_VALUE / 2) <= maxConcurrency ? Integer.MAX_VALUE : (maxConcurrency * 2);
        this.locks = Striped.lazyWeakLock(Math.max(maxConcurrencyTimesTwo, MINIMUM_LOCKS));
    }

    private static StreamStore getStreamStore(MVStore mvStore) {
        MVMap<Long, byte[]> map = mvStore.openMap("data");
        StreamStore streamStore = new StreamStore(map);
        // Don't use already used keys
        streamStore.setNextKey(map.lastKey() == null ? 0 : map.lastKey() + 1);
        return streamStore;
    }

    public void putIfAbsent(BuildCacheKey key, Supplier<InputStream> inputStreamSupplier) {
        byte[] keyAsBytes = key.toByteArray();
        if (blobIds.containsKey(keyAsBytes)) {
            return;
        }
        lockAndRun(key, () -> blobIds.computeIfAbsent(keyAsBytes, k -> {
            Optional<byte[]> blobId = Optional.empty();
            MVStore.TxCounter txCounter = mvStore.registerVersionUsage();

            try (InputStream input = inputStreamSupplier.get()) {
                long timestamp = System.currentTimeMillis();
                blobId = Optional.of(streamStore.put(input));
                long lruKey = lruCounter.getAndIncrement();
                lruEntries.put(lruKey, new LruEntry(keyAsBytes, blobId.get(), timestamp));
                return new BlobId(lruKey, blobId.get(), timestamp);
            } catch (IOException | MVStoreException e) {
                // If lruKeyToIndex.put throws exception, remove data entry
                blobId.ifPresent(streamStore::remove);
                throw new BuildCacheException("storing " + key, e);
            } finally {
                mvStore.deregisterVersionUsage(txCounter);
            }
        }));
    }

    public boolean containsKey(BuildCacheKey key) {
        return blobIds.containsKey(key.toByteArray());
    }

    @Nullable
    public InputStream get(BuildCacheKey key) {
        byte[] keyAsBytes = key.toByteArray();
        BlobId blobId = blobIds.get(keyAsBytes);
        if (blobId == null) {
            return null;
        }
        InputStream inputStream = streamStore.get(blobId.getBlobId());
        if (inputStream == null) {
            return null;
        }
        // TODO update timestamp only if some time has passed, e.g. 1 day?
        tryUpdateTimestamp(key, keyAsBytes);
        return inputStream;
    }

    /**
     * Tries to update usage timestamp for a cache key.
     * If some other thread is accessing the same key then operation is skipped.
     *
     * That is ok, since if delete is in progress, then we don't need to update timestamp.
     * And if another get is in progress, timestamp will be updated anyway.
     */
    private void tryUpdateTimestamp(BuildCacheKey key, byte[] keyAsBytes) {
        tryLockAndRun(key, () -> {
            BlobId blobId = blobIds.get(keyAsBytes);
            if (blobId == null) {
                // If deletion happened just before lock was acquired
                return;
            }
            long currentTimestamp = System.currentTimeMillis();
            long lruKey = lruCounter.getAndIncrement();
            lruEntries.put(lruKey, new LruEntry(keyAsBytes, blobId.getBlobId(), currentTimestamp));
            blobIds.put(keyAsBytes, new BlobId(lruKey, blobId.getBlobId(), currentTimestamp));
            // Remove previous LRU entry last, so data can be cleaned up even if something explodes before
            lruEntries.remove(blobId.getLruKey());
        });
    }

    public void delete(BuildCacheKey key) {
        byte[] keyAsBytes = key.toByteArray();
        if (!blobIds.containsKey(keyAsBytes)) {
            return;
        }
        lockAndRun(key, () -> {
            BlobId blobId = blobIds.get(keyAsBytes);
            if (blobId == null) {
                // If deletion happened just before lock was acquired
                return;
            }
            // Remove key to blob metadata first, so that contains and get works always properly, even if delete operations after fail
            blobIds.remove(keyAsBytes);
            streamStore.remove(blobId.getBlobId());
            // Remove LRU entry last, so data can be cleaned up even if something explodes before
            lruEntries.remove(blobId.getLruKey());
        });
    }

    /**
     * Cleanup entries in LRU order
     */
    public void cleanup(long removeUnusedEntriesOlderThan) {
        MVStore.TxCounter txCounter = mvStore.registerVersionUsage();
        try {
            cleanup(removeUnusedEntriesOlderThan, lruEntries.keyIterator(null));
        } finally {
            mvStore.deregisterVersionUsage(txCounter);
        }
    }

    private void cleanup(long removeUnusedEntriesOlderThan, Iterator<Long> iterator) {
        while (iterator.hasNext()) {
            long lruKey = iterator.next();
            LruEntry lruEntry = lruEntries.get(lruKey);
            if (lruEntry.getLastAccessTime() > removeUnusedEntriesOlderThan) {
                break;
            }
            lockAndRun(new DefaultBuildCacheKey(HashCode.fromBytes(lruEntry.getBuildCacheKey())), () -> {
                BlobId blobId = blobIds.get(lruEntry.getBuildCacheKey());
                if (blobId != null && blobId.getLruKey() == lruKey) {
                    // Remove blob id only if it matches lru key, otherwise it means we are reading old lru entry
                    // Remove key to blob metadata first, so that contains and get works always properly, even if delete operations after fail.
                    blobIds.remove(lruEntry.getBuildCacheKey());
                    streamStore.remove(lruEntry.getBlobId());
                } else if (blobId == null || blobId.getLruKey() != lruKey && !Arrays.equals(blobId.getBlobId(), lruEntry.getBlobId())) {
                    // If we don't have any reference to a blob referenced by a lru key in the blobIds map
                    streamStore.remove(lruEntry.getBlobId());
                }
                // Remove LRU entry last, so data can be cleaned up even if something explodes before
                lruEntries.remove(lruKey);
            });
        }
    }

    private void tryLockAndRun(BuildCacheKey key, Runnable runnable) {
        Lock lock = locks.get(key);
        if (lock.tryLock()) {
            try {
                runnable.run();
            } finally {
                lock.unlock();
            }
        }
    }

    private void lockAndRun(BuildCacheKey key, Runnable runnable) {
        Lock lock = locks.get(key);
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }
}
