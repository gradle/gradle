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
import org.gradle.caching.local.internal.mvstore.ValueWithTimestamp.ValueWithTimestampType;
import org.gradle.internal.hash.HashCode;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;
import org.h2.mvstore.StreamStore;
import org.h2.mvstore.type.ByteArrayDataType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Optional;
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
    private final MVMap<ValueWithTimestamp, byte[]> lruBuildCacheKeyToBlobIds;
    private final MVMap<byte[], ValueWithTimestamp> buildCacheKeyToBlobLastUsed;
    private final StreamStore streamStore;
    private final MVStore mvStore;

    public MVStoreLruStreamMap(MVStore mvStore, int maxConcurrency) {
        this.mvStore = mvStore;
        this.lruBuildCacheKeyToBlobIds = mvStore.openMap("lruBuildCacheKeyToBlobIds", new MVMap.Builder<ValueWithTimestamp, byte[]>()
            .keyType(ValueWithTimestampType.INSTANCE)
            .valueType(ByteArrayDataType.INSTANCE)
        );
        this.buildCacheKeyToBlobLastUsed = mvStore.openMap("buildCacheKeyToBlobLastUsed", new MVMap.Builder<byte[], ValueWithTimestamp>()
            .keyType(ComparableByteArrayType.INSTANCE)
            .valueType(ValueWithTimestampType.INSTANCE)
        );
        streamStore = getStreamStore(mvStore);
        int maxConcurrencyTimesTwo = (Integer.MAX_VALUE / 2) <= maxConcurrency ? Integer.MAX_VALUE : (maxConcurrency * 2);
        this.locks = Striped.lazyWeakLock(Math.max(maxConcurrencyTimesTwo, MINIMUM_LOCKS));
    }

    private static StreamStore getStreamStore(MVStore mvStore) {
        MVMap<Long, byte[]> map = mvStore.openMap("data");
        StreamStore streamStore = new StreamStore(map);
        if (map.lastKey() != null) {
            // Don't use already used keys
            streamStore.setNextKey(map.lastKey() + 1);
        }
        return streamStore;
    }

    public void putIfAbsent(BuildCacheKey key, Supplier<InputStream> inputStreamSupplier) {
        byte[] keyAsBytes = key.toByteArray();
        if (buildCacheKeyToBlobLastUsed.containsKey(keyAsBytes)) {
            return;
        }
        lockAndRun(key, () -> buildCacheKeyToBlobLastUsed.computeIfAbsent(keyAsBytes, k -> {

            Optional<byte[]> blobId = Optional.empty();
            MVStore.TxCounter txCounter = mvStore.registerVersionUsage();
            try (InputStream input = inputStreamSupplier.get()) {
                long timestamp = System.currentTimeMillis();
                blobId = Optional.of(streamStore.put(input));
                lruBuildCacheKeyToBlobIds.put(new ValueWithTimestamp(timestamp, keyAsBytes), blobId.get());
                return new ValueWithTimestamp(timestamp, blobId.get());
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
        return buildCacheKeyToBlobLastUsed.containsKey(key.toByteArray());
    }

    @Nullable
    public InputStream get(BuildCacheKey key) {
        byte[] keyAsBytes = key.toByteArray();
        ValueWithTimestamp dataReference = buildCacheKeyToBlobLastUsed.get(keyAsBytes);
        if (dataReference == null) {
            return null;
        }
        InputStream inputStream = streamStore.get(dataReference.get());
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
            ValueWithTimestamp blobLastUsedData = buildCacheKeyToBlobLastUsed.get(keyAsBytes);
            if (blobLastUsedData == null) {
                // If deletion happened just before lock was acquired
                return;
            }
            long currentTimestamp = System.currentTimeMillis();
            lruBuildCacheKeyToBlobIds.put(new ValueWithTimestamp(currentTimestamp, keyAsBytes), blobLastUsedData.get());
            buildCacheKeyToBlobLastUsed.put(keyAsBytes, new ValueWithTimestamp(currentTimestamp, blobLastUsedData.get()));
            // Remove previous LRU entry last, so data can be cleaned up even if something explodes before
            lruBuildCacheKeyToBlobIds.remove(new ValueWithTimestamp(blobLastUsedData.getTimestamp(), keyAsBytes));
        });
    }

    public void delete(BuildCacheKey key) {
        byte[] keyAsBytes = key.toByteArray();
        if (!buildCacheKeyToBlobLastUsed.containsKey(keyAsBytes)) {
            return;
        }
        lockAndRun(key, () -> {
            ValueWithTimestamp blobLastUsedData = buildCacheKeyToBlobLastUsed.get(keyAsBytes);
            if (blobLastUsedData == null) {
                // If deletion happened just before lock was acquired
                return;
            }
            // Remove key to blob metadata first, so that contains and get works always properly, even if delete operations after fail
            buildCacheKeyToBlobLastUsed.remove(keyAsBytes);
            streamStore.remove(blobLastUsedData.get());
            // Remove LRU entry last, so data can be cleaned up even if something explodes before
            lruBuildCacheKeyToBlobIds.remove(new ValueWithTimestamp(blobLastUsedData.getTimestamp(), keyAsBytes));
        });
    }

    /**
     * Cleanup entries in LRU order
     */
    public void cleanup(long removeUnusedEntriesOlderThan) {
        MVStore.TxCounter txCounter = mvStore.registerVersionUsage();
        try {
            cleanup(removeUnusedEntriesOlderThan, lruBuildCacheKeyToBlobIds.keyIterator(null));
        } finally {
            mvStore.deregisterVersionUsage(txCounter);
        }
    }

    private void cleanup(long removeUnusedEntriesOlderThan, Iterator<ValueWithTimestamp> iterator) {
        while (iterator.hasNext()) {
            ValueWithTimestamp buildCacheKeyWithTimestamp = iterator.next();
            if (buildCacheKeyWithTimestamp.getTimestamp() > removeUnusedEntriesOlderThan) {
                break;
            }
            lockAndRun(new DefaultBuildCacheKey(HashCode.fromBytes(buildCacheKeyWithTimestamp.get())), () -> {
                ValueWithTimestamp blobLastUsedData = buildCacheKeyToBlobLastUsed.get(buildCacheKeyWithTimestamp.get());
                byte[] blobId = lruBuildCacheKeyToBlobIds.get(buildCacheKeyWithTimestamp);
                if (blobLastUsedData != null && buildCacheKeyWithTimestamp.getTimestamp() == blobLastUsedData.getTimestamp()) {
                    // Remove key to blob metadata first, so that contains and get works always properly, even if delete operations after fail.
                    // We do that only if blob last used timestamp matches lru key timestamp.
                    this.buildCacheKeyToBlobLastUsed.remove(buildCacheKeyWithTimestamp.get());
                }
                streamStore.remove(blobId);
                // Remove LRU entry last, so data can be cleaned up even if something explodes before
                lruBuildCacheKeyToBlobIds.remove(buildCacheKeyWithTimestamp);
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
