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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A persistent indexed cache backed by an append-only data log and a hash-table index file.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>{@code put()} and {@code remove()} buffer in a {@link ConcurrentHashMap} — zero disk I/O.</li>
 *   <li>{@code flush()} appends buffered entries to the data file and keeps the bytes in a secondary map.</li>
 *   <li>{@code close()} compacts only when dirty: rewrites data + index, updating last-access timestamps.</li>
 *   <li>{@code get()} checks three tiers: pending writes → flushed bytes → on-disk index.
 *       The index is a ByteBuffer in memory. Values from the on-disk tier are read via
 *       {@link FileChannel#read(ByteBuffer, long)} (positional read / pread), which is
 *       thread-safe without synchronization, enabling true concurrent reads.</li>
 * </ul>
 *
 * <h3>File layout</h3>
 * <pre>
 *   &lt;base&gt;.dat  — append-only data log: [keyHash:8][valueLen:4][value:N][type:1] per entry
 *   &lt;base&gt;.idx  — hash-table index:     header(16) + N × [keyHash:8][offset:8][lastAccess:8]
 * </pre>
 */
@NullMarked
public class LogHashPersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogHashPersistentIndexedCache.class);

    static final int MAGIC = 0x47484153; // "GHAS"
    private static final int HEADER_SIZE = 16;    // magic(4) + bucketCount(4) + entryCount(4) + reserved(4)
    private static final int BUCKET_SIZE = 24;    // keyHash(8) + offset(8) + lastAccess(8)
    private static final byte ENTRY_PUT = 1;
    private static final byte[] TOMBSTONE = new byte[0];
    private static final int KRYO_BUFFER_SIZE = 512;
    private static final int ENTRY_HEADER_SIZE = 12; // keyHash(8) + valueLen(4)
    private static final int ENTRY_TYPE_SIZE = 1;

    private final File baseFile;
    private final File dataFile;
    private final File indexFile;
    private final Serializer<V> valueSerializer;
    private final FastKeyHasher<K> keyHasher;

    // Tier 1: buffered writes not yet on disk (serialized bytes)
    private final ConcurrentHashMap<Long, byte[]> pendingWrites = new ConcurrentHashMap<>();

    // Tier 2: flushed since last compaction — holds serialized bytes in memory
    private final ConcurrentHashMap<Long, byte[]> flushedWrites = new ConcurrentHashMap<>();

    // Tier 3: index loaded into ByteBuffer; values read via FileChannel pread (thread-safe)
    private volatile ByteBuffer indexBuffer;
    private volatile FileChannel dataChannel;
    private volatile int bucketCount;
    private volatile int bucketMask;

    private final Set<Long> accessedKeys = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<SerializationBuffer> serializationBuffers = ThreadLocal.withInitial(SerializationBuffer::new);
    // Reusable speculative read buffer: header(12) + value — sized for typical entries.
    // Avoids 2-syscall reads for values ≤ (SPECULATIVE_READ_SIZE - ENTRY_HEADER_SIZE) bytes.
    private static final int SPECULATIVE_READ_SIZE = 512;
    private final ThreadLocal<ByteBuffer> readBuffers = ThreadLocal.withInitial(() -> ByteBuffer.allocate(SPECULATIVE_READ_SIZE));

    private volatile boolean open;
    private volatile boolean dirty;
    // Data file length at open time — everything before this offset is covered by the index.
    // rebuildIndex() only scans from here to find flushed entries.
    private long dataFileBaseLength;

    public LogHashPersistentIndexedCache(File baseFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
        this.baseFile = baseFile;
        String basePath = baseFile.getPath();
        int dotIndex = basePath.lastIndexOf('.');
        String base = dotIndex >= 0 ? basePath.substring(0, dotIndex) : basePath;
        this.dataFile = new File(base + ".dat");
        this.indexFile = new File(base + ".idx");
        this.valueSerializer = valueSerializer;
        this.keyHasher = new FastKeyHasher<>(keySerializer);
    }

    private void ensureOpen() {
        if (!open) {
            synchronized (this) {
                if (!open) {
                    try {
                        doOpen();
                    } catch (Exception e) {
                        rebuild();
                    }
                }
            }
        }
    }

    private void doOpen() throws IOException {
        baseFile.getParentFile().mkdirs();
        loadIndex();
        if (dataFile.exists() && dataFile.length() > 0) {
            dataChannel = FileChannel.open(dataFile.toPath(), StandardOpenOption.READ);
            dataFileBaseLength = dataFile.length();
        } else {
            dataFileBaseLength = 0;
        }
        open = true;
    }

    private void loadIndex() throws IOException {
        if (indexFile.exists() && indexFile.length() >= HEADER_SIZE) {
            byte[] raw = Files.readAllBytes(indexFile.toPath());
            ByteBuffer buf = ByteBuffer.wrap(raw);
            int magic = buf.getInt(0);
            if (magic != MAGIC) {
                LOGGER.warn("{} has invalid magic. Starting fresh.", this);
                initEmpty();
                return;
            }
            int bc = buf.getInt(4);
            if (bc <= 0 || (bc & (bc - 1)) != 0) {
                LOGGER.warn("{} has invalid bucket count. Starting fresh.", this);
                initEmpty();
                return;
            }
            int expectedSize = HEADER_SIZE + bc * BUCKET_SIZE;
            if (raw.length < expectedSize) {
                LOGGER.warn("{} index file truncated. Starting fresh.", this);
                initEmpty();
                return;
            }
            indexBuffer = buf;
            bucketCount = bc;
            bucketMask = bc - 1;
        } else {
            initEmpty();
        }
    }

    private void initEmpty() {
        bucketCount = 0;
        bucketMask = 0;
        indexBuffer = null;
    }

    @Override
    public String toString() {
        return "cache " + baseFile.getName() + " (" + baseFile + ")";
    }

    @Override
    public boolean supportsConcurrentReads() {
        return true;
    }

    @Nullable
    @Override
    public V get(K key) {
        ensureOpen();
        try {
            long hash = hashKey(key);

            // Tier 1: pending writes
            byte[] pending = pendingWrites.get(hash);
            if (pending != null) {
                if (pending == TOMBSTONE) {
                    return null;
                }
                accessedKeys.add(hash);
                return deserialize(pending);
            }

            // Tier 2: flushed bytes
            byte[] flushed = flushedWrites.get(hash);
            if (flushed != null) {
                if (flushed == TOMBSTONE) {
                    return null;
                }
                accessedKeys.add(hash);
                return deserialize(flushed);
            }

            // Tier 3: index → FileChannel positional read (thread-safe, no sync needed)
            ByteBuffer idx = indexBuffer;
            FileChannel ch = dataChannel;
            if (idx != null && ch != null && bucketCount > 0) {
                int bucket = (int) (hash & bucketMask);
                for (int probe = 0; probe < bucketCount; probe++) {
                    int pos = HEADER_SIZE + ((bucket + probe) & bucketMask) * BUCKET_SIZE;
                    long storedHash = idx.getLong(pos);
                    if (storedHash == 0) {
                        return null;
                    }
                    if (storedHash == hash) {
                        long offset = idx.getLong(pos + 8);
                        accessedKeys.add(hash);
                        return readValueFromChannel(ch, offset);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            rebuild();
            return null;
        }
    }

    @Override
    public void put(K key, V value) {
        ensureOpen();
        try {
            long hash = hashKey(key);
            byte[] serialized = serialize(value);
            pendingWrites.put(hash, serialized);
            accessedKeys.add(hash);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(
                new IOException(String.format("Could not add entry '%s' to %s.", key, this), e), true);
        }
    }

    @Override
    public void remove(K key) {
        ensureOpen();
        try {
            long hash = hashKey(key);
            pendingWrites.put(hash, TOMBSTONE);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(
                new IOException(String.format("Could not remove entry '%s' from %s.", key, this), e), true);
        }
    }

    @Override
    public void flush() {
        if (!open || pendingWrites.isEmpty()) {
            return;
        }
        try {
            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dataFile, true), 8192)) {
                for (Map.Entry<Long, byte[]> entry : pendingWrites.entrySet()) {
                    long hash = entry.getKey();
                    byte[] value = entry.getValue();
                    if (value == TOMBSTONE) {
                        flushedWrites.put(hash, TOMBSTONE);
                    } else {
                        writeEntry(bos, hash, value, ENTRY_PUT);
                        flushedWrites.put(hash, value);
                    }
                }
                bos.flush();
            }
            pendingWrites.clear();
            dirty = true;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void close() {
        if (!open) {
            return;
        }
        try {
            flush();
            if (dirty) {
                // Reopen data channel to see bytes appended by flush()
                closeDataChannel();
                if (dataFile.exists() && dataFile.length() > 0) {
                    dataChannel = FileChannel.open(dataFile.toPath(), StandardOpenOption.READ);
                }
                rebuildIndex();
            }
        } catch (Exception e) {
            LOGGER.warn("Error rebuilding index for {}. Data may be stale.", this, e);
        } finally {
            closeQuietly();
            open = false;
        }
    }

    /**
     * Incrementally rebuilds the index file.
     * Starts with existing index entries (already in memory), then scans only the
     * new tail of the data file (from {@link #dataFileBaseLength}) to pick up flushed entries.
     * For a warm daemon with 50K existing + 50 new entries, scans ~50 headers instead of 50K.
     */
    private void rebuildIndex() throws IOException {
        long now = System.currentTimeMillis();
        Map<Long, long[]> entries = new LinkedHashMap<>();

        // Start with existing index entries (already in memory — zero I/O)
        ByteBuffer idx = indexBuffer;
        if (idx != null && bucketCount > 0) {
            for (int i = 0; i < bucketCount; i++) {
                int pos = HEADER_SIZE + i * BUCKET_SIZE;
                long storedHash = idx.getLong(pos);
                if (storedHash != 0) {
                    long offset = idx.getLong(pos + 8);
                    long lastAccess = idx.getLong(pos + 16);
                    if (accessedKeys.contains(storedHash)) {
                        lastAccess = now;
                    }
                    entries.put(storedHash, new long[]{offset, lastAccess});
                }
            }
        }

        // Scan only the NEW tail of the data file (entries appended by flush() this session).
        FileChannel ch = dataChannel;
        if (ch != null && ch.size() > dataFileBaseLength) {
            long fileSize = ch.size();
            long tailSize = fileSize - dataFileBaseLength;
            int scanBufSize = (int) Math.min(65536, tailSize + 1);
            ByteBuffer scanBuf = ByteBuffer.allocate(scanBufSize);
            int bufPos = 0;

            scanBuf.clear();
            int bufLimit = ch.read(scanBuf, dataFileBaseLength);
            scanBuf.flip();
            long filePos = dataFileBaseLength;

            long entryOffset = dataFileBaseLength;
            while (entryOffset + ENTRY_HEADER_SIZE <= fileSize) {
                if (bufPos + ENTRY_HEADER_SIZE > bufLimit) {
                    long readFrom = filePos + bufPos;
                    scanBuf.clear();
                    int read = ch.read(scanBuf, readFrom);
                    if (read <= 0) {
                        break;
                    }
                    scanBuf.flip();
                    filePos = readFrom;
                    bufPos = 0;
                    bufLimit = read;
                    if (bufLimit < ENTRY_HEADER_SIZE) {
                        break;
                    }
                }

                long hash = scanBuf.getLong(bufPos);
                int valueLen = scanBuf.getInt(bufPos + 8);
                if (valueLen < 0 || entryOffset + ENTRY_HEADER_SIZE + valueLen + ENTRY_TYPE_SIZE > fileSize) {
                    break;
                }

                // Flushed entries override existing index entries (latest value wins)
                entries.put(hash, new long[]{entryOffset, now});

                int entrySize = ENTRY_HEADER_SIZE + valueLen + ENTRY_TYPE_SIZE;
                entryOffset += entrySize;
                bufPos += entrySize;
            }
        }

        // Apply tombstones from flushedWrites
        for (Map.Entry<Long, byte[]> entry : flushedWrites.entrySet()) {
            if (entry.getValue() == TOMBSTONE) {
                entries.remove(entry.getKey());
            }
        }

        if (entries.isEmpty()) {
            closeDataChannel();
            deleteFiles();
            return;
        }

        // Write new index file
        int entryCount = entries.size();
        int newBucketCount = nextPowerOfTwo(entryCount * 4 / 3 + 1);
        int newBucketMask = newBucketCount - 1;

        byte[] newIndexBytes = new byte[HEADER_SIZE + newBucketCount * BUCKET_SIZE];
        ByteBuffer newIdx = ByteBuffer.wrap(newIndexBytes);
        newIdx.putInt(0, MAGIC);
        newIdx.putInt(4, newBucketCount);
        newIdx.putInt(8, entryCount);

        for (Map.Entry<Long, long[]> entry : entries.entrySet()) {
            long hash = entry.getKey();
            long[] meta = entry.getValue();
            long offset = meta[0];
            long lastAccess = meta[1];

            int bucket = (int) (hash & newBucketMask);
            for (int probe = 0; probe < newBucketCount; probe++) {
                int idxPos = HEADER_SIZE + ((bucket + probe) & newBucketMask) * BUCKET_SIZE;
                if (newIdx.getLong(idxPos) == 0) {
                    newIdx.putLong(idxPos, hash);
                    newIdx.putLong(idxPos + 8, offset);
                    newIdx.putLong(idxPos + 16, lastAccess);
                    break;
                }
            }
        }

        File newIndexFile = new File(indexFile.getPath() + ".new");
        Files.write(newIndexFile.toPath(), newIndexBytes);
        Files.move(newIndexFile.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Reads and deserializes a value from the data file using positional read (pread).
     * Thread-safe: {@link FileChannel#read(ByteBuffer, long)} does not modify channel position.
     *
     * <p>Uses a speculative single-pread: reads header + value in one syscall for values
     * ≤ {@code SPECULATIVE_READ_SIZE - ENTRY_HEADER_SIZE} bytes (covers ~99% of entries).
     * Falls back to a second pread for larger values.
     */
    @Nullable
    private V readValueFromChannel(FileChannel ch, long offset) throws IOException {
        ByteBuffer buf = readBuffers.get();
        buf.clear();
        int read = ch.read(buf, offset);
        if (read < ENTRY_HEADER_SIZE) {
            return null;
        }
        buf.flip();
        buf.getLong(); // skip keyHash
        int valueLen = buf.getInt();
        if (valueLen < 0 || valueLen > 64 * 1024 * 1024) {
            return null;
        }

        // Use reusable thread-local buffer — zero allocation for reads
        SerializationBuffer sb = serializationBuffers.get();
        byte[] valueBuf = sb.ensureReadCapacity(valueLen);

        int available = buf.remaining(); // bytes already read after header
        if (available >= valueLen) {
            // Fast path: value fits in speculative read — single syscall, zero alloc
            buf.get(valueBuf, 0, valueLen);
        } else {
            // Slow path: copy partial + second pread
            buf.get(valueBuf, 0, available);
            ByteBuffer remainder = ByteBuffer.wrap(valueBuf, available, valueLen - available);
            read = ch.read(remainder, offset + ENTRY_HEADER_SIZE + available);
            if (read < valueLen - available) {
                return null;
            }
        }

        // Deserialize directly from reusable buffer
        sb.decoderInput.setData(valueBuf, valueLen);
        sb.decoder.restart(sb.decoderInput);
        try {
            return valueSerializer.read(sb.decoder);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private long hashKey(K key) {
        try {
            return keyHasher.getHashCode(key);
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

    private static void writeEntry(BufferedOutputStream bos, long hash, byte[] value, byte type) throws IOException {
        writeLong(bos, hash);
        writeInt(bos, value.length);
        bos.write(value);
        bos.write(type);
    }

    private static void writeLong(BufferedOutputStream bos, long value) throws IOException {
        bos.write((int) (value >> 56) & 0xFF);
        bos.write((int) (value >> 48) & 0xFF);
        bos.write((int) (value >> 40) & 0xFF);
        bos.write((int) (value >> 32) & 0xFF);
        bos.write((int) (value >> 24) & 0xFF);
        bos.write((int) (value >> 16) & 0xFF);
        bos.write((int) (value >> 8) & 0xFF);
        bos.write((int) value & 0xFF);
    }

    private static void writeInt(BufferedOutputStream bos, int value) throws IOException {
        bos.write((value >> 24) & 0xFF);
        bos.write((value >> 16) & 0xFF);
        bos.write((value >> 8) & 0xFF);
        bos.write(value & 0xFF);
    }

    static int nextPowerOfTwo(int value) {
        if (value <= 1) {
            return 1;
        }
        return Integer.highestOneBit(value - 1) << 1;
    }

    @Override
    public boolean isOpen() {
        return open;
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
        pendingWrites.clear();
        flushedWrites.clear();
        accessedKeys.clear();
        closeQuietly();
        open = false;
        deleteFiles();
        try {
            doOpen();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void verify() {
        ByteBuffer idx = indexBuffer;
        if (idx != null && idx.capacity() >= HEADER_SIZE) {
            int magic = idx.getInt(0);
            if (magic != MAGIC) {
                throw new IllegalStateException("Index file has invalid magic: " + Integer.toHexString(magic));
            }
        }
    }

    private void rebuild() {
        LOGGER.warn("{} is corrupt. Discarding.", this);
        pendingWrites.clear();
        flushedWrites.clear();
        accessedKeys.clear();
        closeQuietly();
        open = false;
        deleteFiles();
        try {
            doOpen();
        } catch (Exception e) {
            LOGGER.warn("{} couldn't be rebuilt. Closing.", this);
        }
    }

    private void closeDataChannel() {
        FileChannel ch = dataChannel;
        dataChannel = null;
        if (ch != null) {
            try {
                ch.close();
            } catch (Exception e) {
                LOGGER.debug("Failed to close data channel", e);
            }
        }
    }

    private void closeQuietly() {
        closeDataChannel();
        indexBuffer = null;
        bucketCount = 0;
        bucketMask = 0;
        pendingWrites.clear();
        flushedWrites.clear();
        accessedKeys.clear();
        dirty = false;
    }

    private void deleteFiles() {
        dataFile.delete();
        indexFile.delete();
        new File(dataFile.getPath() + ".new").delete();
        new File(indexFile.getPath() + ".new").delete();
    }

    private static class SerializationBuffer {
        final FastByteArrayOutputStream baos = new FastByteArrayOutputStream(KRYO_BUFFER_SIZE);
        final KryoBackedEncoder encoder = new KryoBackedEncoder(baos, KRYO_BUFFER_SIZE);
        final ResettableByteArrayInputStream decoderInput = new ResettableByteArrayInputStream();
        final KryoBackedDecoder decoder = new KryoBackedDecoder(decoderInput, KRYO_BUFFER_SIZE);
        // Reusable buffer for reading values from the data file — avoids allocation per get()
        byte[] valueReadBuffer = new byte[KRYO_BUFFER_SIZE];

        byte[] ensureReadCapacity(int size) {
            if (valueReadBuffer.length < size) {
                valueReadBuffer = new byte[Math.max(valueReadBuffer.length * 2, size)];
            }
            return valueReadBuffer;
        }
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

        void setData(byte[] data, int length) {
            this.buf = data;
            this.pos = 0;
            this.count = length;
            this.mark = 0;
        }
    }
}
