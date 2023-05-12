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
import org.gradle.caching.internal.DefaultBuildCacheKey;
import org.gradle.caching.internal.StatefulNextGenBuildCacheService;
import org.gradle.internal.hash.HashCode;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.StreamStore;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
        lruStreamMap = new LruStreamMap(
            mvStore.openMap("timestampToKey"),
            mvStore.openMap("keyToTimestamp"),
            mvStore.openMap("keyToIndex"),
            new StreamStore(mvStore.openMap("data")),
            maxConcurrency
        );
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
        private final MVMap<Long, byte[]> lruKeyToIndex;
        private final MVMap<byte[], Long> keyToTimestamp;
        private final ConcurrentMap<byte[], byte[]> keyToIndex;
        private final StreamStore data;

        public LruStreamMap(MVMap<Long, byte[]> lruKeyToIndex, MVMap<byte[], Long> keyToTimestamp, MVMap<byte[], byte[]> keyToIndex, StreamStore data, int maxConcurrency) {
            this.lruKeyToIndex = lruKeyToIndex;
            this.keyToTimestamp = keyToTimestamp;
            this.keyToIndex = keyToIndex;
            this.data = data;
            int maxConcurrencyTimesTwo = (Integer.MAX_VALUE / 2) <= maxConcurrency ? Integer.MAX_VALUE : (maxConcurrency * 2);
            this.locks = Striped.lazyWeakLock(Math.max(maxConcurrencyTimesTwo, MINIMUM_LOCKS));
        }

        public void putIfAbsent(BuildCacheKey key, Supplier<InputStream> inputStreamSupplier) {
            byte[] keyAsBytes = key.toByteArray();
            keyToIndex.computeIfAbsent(keyAsBytes, k -> {
                try (InputStream input = inputStreamSupplier.get()) {
                    byte[] id = data.put(input);
                    keyToIndex.put(keyAsBytes, id);
                    long timestamp = System.currentTimeMillis();
                    lruKeyToIndex.put(timestamp, keyAsBytes);
                    keyToTimestamp.put(keyAsBytes, timestamp);
                    return id;
                } catch (IOException e) {
                    throw new BuildCacheException("storing " + key, e);
                }
            });
        }

        public boolean containsKey(BuildCacheKey key) {
            return keyToIndex.containsKey(key.toByteArray());
        }

        @Nullable
        public InputStream get(BuildCacheKey key) {
            byte[] keyAsBytes = key.toByteArray();
            byte[] index = keyToIndex.get(keyAsBytes);
            if (index == null) {
                return null;
            }
            InputStream inputStream = data.get(index);
            if (inputStream == null) {
                return null;
            }
            tryUpdateTimestamp(key, keyAsBytes);
            return inputStream;
        }

        /**
         * Tries to update usage timestamp for a cache key.
         * If some other thread is accessing the same key (either delete or another get) then operation is skipped.
         *
         * That is ok, since if delete is in progress, then we don't need to update timestamp.
         * And if another get is in progress, timestamp will be updated anyway.
         */
        private void tryUpdateTimestamp(BuildCacheKey key, byte[] keyAsBytes) {
            tryLockAndRun(key, () -> keyToIndex.computeIfPresent(keyAsBytes, (k, id) -> {
                Long timestamp = keyToTimestamp.remove(keyAsBytes);
                lruKeyToIndex.remove(timestamp);
                data.remove(id);
                return null;
            }));
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
            byte[] keyAsBytes = key.toByteArray();
            lockAndRun(key, () -> keyToIndex.computeIfPresent(keyAsBytes, (k, id) -> {
                Long timestamp = keyToTimestamp.remove(keyAsBytes);
                lruKeyToIndex.remove(timestamp);
                data.remove(id);
                return null;
            }));
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
            Long firstKey = lruKeyToIndex.firstKey();
            if (firstKey == null) {
                return Stream.empty();
            }
            Spliterator<Long> keySpliterator = Spliterators.spliteratorUnknownSize(lruKeyToIndex.keyIterator(firstKey), Spliterator.ORDERED);
            return StreamSupport.stream(keySpliterator, false)
                .map(key -> new DefaultBuildCacheKey(HashCode.fromBytes(lruKeyToIndex.get(key))));
        }
    }
}
