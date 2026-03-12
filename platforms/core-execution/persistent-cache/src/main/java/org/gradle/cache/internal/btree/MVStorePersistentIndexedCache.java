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
import org.h2.mvstore.DataUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.WriteBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * A persistent indexed cache backed by H2 MVStore.
 */
@NullMarked
public class MVStorePersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MVStorePersistentIndexedCache.class);
    private static final String MAP_NAME = "cache";
    private static final int CACHE_SIZE_MB = 16;
    private static final int PAGE_SPLIT_SIZE = 8 * 1024;

    private final File cacheFile;
    private final Serializer<K> keySerializer;
    private final Serializer<V> valueSerializer;
    private MVStore store;
    private MVMap<K, V> map;

    public MVStorePersistentIndexedCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.cacheFile = cacheFile;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
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
            .autoCommitDisabled()
            .cacheSize(CACHE_SIZE_MB)
            .pageSplitSize(PAGE_SPLIT_SIZE)
            .compress();
        if (cacheFile.exists() && !cacheFile.canWrite()) {
            builder.readOnly();
        }
        store = builder.open();
        MVMap.Builder<K, V> mapBuilder = new MVMap.Builder<K, V>()
            .keyType(new CacheDataType<>(keySerializer))
            .valueType(new CacheDataType<>(valueSerializer));
        map = store.openMap(MAP_NAME, mapBuilder);
    }

    @Nullable
    @Override
    public V get(K key) {
        try {
            return map.get(key);
        } catch (Exception e) {
            rebuild();
            return null;
        }
    }

    @Override
    public void put(K key, V value) {
        try {
            map.put(key, value);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(new IOException(String.format("Could not add entry '%s' to %s.", key, this), e), true);
        }
    }

    @Override
    public void remove(K key) {
        try {
            map.remove(key);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(new IOException(String.format("Could not remove entry '%s' from %s.", key, this), e), true);
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
            close();
        } catch (Exception ignored) {
            store = null;
            map = null;
        }
        try {
            cacheFile.delete();
            doOpen();
        } catch (Exception e) {
            LOGGER.warn("{} couldn't be rebuilt. Closing.", this);
            close();
        }
    }

    private static class CacheDataType<T> extends org.h2.mvstore.type.BasicDataType<T> {
        private final Serializer<T> serializer;

        private final ThreadLocal<FastByteArrayOutputStream> bufferPool =
            ThreadLocal.withInitial(FastByteArrayOutputStream::new);

        public CacheDataType(Serializer<T> serializer) {
            this.serializer = serializer;
        }

        @Override
        public int getMemory(T obj) {
            return 128; // Static estimate for MVStore's RAM cache management
        }

        @Override
        public void write(WriteBuffer buff, T obj) {
            try {
                FastByteArrayOutputStream baos = bufferPool.get();
                baos.reset();

                KryoBackedEncoder encoder = new KryoBackedEncoder(baos);
                serializer.write(encoder, obj);
                encoder.flush();

                // Frame the data: Write length, then write directly from the exposed buffer
                buff.putVarInt(baos.getCount());
                buff.put(baos.getBuffer(), 0, baos.getCount());
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public T read(ByteBuffer buff) {
            try {
                // Read length to frame the greedy stream
                int len = DataUtils.readVarInt(buff);

                // Create a strictly bounded, zero-copy slice for Kryo
                ByteBuffer slice = buff.slice();
                slice.limit(len);

                // Advance the main buffer's position so MVStore knows we consumed it
                buff.position(buff.position() + len);

                KryoBackedDecoder decoder = new KryoBackedDecoder(new ByteBufferInputStream(slice));
                return serializer.read(decoder);
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public T[] createStorage(int size) {
            return (T[]) new Object[size];
        }

        @SuppressWarnings("unchecked")
        @Override
        public int compare(T a, T b) {
            if (a instanceof Comparable && b instanceof Comparable) {
                return ((Comparable) a).compareTo(b);
            }
            throw new UnsupportedOperationException("Keys must implement Comparable, but " + a.getClass() + " does not.");
        }

        // Subclass to expose the internal byte array, eliminating the toByteArray() allocation
        private static class FastByteArrayOutputStream extends ByteArrayOutputStream {
            FastByteArrayOutputStream() {
                super(512);
            }

            byte[] getBuffer() {
                return buf;
            }

            int getCount() {
                return count;
            }
        }
    }

    private static class ByteBufferInputStream extends InputStream {

        private final ByteBuffer buffer;

        public ByteBufferInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public int read() {
            if (!buffer.hasRemaining()) {
                return -1;
            }
            // Bitwise AND ensures we return an unsigned byte value (0-255) as required by InputStream
            return buffer.get() & 0xFF;
        }

        @Override
        public int read(byte[] bytes, int off, int len) {
            if (!buffer.hasRemaining()) {
                return -1;
            }

            // Only read as much as is available in the buffer
            int length = Math.min(len, buffer.remaining());
            buffer.get(bytes, off, length);
            return length;
        }
    }
}
