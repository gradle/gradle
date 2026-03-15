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
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.StreamStore;
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
import java.io.InputStream;

/**
 * A persistent indexed cache backed by H2 MVStore.
 *
 * Keys are hashed to longs (via SipHash-2-4) for fast lookup.
 *
 * For complex value types (non-{@link BaseSerializerFactory} serializers), values are stored
 * via {@link StreamStore}, which splits large values into fixed-size chunks. This avoids
 * MVStore's {@code WriteBuffer} growing to accommodate single large byte arrays on each
 * {@code put()}, and avoids allocating a full-value {@code byte[]} on each {@code get()}.
 *
 * For simple value types ({@link BaseSerializerFactory} serializers such as {@code String},
 * {@code Long}, etc.), values are stored inline as {@code byte[]} in the main map — these
 * are small enough that StreamStore's chunking overhead is not worthwhile.
 */
@NullMarked
public class MVStorePersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MVStorePersistentIndexedCache.class);
    private static final String MAP_NAME = "cache";
    private static final String STREAM_CHUNKS_MAP_NAME = "cache.chunks";
    private static final int CACHE_SIZE_MB = 32;
    private static final int AUTO_COMMIT_BUFFER_SIZE_KB = 32 * 1024;
    private static final int PAGE_SPLIT_SIZE = 4 * 1024;
    private static final int KRYO_BUFFER_SIZE = 512;
    /** Sentinel value stored in the map to represent a removed entry. */
    private static final byte[] TOMBSTONE = new byte[0];

    private final File cacheFile;
    private final Serializer<V> valueSerializer;
    private final FastKeyHasher<K> keyHasher;
    private final boolean useStreamStore;
    private final ThreadLocal<SerializationBuffer> serializationBuffers = ThreadLocal.withInitial(SerializationBuffer::new);
    private MVStore store;
    private MVMap<Long, byte[]> map;
    @Nullable private StreamStore streamStore;

    public MVStorePersistentIndexedCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.cacheFile = cacheFile;
        this.valueSerializer = valueSerializer;
        this.keyHasher = new FastKeyHasher<>(keySerializer);
        this.useStreamStore = !isSimpleSerializer(valueSerializer);
        try {
            doOpen();
        } catch (Exception e) {
            rebuild();
        }
    }

    /**
     * Returns true if the serializer is a simple/small-value serializer from
     * {@link BaseSerializerFactory} (e.g. String, Long, File). These produce small
     * byte arrays that are cheaper to store inline than via StreamStore.
     */
    private static boolean isSimpleSerializer(Serializer<?> serializer) {
        return serializer.getClass().getEnclosingClass() == BaseSerializerFactory.class;
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
            .cacheConcurrency(1)
            .autoCompactFillRate(0);
        if (cacheFile.exists() && !cacheFile.canWrite()) {
            builder.readOnly();
        }
        store = builder.open();
        MVMap.Builder<Long, byte[]> mapBuilder = new MVMap.Builder<Long, byte[]>()
            .keyType(LongDataType.INSTANCE)
            .valueType(ByteArrayDataType.INSTANCE);
        map = store.openMap(MAP_NAME, mapBuilder);
        if (useStreamStore) {
            MVMap<Long, byte[]> chunkMap = store.openMap(STREAM_CHUNKS_MAP_NAME, new MVMap.Builder<Long, byte[]>()
                .keyType(LongDataType.INSTANCE)
                .valueType(ByteArrayDataType.INSTANCE));
            streamStore = new StreamStore(chunkMap);
        }
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
            byte[] data = map.get(hashKey(key));
            if (data == null || data.length == 0) {
                return null;
            }
            if (useStreamStore) {
                return deserializeStream(streamStore.get(data));
            } else {
                return deserialize(data);
            }
        } catch (Exception e) {
            rebuild();
            return null;
        }
    }

    @Override
    public void put(K key, V value) {
        try {
            long hash = hashKey(key);
            if (useStreamStore) {
                removeStreamChunks(map.get(hash));
                byte[] streamRef = streamStore.put(serializeToStream(value));
                map.put(hash, streamRef);
            } else {
                map.put(hash, serialize(value));
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(new IOException(String.format("Could not add entry '%s' to %s.", key, this), e), true);
        }
    }

    @Override
    public void remove(K key) {
        try {
            long hash = hashKey(key);
            if (useStreamStore) {
                removeStreamChunks(map.get(hash));
            }
            map.put(hash, TOMBSTONE);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(new IOException(String.format("Could not remove entry '%s' from %s.", key, this), e), true);
        }
    }

    /** Removes the stream chunks for {@code existingRef} if it is a live stream reference. */
    private void removeStreamChunks(byte @Nullable [] existingRef) {
        if (existingRef != null && existingRef.length > 0) {
            streamStore.remove(existingRef);
        }
    }

    /**
     * Serializes {@code value} into the ThreadLocal reusable buffer and returns a
     * {@link ByteArrayInputStream} view over it. Safe to pass to {@link StreamStore#put}
     * since {@code put} reads the stream synchronously before returning.
     */
    private ByteArrayInputStream serializeToStream(V value) {
        try {
            SerializationBuffer sb = serializationBuffers.get();
            sb.baos.reset();
            sb.encoder.flush();
            valueSerializer.write(sb.encoder, value);
            sb.encoder.flush();
            return new ByteArrayInputStream(sb.baos.getBuffer(), 0, sb.baos.getCount());
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
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

    private V deserializeStream(InputStream in) {
        try {
            SerializationBuffer sb = serializationBuffers.get();
            sb.decoder.restart(in);
            return valueSerializer.read(sb.decoder);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void close() {
        if (store != null && !store.isClosed()) {
            try {
                if (!store.isReadOnly()) {
                    store.commit();
                }
                store.close();
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                store = null;
                map = null;
                streamStore = null;
            }
        }
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
            streamStore = null;
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
