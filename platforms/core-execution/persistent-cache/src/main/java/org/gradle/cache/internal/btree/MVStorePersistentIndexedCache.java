/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.cache.internal.btree;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.type.ByteArrayDataType;
import org.h2.mvstore.type.LongDataType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A persistent indexed cache backed by H2 MVStore.
 *
 * Keys are hashed to longs (via MD5) for fast lookup, matching the approach
 * used by {@link BTreePersistentIndexedCache}.
 *
 * Writes (puts and removes) are buffered in a heap {@link HashMap}, mirroring
 * the {@code CachingBlockStore} pattern in {@link BTreePersistentIndexedCache}:
 * the write buffer is checked first on reads, falling through to MVStore on a miss.
 * MVStore is only written at close (bulk flush). This eliminates MVStore's
 * per-operation paged-store overhead from the write hot path entirely.
 */
@NullMarked
public class MVStorePersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MVStorePersistentIndexedCache.class);
    private static final String MAP_NAME = "cache";
    private static final int CACHE_SIZE_MB = 32;
    private static final int AUTO_COMMIT_BUFFER_SIZE_KB = 32 * 1024;
    private static final int PAGE_SPLIT_SIZE = 4 * 1024;
    private static final int KRYO_BUFFER_SIZE = 512;
    /** Sentinel stored in {@link #dirty} to represent a removed entry. */
    private static final byte[] TOMBSTONE = new byte[0];

    private final File cacheFile;
    private final Serializer<V> valueSerializer;
    private final KeyHasher<K> keyHasher;
    private final ThreadLocal<SerializationBuffer> serializationBuffers = ThreadLocal.withInitial(SerializationBuffer::new);
    /**
     * Write buffer for this session, mirroring {@code CachingBlockStore#dirty}.
     * Puts are stored as serialized bytes; removes are stored as {@link #TOMBSTONE}.
     * Reads check here first before falling through to MVStore.
     * Flushed to MVStore at close.
     */
    private Map<Long, byte[]> dirty;
    private MVStore store;
    private MVMap<Long, byte[]> map;

    public MVStorePersistentIndexedCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.cacheFile = cacheFile;
        this.valueSerializer = valueSerializer;
        this.keyHasher = new KeyHasher<>(keySerializer);
        try {
            doOpen();
        } catch (Exception e) {
            rebuild();
        }
    }

    @Override
    public String toString() {
        return "cache " + cacheFile.getName() + " (" + cacheFile + ")";
    }

    private void doOpen() {
        cacheFile.getParentFile().mkdirs();
        MVStore.Builder builder = new MVStore.Builder()
            .fileName(cacheFile.getPath())
            .cacheSize(CACHE_SIZE_MB)
            .autoCommitBufferSize(AUTO_COMMIT_BUFFER_SIZE_KB)
            .pageSplitSize(PAGE_SPLIT_SIZE)
            .autoCompactFillRate(0);
        if (cacheFile.exists() && !cacheFile.canWrite()) {
            builder.readOnly();
        }
        store = builder.open();
        MVMap.Builder<Long, byte[]> mapBuilder = new MVMap.Builder<Long, byte[]>()
            .keyType(LongDataType.INSTANCE)
            .valueType(ByteArrayDataType.INSTANCE);
        map = store.openMap(MAP_NAME, mapBuilder);
        dirty = new HashMap<>();
    }

    private long hashKey(K key) {
        try {
            return keyHasher.getHashCode(key);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Nullable
    @Override
    public V get(K key) {
        try {
            long hash = hashKey(key);
            byte[] data = dirty.get(hash);
            if (data == TOMBSTONE) {
                return null;
            }
            if (data == null) {
                data = map.get(hash);
            }
            if (data == null) {
                return null;
            }
            return deserialize(data);
        } catch (Exception e) {
            rebuild();
            return null;
        }
    }

    @Override
    public void put(K key, V value) {
        try {
            dirty.put(hashKey(key), serialize(value));
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(new IOException(String.format("Could not add entry '%s' to %s.", key, this), e), true);
        }
    }

    private byte[] serialize(V value) {
        try {
            SerializationBuffer sb = serializationBuffers.get();
            sb.baos.reset();
            sb.encoder.flush();
            valueSerializer.write(sb.encoder, value);
            sb.encoder.flush();
            int count = sb.baos.getCount();
            byte[] result = new byte[count];
            System.arraycopy(sb.baos.getBuffer(), 0, result, 0, count);
            return result;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private V deserialize(byte[] data) {
        try {
            SerializationBuffer sb = serializationBuffers.get();
            sb.decoderInput.setData(data);
            sb.decoder.restart(sb.decoderInput);
            return valueSerializer.read(sb.decoder);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void remove(K key) {
        try {
            dirty.put(hashKey(key), TOMBSTONE);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(new IOException(String.format("Could not remove entry '%s' from %s.", key, this), e), true);
        }
    }

    @Override
    public void close() {
        if (store != null && !store.isClosed()) {
            try {
                if (!store.isReadOnly()) {
                    flush();
                    store.commit();
                }
                store.close();
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                store = null;
                map = null;
                dirty = null;
            }
        }
    }

    private void flush() {
        for (Map.Entry<Long, byte[]> entry : dirty.entrySet()) {
            if (entry.getValue() == TOMBSTONE) {
                map.remove(entry.getKey());
            } else {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        dirty.clear();
    }

    @Override
    public boolean isOpen() {
        return store != null && !store.isClosed();
    }

    @Override
    public void reset() {
        close();
        try {
            doOpen();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void clear() {
        close();
        cacheFile.delete();
        try {
            doOpen();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void verify() {
        // MVStore handles integrity internally with page checksums
    }

    private void rebuild() {
        LOGGER.warn("{} is corrupt. Discarding.", this);
        try {
            if (store != null && !store.isClosed()) {
                store.closeImmediately();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to close corrupt store", e);
        } finally {
            store = null;
            map = null;
            dirty = null;
        }
        try {
            cacheFile.delete();
            doOpen();
        } catch (Exception e) {
            LOGGER.warn("{} couldn't be rebuilt. Closing.", this);
        }
    }

    private static class SerializationBuffer {
        final FastByteArrayOutputStream baos = new FastByteArrayOutputStream(KRYO_BUFFER_SIZE);
        final KryoBackedEncoder encoder = new KryoBackedEncoder(baos, KRYO_BUFFER_SIZE);
        final ResettableByteArrayInputStream decoderInput = new ResettableByteArrayInputStream();
        final KryoBackedDecoder decoder = new KryoBackedDecoder(decoderInput, KRYO_BUFFER_SIZE);
    }

    /**
     * A ByteArrayOutputStream that exposes its internal buffer to avoid
     * the copy done by {@link ByteArrayOutputStream#toByteArray()}.
     */
    private static class FastByteArrayOutputStream extends ByteArrayOutputStream {
        FastByteArrayOutputStream(int size) {
            super(size);
        }

        byte[] getBuffer() {
            return buf;
        }

        int getCount() {
            return count;
        }
    }

    /**
     * A ByteArrayInputStream whose backing data can be swapped without allocating a new stream,
     * allowing reuse of the KryoBackedDecoder across deserialization calls.
     */
    private static class ResettableByteArrayInputStream extends ByteArrayInputStream {
        ResettableByteArrayInputStream() {
            super(new byte[0]);
        }

        void setData(byte[] data) {
            this.buf = data;
            this.pos = 0;
            this.count = data.length;
            this.mark = 0;
        }
    }
}
