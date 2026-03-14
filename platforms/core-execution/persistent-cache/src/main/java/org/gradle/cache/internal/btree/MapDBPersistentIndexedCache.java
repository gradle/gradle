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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * A persistent indexed cache backed by MapDB.
 *
 * Keys are hashed to longs (via MD5) for fast lookup,
 * matching the approach used by {@link BTreePersistentIndexedCache}.
 * This trades hash-collision safety for significantly faster lookups.
 *
 * Values are stored as raw byte arrays. MapDB's HTreeMap provides O(1)
 * hash-based access with no MVCC overhead, making it efficient under
 * Gradle's externally-serialized access pattern.
 */
@NullMarked
public class MapDBPersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapDBPersistentIndexedCache.class);
    private static final String MAP_NAME = "cache";
    private static final int KRYO_BUFFER_SIZE = 512;

    private final File cacheFile;
    private final Serializer<V> valueSerializer;
    private final KeyHasher<K> keyHasher;
    private final ThreadLocal<SerializationBuffer> serializationBuffers = ThreadLocal.withInitial(SerializationBuffer::new);
    private DB db;
    private HTreeMap<Long, byte[]> map;

    public MapDBPersistentIndexedCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
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
        DBMaker.Maker maker = DBMaker
            .fileDB(cacheFile)
            .fileMmapEnableIfSupported()
            .fileLockDisable();
        if (cacheFile.exists() && !cacheFile.canWrite()) {
            maker = maker.readOnly();
        }
        db = maker.make();
        map = db.hashMap(MAP_NAME, org.mapdb.Serializer.LONG, org.mapdb.Serializer.BYTE_ARRAY)
            .createOrOpen();
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
            map.put(hashKey(key), serialize(value));
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
            map.remove(hashKey(key));
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(new IOException(String.format("Could not remove entry '%s' from %s.", key, this), e), true);
        }
    }

    @Override
    public void close() {
        if (db != null && !db.isClosed()) {
            try {
                db.close();
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                db = null;
                map = null;
            }
        }
    }

    @Override
    public boolean isOpen() {
        return db != null && !db.isClosed();
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
        deleteFiles();
        try {
            doOpen();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void verify() {
        // MapDB handles integrity internally
    }

    private void rebuild() {
        LOGGER.warn("{} is corrupt. Discarding.", this);
        try {
            close();
        } catch (Exception ignored) {
            db = null;
            map = null;
        }
        try {
            deleteFiles();
            doOpen();
        } catch (Exception e) {
            LOGGER.warn("{} couldn't be rebuilt. Closing.", this);
            close();
        }
    }

    private void deleteFiles() {
        cacheFile.delete();
        // MapDB may create a .p (wal) companion file
        new File(cacheFile.getPath() + ".p").delete();
    }

    private static class SerializationBuffer {
        final FastByteArrayOutputStream baos = new FastByteArrayOutputStream(KRYO_BUFFER_SIZE);
        final KryoBackedEncoder encoder = new KryoBackedEncoder(baos, KRYO_BUFFER_SIZE);
        final ResettableByteArrayInputStream decoderInput = new ResettableByteArrayInputStream();
        final KryoBackedDecoder decoder = new KryoBackedDecoder(decoderInput, KRYO_BUFFER_SIZE);
    }

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
