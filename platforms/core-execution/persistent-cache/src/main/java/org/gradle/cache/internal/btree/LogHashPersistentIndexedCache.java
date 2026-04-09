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
 * <p>Fully on-disk index: both reads and writes probe the index file via positional I/O (pread/pwrite).
 * No in-memory index — memory usage is O(1) regardless of cache size. The OS page cache provides
 * near-memory performance for hot buckets without explicit cache management.</p>
 *
 * <p>{@code put()} appends to the data file and pwrites the updated bucket to the index file.
 * {@code get()} preads a chunk of buckets from the index file, then preads the value from the data file.
 * {@code flush()} updates the index header and fsyncs for crash-recovery consistency.</p>
 *
 * <h3>File layout</h3>
 * <pre>
 *   &lt;base&gt;.dat  — append-only data log: [keyHash:8][valueLen:4][lastWrite:8][type:1][value:N] per entry
 *   &lt;base&gt;.idx  — hash-table index:     header(16) + N × [keyHash:8][offset:8]
 *   Header: [magic:4][bucketCount:4][entryCount:4][occupiedSlots:4]
 * </pre>
 */
@NullMarked
public class LogHashPersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogHashPersistentIndexedCache.class);

    static final int MAGIC = 0x47484133; // "GHA3" — v3 format (type byte in header)
    private static final int HEADER_SIZE = 16;
    private static final int BUCKET_SIZE = 16;
    private static final byte ENTRY_PUT = 1;
    private static final int KRYO_BUFFER_SIZE = 512;
    private static final int WRITE_BUFFER_SIZE = 32 * 1024;
    private static final int ENTRY_HEADER_SIZE = 21; // keyHash:8 + valueLen:4 + lastWrite:8 + type:1
    private static final int SPECULATIVE_READ_SIZE = 512;
    private static final int PROBE_CHUNK_BUCKETS = 8;
    private static final int MIN_BUCKET_COUNT = 8192; // 128 KB initial index, covers ~6000 entries at 75% load

    // Return values for probeAndUpsert
    private static final int UPSERT_NEW = 0;
    private static final int UPSERT_UPDATED = 1;
    private static final int UPSERT_REVIVED = 2;

    private final File baseFile;
    private final File dataFile;
    private final File indexFile;
    private final Serializer<V> valueSerializer;
    private final FastKeyHasher<K> keyHasher;

    // Index metadata — volatile for visibility to concurrent reader threads.
    // Writer updates bucketCount/bucketMask BEFORE indexChannel so that a reader
    // seeing the new channel (volatile read) is guaranteed to see the new counts.
    private volatile int bucketCount;
    private volatile int bucketMask;
    private int entryCount;
    private int occupiedSlots; // live + tombstoned — used for growth trigger

    // Data file: FileChannel for both writes (append via position) and concurrent reads (pread)
    private volatile FileChannel dataChannel;
    private long appendPosition;

    // Index file: FileChannel for on-disk probing (pread) and write-through (pwrite).
    // Written LAST during growth to establish happens-before for concurrent readers.
    private volatile FileChannel indexChannel;

    // Write stream: plain fields, only accessed by single worker thread.
    private final PositionalOutputStream writeStream = new PositionalOutputStream();
    private final KryoBackedEncoder writeEncoder = new KryoBackedEncoder(writeStream, WRITE_BUFFER_SIZE);
    private final byte[] entryHeader = new byte[ENTRY_HEADER_SIZE];
    private final ByteBuffer entryHeaderBuffer = ByteBuffer.wrap(entryHeader);

    // Writer-side probe/write buffers (single-threaded, not ThreadLocal)
    private final ByteBuffer writerProbeBuf = ByteBuffer.allocate(PROBE_CHUNK_BUCKETS * BUCKET_SIZE);
    private final ByteBuffer writerBucketBuf = ByteBuffer.allocate(BUCKET_SIZE);
    private final ByteBuffer headerFlushBuf = ByteBuffer.allocate(8);

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
        if (indexChannel == null) {
            createEmptyIndex();
        }
        dataChannel = FileChannel.open(dataFile.toPath(),
            StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        appendPosition = dataChannel.size();
        open = true;
    }

    /**
     * Loads index metadata from the header (16 bytes). Does NOT load the full index into memory.
     */
    private void loadIndex() throws IOException {
        if (indexFile.exists() && indexFile.length() >= HEADER_SIZE) {
            FileChannel ch = FileChannel.open(indexFile.toPath(),
                StandardOpenOption.READ, StandardOpenOption.WRITE);
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            ch.read(header, 0);
            header.flip();

            int magic = header.getInt();
            if (magic != MAGIC) {
                LOGGER.warn("{} has invalid magic. Starting fresh.", this);
                ch.close();
                initEmpty();
                return;
            }
            int bc = header.getInt();
            if (bc <= 0 || (bc & (bc - 1)) != 0) {
                LOGGER.warn("{} has invalid bucket count. Starting fresh.", this);
                ch.close();
                initEmpty();
                return;
            }
            int expectedSize = HEADER_SIZE + bc * BUCKET_SIZE;
            if (ch.size() < expectedSize) {
                LOGGER.warn("{} index file truncated. Starting fresh.", this);
                ch.close();
                initEmpty();
                return;
            }

            bucketCount = bc;
            bucketMask = bc - 1;
            entryCount = header.getInt();
            occupiedSlots = header.getInt();
            indexChannel = ch;
        } else {
            initEmpty();
        }
    }

    /** Creates an empty index file with MIN_BUCKET_COUNT zeroed buckets and opens the channel. */
    private void createEmptyIndex() throws IOException {
        int bc = MIN_BUCKET_COUNT;
        int size = HEADER_SIZE + bc * BUCKET_SIZE;
        byte[] bytes = new byte[size];
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.putInt(0, MAGIC);
        buf.putInt(4, bc);
        buf.putInt(8, 0); // entryCount
        buf.putInt(12, 0); // occupiedSlots
        Files.write(indexFile.toPath(), bytes);

        bucketCount = bc;
        bucketMask = bc - 1;
        entryCount = 0;
        occupiedSlots = 0;
        indexChannel = FileChannel.open(indexFile.toPath(),
            StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    private void initEmpty() {
        bucketCount = 0;
        bucketMask = 0;
        entryCount = 0;
        occupiedSlots = 0;
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
            // Read indexChannel FIRST (volatile) to establish happens-before for bucketCount/bucketMask
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
            // then seek back to write the header.
            writeStream.reset(dataChannel, offset + ENTRY_HEADER_SIZE);
            writeEncoder.flush();
            valueSerializer.write(writeEncoder, value);
            writeEncoder.flush();
            int valueLen = writeStream.getBytesWritten();

            // Write header (includes type byte) via seek-back — one pwrite for the whole header
            long now = System.currentTimeMillis();
            putLong(entryHeader, 0, hash);
            putInt(entryHeader, 8, valueLen);
            putLong(entryHeader, 12, now);
            entryHeader[20] = ENTRY_PUT;
            entryHeaderBuffer.clear();
            dataChannel.write(entryHeaderBuffer, offset);

            appendPosition = offset + ENTRY_HEADER_SIZE + valueLen;

            // Optimistic growth check before combined probe.
            // If the entry already exists (update), growth was slightly premature — harmless.
            if ((occupiedSlots + 1) * 4 > bucketCount * 3) {
                growIndex();
            }

            // Single-pass probe: find the slot, determine new/update/revived, and write
            int result = probeAndUpsert(hash, offset);
            if (result == UPSERT_NEW) {
                entryCount++;
                occupiedSlots++;
            } else if (result == UPSERT_REVIVED) {
                entryCount++;
                // occupiedSlots unchanged — tombstone already counted as occupied
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
            if (indexChannel != null && probeAndRemove(hash)) {
                entryCount--;
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
            flushHeader();
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

    // ---- On-disk probing (writer-side, single-threaded) ----

    /**
     * Combined probe + insert/update in a single pass. Probes the on-disk index to find the
     * target bucket (empty or same-hash), writes the bucket, and returns the result type.
     *
     * @return UPSERT_NEW if inserted into empty slot, UPSERT_UPDATED if overwrote live entry,
     *         UPSERT_REVIVED if overwrote tombstoned entry
     */
    private int probeAndUpsert(long hash, long offset) throws IOException {
        FileChannel ch = indexChannel;
        int bc = bucketCount;
        int mask = bucketMask;
        int startBucket = (int) (hash & mask);

        int probesCompleted = 0;
        while (probesCompleted < bc) {
            int currentBucket = (startBucket + probesCompleted) & mask;
            int bucketsUntilEnd = bc - currentBucket;
            int bucketsToRead = Math.min(PROBE_CHUNK_BUCKETS, Math.min(bc - probesCompleted, bucketsUntilEnd));

            long filePos = HEADER_SIZE + (long) currentBucket * BUCKET_SIZE;
            writerProbeBuf.clear();
            writerProbeBuf.limit(bucketsToRead * BUCKET_SIZE);
            ch.read(writerProbeBuf, filePos);

            for (int i = 0; i < bucketsToRead; i++) {
                int bufPos = i * BUCKET_SIZE;
                long stored = writerProbeBuf.getLong(bufPos);
                if (stored == 0) {
                    writeBucket(ch, filePos + (long) i * BUCKET_SIZE, hash, offset);
                    return UPSERT_NEW;
                }
                if (stored == hash) {
                    long existingOffset = writerProbeBuf.getLong(bufPos + 8);
                    writeBucket(ch, filePos + (long) i * BUCKET_SIZE, hash, offset);
                    return existingOffset < 0 ? UPSERT_REVIVED : UPSERT_UPDATED;
                }
            }

            probesCompleted += bucketsToRead;
        }
        return UPSERT_NEW; // shouldn't reach here with proper growth
    }

    /**
     * Combined probe + tombstone in a single pass. Finds the hash and tombstones it if live.
     *
     * @return true if the entry was found live and tombstoned
     */
    private boolean probeAndRemove(long hash) throws IOException {
        FileChannel ch = indexChannel;
        if (ch == null) {
            return false;
        }
        int bc = bucketCount;
        int mask = bucketMask;
        int startBucket = (int) (hash & mask);

        int probesCompleted = 0;
        while (probesCompleted < bc) {
            int currentBucket = (startBucket + probesCompleted) & mask;
            int bucketsUntilEnd = bc - currentBucket;
            int bucketsToRead = Math.min(PROBE_CHUNK_BUCKETS, Math.min(bc - probesCompleted, bucketsUntilEnd));

            long filePos = HEADER_SIZE + (long) currentBucket * BUCKET_SIZE;
            writerProbeBuf.clear();
            writerProbeBuf.limit(bucketsToRead * BUCKET_SIZE);
            ch.read(writerProbeBuf, filePos);

            for (int i = 0; i < bucketsToRead; i++) {
                int bufPos = i * BUCKET_SIZE;
                long stored = writerProbeBuf.getLong(bufPos);
                if (stored == 0) {
                    return false;
                }
                if (stored == hash) {
                    long existingOffset = writerProbeBuf.getLong(bufPos + 8);
                    if (existingOffset < 0) {
                        return false; // already tombstoned
                    }
                    writeBucket(ch, filePos + (long) i * BUCKET_SIZE, hash, -1L);
                    return true;
                }
            }

            probesCompleted += bucketsToRead;
        }
        return false;
    }

    /** Writes a single 16-byte bucket to the index file at the given position. */
    private void writeBucket(FileChannel ch, long pos, long hash, long offset) throws IOException {
        writerBucketBuf.clear();
        writerBucketBuf.putLong(hash);
        writerBucketBuf.putLong(offset);
        writerBucketBuf.flip();
        ch.write(writerBucketBuf, pos);
    }

    // ---- Index growth and flush ----

    /**
     * Grows the hash table. Reads old entries from the index file into a temporary buffer,
     * rehashes into a new temporary buffer, writes the new index to disk, and reopens the channel.
     * Both buffers are temporary and immediately eligible for GC after this method returns.
     */
    private void growIndex() throws IOException {
        int newBucketCount = nextPowerOfTwo(Math.max(MIN_BUCKET_COUNT, entryCount * 4 / 3 + 1));
        int newBucketMask = newBucketCount - 1;

        // Temporary buffer for the new index
        byte[] newBytes = new byte[HEADER_SIZE + newBucketCount * BUCKET_SIZE];
        ByteBuffer newBuf = ByteBuffer.wrap(newBytes);
        newBuf.putInt(0, MAGIC);
        newBuf.putInt(4, newBucketCount);

        // Rehash: read old entries from disk into temporary buffer
        FileChannel oldCh = indexChannel;
        int rehashed = 0;
        if (oldCh != null && bucketCount > 0) {
            int oldSize = HEADER_SIZE + bucketCount * BUCKET_SIZE;
            ByteBuffer oldBuf = ByteBuffer.allocate(oldSize);
            oldCh.read(oldBuf, 0);
            oldBuf.flip();

            for (int i = 0; i < bucketCount; i++) {
                int pos = HEADER_SIZE + i * BUCKET_SIZE;
                long storedHash = oldBuf.getLong(pos);
                if (storedHash != 0) {
                    long offset = oldBuf.getLong(pos + 8);
                    if (offset >= 0) { // skip tombstones
                        insertIntoBuf(newBuf, newBucketCount, newBucketMask, storedHash, offset);
                        rehashed++;
                    }
                }
            }
            // oldBuf goes out of scope — eligible for GC
        }

        newBuf.putInt(8, rehashed);
        newBuf.putInt(12, rehashed); // occupiedSlots = entryCount after rehash (no tombstones)

        entryCount = rehashed;
        occupiedSlots = rehashed;

        // Write new index to disk via temp file + atomic rename
        File newFile = new File(indexFile.getPath() + ".new");
        Files.write(newFile.toPath(), newBytes);
        Files.move(newFile.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        // newBytes/newBuf go out of scope — eligible for GC

        // Update bucketCount/bucketMask BEFORE indexChannel (volatile write ordering)
        bucketCount = newBucketCount;
        bucketMask = newBucketMask;

        // Reopen channel (volatile write — establishes happens-before for readers)
        FileChannel oldChannel = indexChannel;
        indexChannel = FileChannel.open(indexFile.toPath(),
            StandardOpenOption.READ, StandardOpenOption.WRITE);
        if (oldChannel != null) {
            oldChannel.close();
        }

        indexDirty = false;
    }

    /** Inserts a bucket into a temporary in-memory ByteBuffer (used only during growIndex rehash). */
    private static void insertIntoBuf(ByteBuffer buf, int bc, int mask, long hash, long offset) {
        int bucket = (int) (hash & mask);
        for (int probe = 0; probe < bc; probe++) {
            int pos = HEADER_SIZE + ((bucket + probe) & mask) * BUCKET_SIZE;
            long stored = buf.getLong(pos);
            if (stored == 0 || stored == hash) {
                buf.putLong(pos, hash);
                buf.putLong(pos + 8, offset);
                return;
            }
        }
    }

    /**
     * Flushes the index header (entryCount + occupiedSlots) and fsyncs.
     * Individual bucket updates are already on disk via pwrite during put/remove.
     */
    private void flushHeader() throws IOException {
        FileChannel ch = indexChannel;
        if (ch == null) {
            return;
        }
        headerFlushBuf.clear();
        headerFlushBuf.putInt(entryCount);
        headerFlushBuf.putInt(occupiedSlots);
        headerFlushBuf.flip();
        ch.write(headerFlushBuf, 8); // entryCount at offset 8, occupiedSlots at offset 12
        ch.force(false);
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
        rb.specBuf.get();    // skip type byte

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
            long hash = keyHasher.getHashCode(key);
            // Hash 0 is the empty-bucket sentinel — remap to avoid silent data loss
            return hash == 0 ? 1 : hash;
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
        FileChannel ch = indexChannel;
        if (ch != null) {
            try {
                ByteBuffer header = ByteBuffer.allocate(4);
                ch.read(header, 0);
                header.flip();
                int magic = header.getInt();
                if (magic != MAGIC) {
                    throw new IllegalStateException("Index file has invalid magic: " + Integer.toHexString(magic));
                }
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
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
        bucketCount = 0;
        bucketMask = 0;
        entryCount = 0;
        occupiedSlots = 0;
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
