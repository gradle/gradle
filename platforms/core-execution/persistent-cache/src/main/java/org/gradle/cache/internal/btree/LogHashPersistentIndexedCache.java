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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * A persistent indexed cache backed by an append-only data log and a hash-table index file.
 *
 * <h3>Design</h3>
 * <p>Direct write-through: {@code put()} immediately appends to the data file and updates both
 * the in-memory index and the on-disk index via pwrite. {@code get()} probes the on-disk index
 * via positional pread (thread-safe, no synchronization), then reads the value from the data file.
 * {@code flush()} writes the full index to disk for crash-recovery consistency.</p>
 *
 * <h3>File layout</h3>
 * <pre>
 *   &lt;base&gt;.dat  — append-only data log: [keyHash:8][valueLen:4][lastWrite:8][value:N][type:1] per entry
 *   &lt;base&gt;.idx  — hash-table index:     header(16) + N × [keyHash:8][offset:8]
 * </pre>
 */
@NullMarked
public class LogHashPersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogHashPersistentIndexedCache.class);

    static final int MAGIC = 0x47484132; // "GHA2" — v2 format (16-byte buckets, timestamp in data entry)
    private static final int HEADER_SIZE = 16;
    private static final int BUCKET_SIZE = 16;
    private static final byte ENTRY_PUT = 1;
    private static final int KRYO_BUFFER_SIZE = 512;
    private static final int WRITE_BUFFER_SIZE = 32 * 1024;
    private static final int ENTRY_HEADER_SIZE = 20; // keyHash:8 + valueLen:4 + lastWrite:8
    private static final int SPECULATIVE_READ_SIZE = 512;
    private static final int PROBE_CHUNK_BUCKETS = 8;

    private final File baseFile;
    private final File dataFile;
    private final File indexFile;
    private final Serializer<V> valueSerializer;
    private final FastKeyHasher<K> keyHasher;

    // In-memory hash-table index — used by writer thread for fast updates.
    // Concurrent readers use on-disk probing via indexChannel instead.
    private volatile ByteBuffer indexBuffer;
    private volatile int bucketCount;
    private volatile int bucketMask;
    private int entryCount;

    // Data file: FileChannel for both writes (append via position) and concurrent reads (pread)
    private volatile FileChannel dataChannel;
    private long appendPosition;

    // Index file: FileChannel for on-disk probing (pread) and write-through (pwrite)
    private volatile FileChannel indexChannel;

    // Write stream: plain fields, only accessed by single worker thread.
    // Streams serialized values directly to the data file — bounded to WRITE_BUFFER_SIZE
    // regardless of value size (no in-memory buffering of full values).
    private final PositionalOutputStream writeStream = new PositionalOutputStream();
    private final KryoBackedEncoder writeEncoder = new KryoBackedEncoder(writeStream, WRITE_BUFFER_SIZE);
    private final byte[] entryHeader = new byte[ENTRY_HEADER_SIZE];
    private final ByteBuffer entryHeaderBuffer = ByteBuffer.wrap(entryHeader);
    // Reusable buffer for writing a single bucket (16 bytes) to the index file
    private final ByteBuffer bucketWriteBuf = ByteBuffer.allocate(BUCKET_SIZE);

    // Read buffers: ThreadLocal, accessed by concurrent reader threads
    private final ThreadLocal<ReadBuffer> readBuffers = ThreadLocal.withInitial(ReadBuffer::new);

    private volatile boolean open;
    private boolean indexDirty;

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
        dataChannel = FileChannel.open(dataFile.toPath(),
            StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        appendPosition = dataChannel.size();
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
            entryCount = buf.getInt(8);
            indexChannel = FileChannel.open(indexFile.toPath(),
                StandardOpenOption.READ, StandardOpenOption.WRITE);
        } else {
            initEmpty();
        }
    }

    private void initEmpty() {
        bucketCount = 0;
        bucketMask = 0;
        entryCount = 0;
        indexBuffer = null;
        indexChannel = null;
    }

    @Override
    public String toString() {
        return "cache " + baseFile.getName() + " (" + baseFile + ")";
    }

    @Override
    public boolean supportsConcurrentReads() {
        return true;
    }

    /**
     * Reads an entry by probing the on-disk index via positional pread, then reading the value
     * from the data file. Thread-safe: both reads use positional I/O that does not modify channel state.
     * Multi-bucket reads (128 bytes = 8 buckets) cover the typical probe chain in a single syscall.
     */
    @Nullable
    @Override
    public V get(K key) {
        ensureOpen();
        try {
            long hash = hashKey(key);
            FileChannel idxCh = indexChannel;
            FileChannel dataCh = dataChannel;
            int bc = bucketCount;
            if (idxCh != null && dataCh != null && bc > 0) {
                int mask = bucketMask;
                int startBucket = (int) (hash & mask);
                ReadBuffer rb = readBuffers.get();

                int probesCompleted = 0;
                while (probesCompleted < bc) {
                    int currentBucket = (startBucket + probesCompleted) & mask;
                    // Clamp chunk to not cross the end-of-table boundary (handle wrap-around)
                    int bucketsUntilEnd = bc - currentBucket;
                    int bucketsToRead = Math.min(PROBE_CHUNK_BUCKETS, Math.min(bc - probesCompleted, bucketsUntilEnd));

                    long filePos = HEADER_SIZE + (long) currentBucket * BUCKET_SIZE;
                    rb.probeBuf.clear();
                    rb.probeBuf.limit(bucketsToRead * BUCKET_SIZE);
                    int bytesRead = idxCh.read(rb.probeBuf, filePos);
                    if (bytesRead < bucketsToRead * BUCKET_SIZE) {
                        return null;
                    }

                    for (int i = 0; i < bucketsToRead; i++) {
                        int bufPos = i * BUCKET_SIZE;
                        long storedHash = rb.probeBuf.getLong(bufPos);
                        if (storedHash == 0) {
                            return null;
                        }
                        if (storedHash == hash) {
                            long offset = rb.probeBuf.getLong(bufPos + 8);
                            if (offset < 0) {
                                return null; // tombstoned
                            }
                            return readValueFromChannel(dataCh, offset);
                        }
                    }

                    probesCompleted += bucketsToRead;
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
            long offset = appendPosition;

            // Seek-back streaming: stream the serialized value directly to the data file,
            // then seek back to write the header. Memory bounded to WRITE_BUFFER_SIZE (32KB)
            // regardless of value size.
            //
            // Step 1: Skip header (20 bytes), stream value directly to file
            writeStream.reset(dataChannel, offset + ENTRY_HEADER_SIZE);
            writeEncoder.flush(); // clear encoder's internal buffer
            valueSerializer.write(writeEncoder, value);
            writeEncoder.flush(); // flush remaining data to file
            int valueLen = writeStream.getBytesWritten();

            // Step 2: Write type byte after value
            writeStream.write(ENTRY_PUT);

            // Step 3: Seek back and write real header (including timestamp)
            long now = System.currentTimeMillis();
            putLong(entryHeader, 0, hash);
            putInt(entryHeader, 8, valueLen);
            putLong(entryHeader, 12, now);
            entryHeaderBuffer.clear();
            dataChannel.write(entryHeaderBuffer, offset);

            // Step 4: Advance append position
            appendPosition = offset + ENTRY_HEADER_SIZE + valueLen + 1;

            // Update in-memory index
            boolean isNew = !probeIndexContains(hash);
            if (isNew) {
                entryCount++;
                if (indexBuffer == null || entryCount * 4 > bucketCount * 3) {
                    growIndex();
                }
            }
            int bucketPos = insertIntoBuckets(indexBuffer, bucketCount, bucketMask, hash, offset);
            if (bucketPos >= 0) {
                writeBucketToDisk(bucketPos);
            }
            indexDirty = true;
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
            if (indexBuffer != null && probeIndexContains(hash)) {
                int bucketPos = tombstoneInBuckets(indexBuffer, bucketCount, bucketMask, hash);
                if (bucketPos >= 0) {
                    writeBucketToDisk(bucketPos);
                }
                indexDirty = true;
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(
                new IOException(String.format("Could not remove entry '%s' from %s.", key, this), e), true);
        }
    }

    @Override
    public void flush() {
        if (!open || !indexDirty) {
            return;
        }
        try {
            writeIndexToDisk();
            indexDirty = false;
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
        } catch (Exception e) {
            LOGGER.warn("Error flushing index for {}.", this, e);
        } finally {
            closeQuietly();
            open = false;
        }
    }

    /** Grows the hash table to accommodate more entries, then writes it to disk. */
    private void growIndex() throws IOException {
        int newBucketCount = nextPowerOfTwo(Math.max(16, entryCount * 4 / 3 + 1));
        int newBucketMask = newBucketCount - 1;

        byte[] newBytes = new byte[HEADER_SIZE + newBucketCount * BUCKET_SIZE];
        ByteBuffer newBuf = ByteBuffer.wrap(newBytes);
        newBuf.putInt(0, MAGIC);
        newBuf.putInt(4, newBucketCount);

        // Rehash existing entries
        ByteBuffer oldIdx = indexBuffer;
        int rehashed = 0;
        if (oldIdx != null && bucketCount > 0) {
            for (int i = 0; i < bucketCount; i++) {
                int pos = HEADER_SIZE + i * BUCKET_SIZE;
                long storedHash = oldIdx.getLong(pos);
                if (storedHash != 0) {
                    long offset = oldIdx.getLong(pos + 8);
                    if (offset >= 0) { // skip tombstones
                        insertIntoBuckets(newBuf, newBucketCount, newBucketMask, storedHash, offset);
                        rehashed++;
                    }
                }
            }
        }

        newBuf.putInt(8, rehashed);
        entryCount = rehashed;
        indexBuffer = newBuf;
        bucketCount = newBucketCount;
        bucketMask = newBucketMask;

        // Write new index to disk so on-disk probing and write-through work immediately
        writeIndexToDisk();
        indexDirty = false;
    }

    private boolean probeIndexContains(long hash) {
        ByteBuffer idx = indexBuffer;
        if (idx == null || bucketCount == 0) {
            return false;
        }
        int bucket = (int) (hash & bucketMask);
        for (int probe = 0; probe < bucketCount; probe++) {
            int pos = HEADER_SIZE + ((bucket + probe) & bucketMask) * BUCKET_SIZE;
            long stored = idx.getLong(pos);
            if (stored == 0) {
                return false;
            }
            if (stored == hash) {
                return idx.getLong(pos + 8) >= 0; // not tombstoned
            }
        }
        return false;
    }

    private static int tombstoneInBuckets(ByteBuffer idx, int bc, int mask, long hash) {
        int bucket = (int) (hash & mask);
        for (int probe = 0; probe < bc; probe++) {
            int pos = HEADER_SIZE + ((bucket + probe) & mask) * BUCKET_SIZE;
            long stored = idx.getLong(pos);
            if (stored == 0) {
                return -1;
            }
            if (stored == hash) {
                idx.putLong(pos + 8, -1L);
                return pos;
            }
        }
        return -1;
    }

    /**
     * Inserts or updates a bucket in the hash table.
     * @return the byte position of the written bucket, or -1 if the table is full
     */
    private static int insertIntoBuckets(ByteBuffer idx, int bc, int mask, long hash, long offset) {
        int bucket = (int) (hash & mask);
        for (int probe = 0; probe < bc; probe++) {
            int pos = HEADER_SIZE + ((bucket + probe) & mask) * BUCKET_SIZE;
            long stored = idx.getLong(pos);
            if (stored == 0 || stored == hash) {
                idx.putLong(pos, hash);
                idx.putLong(pos + 8, offset);
                return pos;
            }
        }
        return -1;
    }

    /** Writes a single 16-byte bucket from the in-memory index to the on-disk index file. */
    private void writeBucketToDisk(int bucketPos) throws IOException {
        FileChannel ch = indexChannel;
        if (ch != null) {
            ByteBuffer idx = indexBuffer;
            bucketWriteBuf.clear();
            bucketWriteBuf.putLong(idx.getLong(bucketPos));
            bucketWriteBuf.putLong(idx.getLong(bucketPos + 8));
            bucketWriteBuf.flip();
            ch.write(bucketWriteBuf, bucketPos);
        }
    }

    /**
     * Writes the full index to disk. Uses the open indexChannel if available (in-place write),
     * otherwise creates the file and opens the channel.
     */
    @SuppressWarnings("ByteBufferBackingArray") // indexBuffer is always heap-allocated via wrap()
    private void writeIndexToDisk() throws IOException {
        ByteBuffer idx = indexBuffer;
        if (idx == null) {
            return;
        }
        // Update entry count in the live buffer
        idx.putInt(8, entryCount);

        int size = HEADER_SIZE + bucketCount * BUCKET_SIZE;
        FileChannel ch = indexChannel;
        if (ch != null) {
            // Write in-place via the open channel
            ch.write(ByteBuffer.wrap(idx.array(), 0, size), 0);
            ch.truncate(size);
            ch.force(false);
        } else {
            // First time: create the file via temp + rename, then open channel
            File newIndexFile = new File(indexFile.getPath() + ".new");
            byte[] data = new byte[size];
            System.arraycopy(idx.array(), 0, data, 0, size);
            Files.write(newIndexFile.toPath(), data);
            Files.move(newIndexFile.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            indexChannel = FileChannel.open(indexFile.toPath(),
                StandardOpenOption.READ, StandardOpenOption.WRITE);
        }
    }

    /**
     * Reads and deserializes a value from the data file using positional read (pread).
     * Thread-safe: {@link FileChannel#read(ByteBuffer, long)} does not modify channel position.
     */
    @Nullable
    private V readValueFromChannel(FileChannel ch, long offset) throws IOException {
        ReadBuffer rb = readBuffers.get();
        rb.specBuf.clear();
        int read = ch.read(rb.specBuf, offset);
        if (read < ENTRY_HEADER_SIZE) {
            return null;
        }
        rb.specBuf.flip();
        rb.specBuf.getLong(); // skip keyHash
        int valueLen = rb.specBuf.getInt();
        if (valueLen < 0 || valueLen > 64 * 1024 * 1024) {
            return null;
        }
        rb.specBuf.getLong(); // skip lastWrite timestamp

        byte[] valueBuf = rb.ensureReadCapacity(valueLen);

        int available = rb.specBuf.remaining();
        if (available >= valueLen) {
            rb.specBuf.get(valueBuf, 0, valueLen);
        } else {
            rb.specBuf.get(valueBuf, 0, available);
            ByteBuffer remainder = ByteBuffer.wrap(valueBuf, available, valueLen - available);
            read = ch.read(remainder, offset + ENTRY_HEADER_SIZE + available);
            if (read < valueLen - available) {
                return null;
            }
        }

        rb.decoderInput.setData(valueBuf, valueLen);
        rb.decoder.restart(rb.decoderInput);
        try {
            return valueSerializer.read(rb.decoder);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /** Writes a long in big-endian directly into a byte array. */
    private static void putLong(byte[] buf, int offset, long value) {
        buf[offset]     = (byte) (value >> 56);
        buf[offset + 1] = (byte) (value >> 48);
        buf[offset + 2] = (byte) (value >> 40);
        buf[offset + 3] = (byte) (value >> 32);
        buf[offset + 4] = (byte) (value >> 24);
        buf[offset + 5] = (byte) (value >> 16);
        buf[offset + 6] = (byte) (value >> 8);
        buf[offset + 7] = (byte) value;
    }

    /** Writes an int in big-endian directly into a byte array. */
    private static void putInt(byte[] buf, int offset, int value) {
        buf[offset]     = (byte) (value >> 24);
        buf[offset + 1] = (byte) (value >> 16);
        buf[offset + 2] = (byte) (value >> 8);
        buf[offset + 3] = (byte) value;
    }

    private long hashKey(K key) {
        try {
            return keyHasher.getHashCode(key);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
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
        closeQuietly();
        open = false;
        deleteFiles();
        try {
            doOpen();
        } catch (Exception e) {
            LOGGER.warn("{} couldn't be rebuilt. Closing.", this);
        }
    }

    private void closeQuietly() {
        try {
            FileChannel ch = indexChannel;
            if (ch != null) {
                ch.close();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to close index channel", e);
        }
        try {
            FileChannel ch = dataChannel;
            if (ch != null) {
                ch.close();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to close data channel", e);
        }
        dataChannel = null;
        indexChannel = null;
        indexBuffer = null;
        bucketCount = 0;
        bucketMask = 0;
        entryCount = 0;
        indexDirty = false;
    }

    private void deleteFiles() {
        dataFile.delete();
        indexFile.delete();
        new File(dataFile.getPath() + ".new").delete();
        new File(indexFile.getPath() + ".new").delete();
    }

    /** Per-thread read buffer for concurrent readers. */
    private static class ReadBuffer {
        final ByteBuffer specBuf = ByteBuffer.allocate(SPECULATIVE_READ_SIZE);
        final ByteBuffer probeBuf = ByteBuffer.allocate(PROBE_CHUNK_BUCKETS * BUCKET_SIZE);
        final ResettableByteArrayInputStream decoderInput = new ResettableByteArrayInputStream();
        final KryoBackedDecoder decoder = new KryoBackedDecoder(decoderInput, KRYO_BUFFER_SIZE);
        byte[] valueReadBuffer = new byte[KRYO_BUFFER_SIZE];

        byte[] ensureReadCapacity(int size) {
            if (valueReadBuffer.length < size) {
                valueReadBuffer = new byte[Math.max(valueReadBuffer.length * 2, size)];
            }
            return valueReadBuffer;
        }
    }

    /**
     * An OutputStream that writes to a FileChannel at a given position via positional writes.
     * Reusable across puts by calling {@link #reset(FileChannel, long)}.
     */
    private static class PositionalOutputStream extends OutputStream {
        private final byte[] singleByte = new byte[1];
        private final ByteBuffer singleByteBuf = ByteBuffer.wrap(singleByte);
        // Cached ByteBuffer for bulk writes — reused when the backing array is the same.
        // KryoBackedEncoder reuses its internal buffer, so this almost never reallocates.
        private byte[] cachedArray = singleByte;
        private ByteBuffer cachedBuf = singleByteBuf;
        private FileChannel channel;
        private long position;
        private int bytesWritten;

        void reset(FileChannel channel, long position) {
            this.channel = channel;
            this.position = position;
            this.bytesWritten = 0;
        }

        int getBytesWritten() {
            return bytesWritten;
        }

        @Override
        public void write(int b) throws IOException {
            singleByte[0] = (byte) b;
            singleByteBuf.clear();
            int n = channel.write(singleByteBuf, position);
            position += n;
            bytesWritten += n;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            ByteBuffer buf = cachedBuf;
            if (cachedArray != b) {
                buf = ByteBuffer.wrap(b);
                cachedArray = b;
                cachedBuf = buf;
            }
            buf.limit(off + len).position(off);
            while (buf.hasRemaining()) {
                int n = channel.write(buf, position);
                position += n;
                bytesWritten += n;
            }
        }
    }

    private static class ResettableByteArrayInputStream extends ByteArrayInputStream {
        ResettableByteArrayInputStream() {
            super(new byte[0]);
        }

        void setData(byte[] data, int length) {
            this.buf = data;
            this.pos = 0;
            this.count = length;
            this.mark = 0;
        }
    }
}
