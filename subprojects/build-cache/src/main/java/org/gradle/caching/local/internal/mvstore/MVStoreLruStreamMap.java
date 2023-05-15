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
import org.gradle.internal.hash.HashCode;
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;
import org.h2.mvstore.StreamStore;
import org.h2.mvstore.WriteBuffer;
import org.h2.mvstore.type.BasicDataType;
import org.h2.mvstore.type.ByteArrayDataType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
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
    private final MVMap<ValueWithTimestamp, byte[]> lruBuildCacheKeyToDataReference;
    private final MVMap<byte[], ValueWithTimestamp> buildCacheKeyToDataReference;
    private final MVMap<byte[], byte[]> dataIndexToBuildCacheKey;
    private final StreamStore data;

    public MVStoreLruStreamMap(MVStore mvStore, int maxConcurrency) {
        this.lruBuildCacheKeyToDataReference = mvStore.openMap("lruBuildCacheKeyToDataReference", new MVMap.Builder<ValueWithTimestamp, byte[]>()
            .keyType(ValueWithTimestampType.INSTANCE)
            .valueType(ByteArrayDataType.INSTANCE)
        );
        this.buildCacheKeyToDataReference = mvStore.openMap("buildCacheKeyToDataReference", new MVMap.Builder<byte[], ValueWithTimestamp>()
            .keyType(ComparableByteArray.INSTANCE)
            .valueType(ValueWithTimestampType.INSTANCE)
        );
        this.data = new StreamStore(mvStore.openMap("data"));
        this.dataIndexToBuildCacheKey = mvStore.openMap("dataIndexToBuildCacheKey");
        int maxConcurrencyTimesTwo = (Integer.MAX_VALUE / 2) <= maxConcurrency ? Integer.MAX_VALUE : (maxConcurrency * 2);
        this.locks = Striped.lazyWeakLock(Math.max(maxConcurrencyTimesTwo, MINIMUM_LOCKS));
    }

    public void putIfAbsent(BuildCacheKey key, Supplier<InputStream> inputStreamSupplier) {
        byte[] keyAsBytes = key.toByteArray();
        if (buildCacheKeyToDataReference.containsKey(keyAsBytes)) {
            return;
        }
        lockAndRun(key, () -> buildCacheKeyToDataReference.computeIfAbsent(keyAsBytes, k -> {
            byte[] id = null;
            try (InputStream input = inputStreamSupplier.get()) {
                id = data.put(input);
                dataIndexToBuildCacheKey.put(id, keyAsBytes);
                long timestamp = System.currentTimeMillis();
                lruBuildCacheKeyToDataReference.put(new ValueWithTimestamp(timestamp, keyAsBytes), id);
                return new ValueWithTimestamp(timestamp, id);
            } catch (IOException | MVStoreException e) {
                if (id != null) {
                    // If lruKeyToIndex.put throws exception, remove data entry
                    data.remove(id);
                    dataIndexToBuildCacheKey.remove(id);
                }
                throw new BuildCacheException("storing " + key, e);
            }
        }));
    }

    public boolean containsKey(BuildCacheKey key) {
        return buildCacheKeyToDataReference.containsKey(key.toByteArray());
    }

    @Nullable
    public InputStream get(BuildCacheKey key) {
        byte[] keyAsBytes = key.toByteArray();
        ValueWithTimestamp dataReference = buildCacheKeyToDataReference.get(keyAsBytes);
        if (dataReference == null) {
            return null;
        }
        InputStream inputStream = data.get(dataReference.get());
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
            ValueWithTimestamp dataReference = buildCacheKeyToDataReference.get(keyAsBytes);
            if (dataReference == null) {
                // If deletion happened just before lock was acquired
                return;
            }
            long currentTimestamp = System.currentTimeMillis();
            lruBuildCacheKeyToDataReference.put(new ValueWithTimestamp(currentTimestamp, keyAsBytes), dataReference.get());
            buildCacheKeyToDataReference.put(keyAsBytes, new ValueWithTimestamp(currentTimestamp, dataReference.get()));
            // Since we do a cleanup by LRU order, delete previous LRU map entry last
            lruBuildCacheKeyToDataReference.remove(new ValueWithTimestamp(dataReference.getTimestamp(), keyAsBytes));
        });
    }

    public void delete(BuildCacheKey key) {
        byte[] keyAsBytes = key.toByteArray();
        if (!buildCacheKeyToDataReference.containsKey(keyAsBytes)) {
            return;
        }
        lockAndRun(key, () -> {
            ValueWithTimestamp dataReference = buildCacheKeyToDataReference.get(keyAsBytes);
            if (dataReference == null) {
                // If deletion happened just before lock was acquired
                return;
            }
            // Remove key to data index first, so that contains works always properly, even if delete operations after fails
            buildCacheKeyToDataReference.remove(keyAsBytes);
            data.remove(dataReference.get());
            dataIndexToBuildCacheKey.remove(dataReference.get());
            // Remove LRU entry last, so data can be cleaned up even if keyToDataIndex doesn't exist anymore
            lruBuildCacheKeyToDataReference.remove(new ValueWithTimestamp(dataReference.getTimestamp(), keyAsBytes));
        });
    }

    public void cleanup(long removeUnusedEntriesOlderThan) {
        Iterator<ValueWithTimestamp> iterator = lruBuildCacheKeyToDataReference.keyIterator(null);
        while (iterator.hasNext()) {
            ValueWithTimestamp buildCacheKey = iterator.next();
            if (buildCacheKey.getTimestamp() > removeUnusedEntriesOlderThan) {
                break;
            }
            lockAndRun(new DefaultBuildCacheKey(HashCode.fromBytes(buildCacheKey.get())), () -> {
                ValueWithTimestamp dataReference = buildCacheKeyToDataReference.get(buildCacheKey.get());
                byte[] dataIndex = lruBuildCacheKeyToDataReference.get(buildCacheKey);
                if (dataReference != null && buildCacheKey.getTimestamp() == dataReference.getTimestamp()) {
                    buildCacheKeyToDataReference.remove(buildCacheKey.get());
                    data.remove(dataReference.get());
                    dataIndexToBuildCacheKey.remove(dataReference.get());
                    lruBuildCacheKeyToDataReference.remove(buildCacheKey);
                } else if (dataIndex != null) {
                    // Handle corner cases that can happen if some delete or put was interrupted for some reason
                    byte[] dataBuildCacheKey = dataIndexToBuildCacheKey.get(dataIndex);
                    if (dataReference == null) {
                        // Case when buildCacheKeyToDataReference was cleaned, but data wasn't
                        if (dataBuildCacheKey != null && HashCode.compareBytes(buildCacheKey.get(), dataBuildCacheKey) == 0) {
                            data.remove(buildCacheKey.get());
                            dataIndexToBuildCacheKey.remove(dataIndex);
                            lruBuildCacheKeyToDataReference.remove(buildCacheKey);
                        }
                    } else if (buildCacheKey.getTimestamp() < dataReference.getTimestamp()) {
                        if (HashCode.compareBytes(dataIndex, dataReference.get()) != 0) {
                            data.remove(buildCacheKey.get());
                            dataIndexToBuildCacheKey.remove(dataIndex);
                            lruBuildCacheKeyToDataReference.remove(buildCacheKey);
                        }
                    }
                }
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

    private static class ValueWithTimestamp implements Comparable<ValueWithTimestamp> {
        private final long timestamp;
        private final byte[] value;

        private ValueWithTimestamp(long timestamp, byte[] value) {
            this.timestamp = timestamp;
            this.value = value;
        }

        @Override
        public int compareTo(ValueWithTimestamp o) {
            int result = Long.compare(timestamp, o.timestamp);
            if (result != 0) {
                return result;
            }
            return HashCode.compareBytes(value, o.value);
        }

        public byte[] get() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    private static class ComparableByteArray extends BasicDataType<byte[]> {

        private static final ComparableByteArray INSTANCE = new ComparableByteArray();

        @Override
        public int compare(byte[] a, byte[] b) {
            return HashCode.compareBytes(a, b);
        }

        @Override
        public int getMemory(byte[] data) {
            return data.length;
        }

        @Override
        public void write(WriteBuffer buff, byte[] data) {
            buff.putVarInt(data.length);
            buff.put(data);
        }

        @Override
        public byte[] read(ByteBuffer buff) {
            int size = DataUtils.readVarInt(buff);
            byte[] data = new byte[size];
            buff.get(data);
            return data;
        }

        @Override
        public byte[][] createStorage(int size) {
            return new byte[size][];
        }
    }

    private static class ValueWithTimestampType extends BasicDataType<ValueWithTimestamp> {
        private static final ValueWithTimestampType INSTANCE = new ValueWithTimestampType();

        @Override
        public int getMemory(ValueWithTimestamp obj) {
            return Long.BYTES + obj.value.length;
        }

        @Override
        public void write(WriteBuffer buff, ValueWithTimestamp obj) {
            buff.putLong(obj.timestamp);
            buff.putVarInt(obj.value.length);
            buff.put(obj.value);
        }

        @Override
        public ValueWithTimestamp read(ByteBuffer buff) {
            long timestamp = buff.getLong();
            int size = DataUtils.readVarInt(buff);
            byte[] key = new byte[size];
            buff.get(key);
            return new ValueWithTimestamp(timestamp, key);
        }

        @Override
        public ValueWithTimestamp[] createStorage(int size) {
            return new ValueWithTimestamp[size];
        }

        @Override
        public int compare(ValueWithTimestamp a, ValueWithTimestamp b) {
            return a.compareTo(b);
        }
    }
}
