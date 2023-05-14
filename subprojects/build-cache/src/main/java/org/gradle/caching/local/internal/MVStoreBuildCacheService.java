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

package org.gradle.caching.local.internal;

import com.google.common.util.concurrent.Striped;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.StatefulNextGenBuildCacheService;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;
import org.h2.mvstore.StreamStore;
import org.h2.mvstore.tx.Transaction;
import org.h2.mvstore.tx.TransactionMap;
import org.h2.mvstore.tx.TransactionStore;
import org.h2.mvstore.tx.VersionedValueType;
import org.h2.mvstore.type.ByteArrayDataType;
import org.h2.mvstore.type.LongDataType;
import org.h2.mvstore.type.StringDataType;
import org.h2.value.VersionedValue;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class MVStoreBuildCacheService implements StatefulNextGenBuildCacheService {

    private final int maxConcurrency;
    private MVStore mvStore;
    private LruStreamMap lruStreamMap;
    private final Path dbPath;

    public MVStoreBuildCacheService(Path dbPath, int maxConcurrency) {
        this.dbPath = dbPath;
        this.maxConcurrency = maxConcurrency;
    }

    @Override
    public void open() {
        mvStore = new MVStore.Builder()
            .fileName(dbPath.resolve("filestore").toString())
            .autoCompactFillRate(0)
            .open();
        lruStreamMap = new LruStreamMap(new TransactionStore(mvStore));
    }

    @Override
    public boolean contains(BuildCacheKey key) {
        return lruStreamMap.containsKey(key);
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        try (InputStream inputStream = lruStreamMap.get(key)) {
            if (inputStream != null) {
                reader.readFrom(inputStream);
                return true;
            }
            return false;
        } catch (IOException e) {
            throw new BuildCacheException("loading " + key, e);
        }
    }

    @Override
    public void store(BuildCacheKey key, NextGenWriter writer) throws BuildCacheException {
        lruStreamMap.putIfAbsent(key, () -> {
            try {
                return writer.openStream();
            } catch (IOException e) {
                throw new BuildCacheException("storing " + key, e);
            }
        });
    }

    @Override
    public void close() {
        mvStore.close();
    }

    private static class LruStreamMap {

        private static final int MINIMUM_LOCKS = 512;
        private final Striped<Lock> locks;
        private final MVMap<String, VersionedValue<byte[]>> keyToIndex;
        private final StreamStore data;
        private final TransactionStore store;

        public LruStreamMap(TransactionStore store) {
            this.store = store;
            this.keyToIndex = store.openMap("keyToIndex", StringDataType.INSTANCE, new VersionedValueType<>(ByteArrayDataType.INSTANCE));
            this.data = new StreamStore(store.openMap("data", LongDataType.INSTANCE, ByteArrayDataType.INSTANCE));
            int maxConcurrencyTimesTwo = (Integer.MAX_VALUE / 2) <= 5 ? Integer.MAX_VALUE : (5 * 2);
            this.locks = Striped.lazyWeakLock(Math.max(maxConcurrencyTimesTwo, MINIMUM_LOCKS));
        }

        public TransactionMap<String, byte[]> getTxKeyToIndex(Transaction tx) {
            return tx.openMapX(this.keyToIndex);
        }

        public TransactionMap<Long, byte[]> getTxTimestampToIndex(Transaction tx) {
            return tx.openMap("timestampToIndex");
        }

        public void putIfAbsent(BuildCacheKey key, Supplier<InputStream> inputStreamSupplier) {
            if (!keyToIndex.containsKey(key.getHashCode())) {
                byte[] id = null;
                Transaction tx = store.begin();
                try(InputStream inputStream = inputStreamSupplier.get()) {
                    id = data.put(inputStream);
                    getTxTimestampToIndex(tx).put(System.currentTimeMillis(), id);
                    getTxKeyToIndex(tx).put(key.getHashCode(), id);
                    tx.commit();
                } catch (IOException | MVStoreException e) {
                    tx.rollback();
                    if (id != null) {
                        data.remove(id);
                    }
                }
            }
        }

        public boolean containsKey(BuildCacheKey key) {
            return keyToIndex.containsKey(key.getHashCode());
        }

        @Nullable
        public InputStream get(BuildCacheKey key) {
            byte[] keyAsBytes = key.toByteArray();
            VersionedValue<byte[]> index = keyToIndex.get(key.getHashCode());
            if (index == null) {
                return null;
            }
            InputStream inputStream = data.get(index.getCommittedValue());
            if (inputStream == null) {
                return null;
            }
            tryUpdateTimestamp(key, keyAsBytes, index);
            return inputStream;
        }

        /**
         * Tries to update usage timestamp for a cache key.
         * If some other thread is accessing the same key (either delete or another get) then operation is skipped.
         *
         * That is ok, since if delete is in progress, then we don't need to update timestamp.
         * And if another get is in progress, timestamp will be updated anyway.
         */
        private void tryUpdateTimestamp(BuildCacheKey key, byte[] keyAsBytes, VersionedValue<byte[]> index) {
            Transaction tx = store.begin();
            tx.setTimeoutMillis(0);
            try {
                getTxKeyToIndex(tx).lock(key.getHashCode());
                getTxTimestampToIndex(tx).put(System.currentTimeMillis(), index.getCommittedValue());
                tx.commit();
            } catch (MVStoreException e) {
                tx.rollback();
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

        public void delete(BuildCacheKey key) {
//            byte[] keyAsBytes = key.toByteArray();
//            lockAndRun(key, () -> keyToIndex.computeIfPresent(keyAsBytes, (k, id) -> {
//                Long timestamp = keyToTimestamp.remove(keyAsBytes);
//                lruKeyToIndex.remove(timestamp);
//                data.remove(id);
//                return null;
//            }));
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

        public Stream<BuildCacheKey> keyStream() {
//            Long firstKey = lruKeyToIndex.firstKey();
//            if (firstKey == null) {
//                return Stream.empty();
//            }
//            Spliterator<Long> keySpliterator = Spliterators.spliteratorUnknownSize(lruKeyToIndex.keyIterator(firstKey), Spliterator.ORDERED);
//            return StreamSupport.stream(keySpliterator, false)
//                .map(key -> new DefaultBuildCacheKey(HashCode.fromBytes(lruKeyToIndex.get(key))));
            return null;
        }
    }
}
