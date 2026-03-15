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
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A persistent indexed cache backed by MapDB.
 *
 * Keys are hashed to longs (via SipHash-2-4) for fast lookup.
 *
 * Tuned for Gradle's single-threaded access pattern (enforced by
 * {@code DefaultCacheCoordinator.stateLock} above):
 * <ul>
 *   <li>{@code concurrencyDisable()} — no internal locking overhead</li>
 *   <li>{@code fileMmapEnableIfSupported()} — fastest file I/O for single-threaded access</li>
 *   <li>{@code fileLockDisable()} — OS file locks above handle multi-process safety</li>
 *   <li>HTreeMap with 1 segment — no segment-level concurrency overhead</li>
 *   <li>Custom {@code org.mapdb.Serializer<V>} — serializes directly to/from MapDB's
 *       {@link DataOutput2}/{@link DataInput2}, eliminating the intermediate {@code byte[]}
 *       that the built-in {@code Serializer.BYTE_ARRAY} approach requires</li>
 * </ul>
 */
@NullMarked
public class MapDBPersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MapDBPersistentIndexedCache.class);
    private static final String MAP_NAME = "cache";
    private static final int KRYO_BUFFER_SIZE = 512;

    private final File cacheFile;
    private final Serializer<V> valueSerializer;
    private final FastKeyHasher<K> keyHasher;
    private DB db;
    private HTreeMap<Long, V> map;

    public MapDBPersistentIndexedCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.cacheFile = cacheFile;
        this.valueSerializer = valueSerializer;
        this.keyHasher = new FastKeyHasher<>(keySerializer);
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
        boolean readOnly = cacheFile.exists() && !cacheFile.canWrite();
        DBMaker.Maker maker = DBMaker
            .fileDB(cacheFile)
            .concurrencyDisable()
            .fileMmapEnableIfSupported()
            .cleanerHackEnable()
            .fileLockDisable()
            .checksumHeaderBypass();
        if (readOnly) {
            maker = maker.readOnly();
        }
        db = maker.make();
        map = db.hashMap(MAP_NAME, org.mapdb.Serializer.LONG, new ValueSerializer())
            .layout(1, 16, 4)
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
            return map.get(hashKey(key));
        } catch (Exception e) {
            rebuild();
            return null;
        }
    }

    @Override
    public void put(K key, V value) {
        try {
            map.put(hashKey(key), value);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(new IOException(String.format("Could not add entry '%s' to %s.", key, this), e), true);
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
            if (db != null && !db.isClosed()) {
                db.close();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to close corrupt store", e);
        } finally {
            db = null;
            map = null;
        }
        try {
            deleteFiles();
            doOpen();
        } catch (Exception e) {
            LOGGER.warn("{} couldn't be rebuilt. Closing.", this);
        }
    }

    private void deleteFiles() {
        cacheFile.delete();
        new File(cacheFile.getPath() + ".p").delete();
    }

    /**
     * Serializes {@code V} values directly to/from MapDB's {@link DataOutput2}/{@link DataInput2},
     * eliminating the intermediate {@code byte[]} that {@code org.mapdb.Serializer.BYTE_ARRAY} requires.
     *
     * <p>{@link DataOutput2} already extends {@link OutputStream}, so it is passed directly to
     * {@link KryoBackedEncoder}. For reads, a resettable {@link BoundedInputStream} wraps
     * {@link DataInput2} as an {@link InputStream} bounded by {@code available} bytes,
     * then passed to {@link KryoBackedDecoder#restart}.
     *
     * <p>Both encoder and decoder are kept in a {@link ThreadLocal} to avoid per-call allocation.
     */
    private class ValueSerializer implements org.mapdb.Serializer<V> {
        private final ThreadLocal<SerializerState> state = ThreadLocal.withInitial(SerializerState::new);

        @Override
        public void serialize(DataOutput2 out, V value) throws IOException {
            SerializerState s = state.get();
            s.outAdapter.setDelegate(out);
            s.encoder.flush(); // clear any leftover from a previous call
            try {
                valueSerializer.write(s.encoder, value);
                s.encoder.flush();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        @Override
        public V deserialize(DataInput2 input, int available) throws IOException {
            SerializerState s = state.get();
            s.inAdapter.reset(input, available);
            s.decoder.restart(s.inAdapter);
            try {
                return valueSerializer.read(s.decoder);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        private class SerializerState {
            final ResettableOutputStream outAdapter = new ResettableOutputStream();
            final KryoBackedEncoder encoder = new KryoBackedEncoder(outAdapter, KRYO_BUFFER_SIZE);
            final BoundedInputStream inAdapter = new BoundedInputStream();
            final KryoBackedDecoder decoder = new KryoBackedDecoder(inAdapter, KRYO_BUFFER_SIZE);
        }
    }

    /**
     * Wraps an {@link OutputStream} delegate that can be swapped per serialize call,
     * allowing {@link KryoBackedEncoder} to be reused across multiple MapDB write operations.
     */
    private static class ResettableOutputStream extends OutputStream {
        private OutputStream delegate;

        void setDelegate(OutputStream out) {
            this.delegate = out;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }
    }

    /**
     * Wraps a {@link DataInput2} as a bounded {@link InputStream}, limited to {@code available}
     * bytes. This prevents {@link KryoBackedDecoder}'s internal read-ahead from consuming
     * bytes belonging to the next entry, and allows the decoder to be reused across calls
     * via {@link KryoBackedDecoder#restart}.
     */
    private static class BoundedInputStream extends InputStream {
        private DataInput2 input;
        private int remaining;

        void reset(DataInput2 input, int available) {
            this.input = input;
            this.remaining = available < 0 ? Integer.MAX_VALUE : available;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            remaining--;
            return input.readUnsignedByte();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = Math.min(len, remaining);
            input.readFully(b, off, toRead);
            remaining -= toRead;
            return toRead;
        }
    }
}
