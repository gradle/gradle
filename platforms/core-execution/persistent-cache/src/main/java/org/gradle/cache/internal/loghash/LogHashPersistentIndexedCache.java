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
package org.gradle.cache.internal.loghash;

import org.gradle.cache.internal.btree.PersistentIndexedCache;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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
 * <h3>Writes: in-place when possible (BTree-style)</h3>
 * <p>Each {@code put} serializes the value into a reusable in-memory buffer (with the entry header
 * reserved at the front). The new entry is written in a single positional write. When the key
 * already exists and the new entry fits in the old slot, the write goes to the existing offset
 * and the index is left untouched — same syscall count as BTree's in-place updates. Otherwise
 * the entry is appended to the end of the data log and the bucket is updated to the new offset.
 * The buffer grows to the largest serialized value seen, then is reused.</p>
 *
 * <h3>Torn-write detection</h3>
 * <p>Each data entry ends with a 4-byte tail equal to {@code ENTRY_HEADER_SIZE + valueLen} (a
 * value derivable from the header). Reads verify the tail bytes against the expected value;
 * mismatch indicates a torn write (typical pattern: leading pages on disk, trailing pages
 * stale or zeros after a crash). Detection turns a corrupt entry into a {@code null} result
 * (cache miss) instead of returning malformed data, and the cache self-heals on the next
 * {@code put} for that key. Same scheme as {@code BTree}'s block-tail check.</p>
 *
 * <h3>File layout</h3>
 * <pre>
 *   &lt;base&gt;.dat  — data log: [keyHash:8][valueLen:4][lastWrite:8][type:1][value:N][tail:4] per entry
 *   &lt;base&gt;.idx  — hash-table index: header(16) + N × [keyHash:8][offset:8]
 *   Header: [magic:4][bucketCount:4][entryCount:4][occupiedSlots:4]
 * </pre>
 */
@NullMarked
public class LogHashPersistentIndexedCache<K, V> implements PersistentIndexedCache<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogHashPersistentIndexedCache.class);

    // --- Index file format ---
    static final int MAGIC = 0x47484134; // "GHA4" — bumped when entry tail-count was added
    private static final int HEADER_SIZE = 16;
    private static final int BUCKET_SIZE = 16;
    private static final int MIN_BUCKET_COUNT = 8192; // 128 KB initial index, covers ~6000 entries at 75% load

    // --- Data entry format ---
    private static final int ENTRY_HEADER_SIZE = 21; // keyHash:8 + valueLen:4 + lastWrite:8 + type:1
    private static final int ENTRY_TAIL_SIZE = 4;    // bytesWritten check, BTree-style
    private static final byte ENTRY_PUT = 1;

    // --- I/O tuning ---
    private static final int SPECULATIVE_READ_SIZE = 512;
    private static final int PROBE_CHUNK_BUCKETS = 8;
    private static final int KRYO_BUFFER_SIZE = 512;
    private static final int WRITE_BUFFER_SIZE = 32 * 1024;

    // --- Probe sentinels returned by probeForExisting ---
    private static final long UPSERT_NEW_SLOT = Long.MIN_VALUE; // bucket was empty
    private static final long UPSERT_REVIVED_SLOT = -1L;        // bucket held a tombstone

    // Reusable buffer of zeros for reserving header space at the front of the entry buffer.
    private static final byte[] HEADER_PLACEHOLDER = new byte[ENTRY_HEADER_SIZE];

    // --- File paths and serializers (immutable after construction) ---
    private final File baseFile;
    private final File dataFile;
    private final File indexFile;
    private final Serializer<V> valueSerializer;
    private final FastKeyHasher<K> keyHasher;

    // --- Index state (volatile — visible to concurrent reader threads) ---
    // Writer updates bucketCount/bucketMask BEFORE indexChannel so that a reader
    // seeing the new channel (volatile read) is guaranteed to see the new counts.
    private volatile int bucketCount;
    private volatile int bucketMask;
    private volatile FileChannel indexChannel;

    // --- Index state (writer-only — not volatile) ---
    private int entryCount;
    private int occupiedSlots; // live + tombstoned — used for growth trigger

    // --- Data file state ---
    private volatile FileChannel dataChannel;
    private long appendPosition;

    // --- Writer-side buffers (single-threaded, reusable) ---
    // Holds [header placeholder | serialized value | tail placeholder] for the current put.
    // Grows as needed to fit the largest value seen, then is reused. The cached ByteBuffer
    // wrapper is re-created only when the underlying byte[] grows.
    private final EntryBuffer entryBuffer = new EntryBuffer(WRITE_BUFFER_SIZE);
    private final KryoBackedEncoder entryEncoder = new KryoBackedEncoder(entryBuffer, WRITE_BUFFER_SIZE);
    private byte[] cachedEntryArray = entryBuffer.buffer();
    private ByteBuffer cachedEntryByteBuffer = ByteBuffer.wrap(cachedEntryArray);
    private final ByteBuffer writerProbeBuf = ByteBuffer.allocate(PROBE_CHUNK_BUCKETS * BUCKET_SIZE);
    private final ByteBuffer writerBucketBuf = ByteBuffer.allocate(BUCKET_SIZE);
    private final ByteBuffer headerFlushBuf = ByteBuffer.allocate(8);
    private final ByteBuffer valueLenReadBuf = ByteBuffer.allocate(12); // keyHash:8 + valueLen:4

    // File position of the bucket selected by the most recent probeForExisting() call.
    private long lastBucketPos;

    // --- Reader-side buffers (ThreadLocal for concurrent access) ---
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

    // ═══════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

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

    /** Loads index metadata from the header (16 bytes). Does NOT load the full index into memory. */
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

    // ═══════════════════════════════════════════════════════════════════════
    // Core operations
    // ═══════════════════════════════════════════════════════════════════════

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
                            return readValue(dataCh, offset);
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

            // Serialize value into the reusable buffer first so we know the exact size.
            int valueLen = serializeIntoEntryBuffer(value);
            int newEntrySize = ENTRY_HEADER_SIZE + valueLen + ENTRY_TAIL_SIZE;

            // Probe to find existing entry or empty slot. Sets lastBucketPos.
            long existingOffset = probeForExisting(hash);
            long bucketPos = lastBucketPos;

            // In-place update when an existing live entry's slot can hold the new entry.
            // Same syscall count as BTree's in-place update path. The tail-size term cancels:
            //   newSize <= oldSize  ⇔  HDR + newValueLen + TAIL <= HDR + oldValueLen + TAIL
            //                       ⇔  newValueLen <= oldValueLen
            if (existingOffset >= 0) {
                int oldValueLen = readValueLenAt(existingOffset);
                if (valueLen <= oldValueLen) {
                    fillEntryHeaderAndTail(hash, valueLen);
                    writeEntry(existingOffset, newEntrySize);
                    return; // index unchanged — same offset, same hash
                }
                // Falls through: append at end and repoint the bucket. Old data becomes dead space.
            }

            // Append at end of data log.
            long offset = appendPosition;
            fillEntryHeaderAndTail(hash, valueLen);
            writeEntry(offset, newEntrySize);
            appendPosition = offset + newEntrySize;

            // Optimistic growth check — only grow when we're about to consume a previously-empty
            // slot. Updates and tombstone-revivals reuse existing slots so they don't trigger growth.
            if (existingOffset == UPSERT_NEW_SLOT && (occupiedSlots + 1) * 4 > bucketCount * 3) {
                growIndex();
                // Bucket positions changed — re-probe in the grown index.
                existingOffset = probeForExisting(hash);
                bucketPos = lastBucketPos;
            }

            writeBucket(indexChannel, bucketPos, hash, offset);

            if (existingOffset == UPSERT_NEW_SLOT) {
                entryCount++;
                occupiedSlots++;
            } else if (existingOffset == UPSERT_REVIVED_SLOT) {
                entryCount++;
            }
            // UPSERT_UPDATED (existingOffset >= 0 reaching here): counters unchanged.
            indexDirty = true;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(
                new IOException(String.format("Could not add entry '%s' to %s.", key, this), e), true);
        }
    }

    /**
     * Serializes a value into {@link #entryBuffer}, reserving the first {@link #ENTRY_HEADER_SIZE}
     * bytes for the header and the last {@link #ENTRY_TAIL_SIZE} bytes for the tail integrity
     * check. Both are filled in by {@link #fillEntryHeaderAndTail} after we know {@code valueLen}.
     *
     * @return the number of value bytes (excluding header and tail)
     */
    private int serializeIntoEntryBuffer(V value) throws Exception {
        entryBuffer.reset();
        entryBuffer.write(HEADER_PLACEHOLDER, 0, ENTRY_HEADER_SIZE);
        valueSerializer.write(entryEncoder, value);
        entryEncoder.flush();
        int valueLen = entryBuffer.size() - ENTRY_HEADER_SIZE;
        // Reserve 4 bytes at the end for the tail (HEADER_PLACEHOLDER's first 4 bytes are zero).
        entryBuffer.write(HEADER_PLACEHOLDER, 0, ENTRY_TAIL_SIZE);
        return valueLen;
    }

    /**
     * Writes the entry header at the front of the entry buffer and the integrity tail at the end.
     * Tail value is {@code ENTRY_HEADER_SIZE + valueLen}, derivable from the header — readers
     * recompute the same value from the header and compare against the on-disk bytes.
     */
    private void fillEntryHeaderAndTail(long hash, int valueLen) {
        ByteBuffer eb = entryByteBuffer();
        // clear() sets limit to capacity so subsequent absolute-index writes are in range.
        eb.clear();
        eb.putLong(hash);
        eb.putInt(valueLen);
        eb.putLong(System.currentTimeMillis());
        eb.put(ENTRY_PUT);
        // Write tail at absolute index — doesn't disturb position/limit.
        eb.putInt(ENTRY_HEADER_SIZE + valueLen, ENTRY_HEADER_SIZE + valueLen);
    }

    /** Writes the entry buffer's first {@code totalLen} bytes to the data file at {@code offset}. */
    private void writeEntry(long offset, int totalLen) throws IOException {
        ByteBuffer eb = entryByteBuffer();
        eb.position(0).limit(totalLen);
        while (eb.hasRemaining()) {
            int n = dataChannel.write(eb, offset + eb.position());
            if (n <= 0) {
                throw new IOException("Truncated write at offset " + offset);
            }
        }
    }

    /** Returns a ByteBuffer wrapping the current entry buffer array, refreshed if the array grew. */
    private ByteBuffer entryByteBuffer() {
        byte[] buf = entryBuffer.buffer();
        if (cachedEntryArray != buf) {
            cachedEntryArray = buf;
            cachedEntryByteBuffer = ByteBuffer.wrap(buf);
        }
        return cachedEntryByteBuffer;
    }

    /** Reads just the valueLen field of an existing data entry (12-byte pread). */
    private int readValueLenAt(long offset) throws IOException {
        valueLenReadBuf.clear();
        int n = dataChannel.read(valueLenReadBuf, offset);
        if (n < 12) {
            throw new IOException("Truncated entry header at offset " + offset);
        }
        return valueLenReadBuf.getInt(8);
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

    // ═══════════════════════════════════════════════════════════════════════
    // Index probing (on-disk, via pread/pwrite)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Probes the on-disk index for an existing entry with the given hash. Stashes the file
     * position of the target bucket in {@link #lastBucketPos} so the caller can write the
     * bucket later (or skip the write entirely for in-place updates).
     *
     * @return {@link #UPSERT_NEW_SLOT} (empty slot found), {@link #UPSERT_REVIVED_SLOT} (slot
     *     held a tombstone with this hash), or the existing live entry's offset (>= 0).
     */
    private long probeForExisting(long hash) throws IOException {
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
                    lastBucketPos = filePos + (long) i * BUCKET_SIZE;
                    return UPSERT_NEW_SLOT;
                }
                if (stored == hash) {
                    long existingOffset = writerProbeBuf.getLong(bufPos + 8);
                    lastBucketPos = filePos + (long) i * BUCKET_SIZE;
                    return existingOffset; // >= 0 (live) or -1 (tombstone, == UPSERT_REVIVED_SLOT)
                }
            }

            probesCompleted += bucketsToRead;
        }
        // Should be unreachable: growIndex() keeps load factor below 75%.
        throw new IOException("Index probe exhausted without finding empty slot");
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
                    if (writerProbeBuf.getLong(bufPos + 8) < 0) {
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

    // ═══════════════════════════════════════════════════════════════════════
    // Index management
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Grows the hash table. Reads old entries from the index file into a temporary buffer,
     * rehashes into a new temporary buffer, writes the new index to disk, and reopens the channel.
     * Both buffers are temporary and immediately eligible for GC after this method returns.
     */
    private void growIndex() throws IOException {
        int newBucketCount = nextPowerOfTwo(Math.max(MIN_BUCKET_COUNT, entryCount * 4 / 3 + 1));
        int newBucketMask = newBucketCount - 1;

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
                        rehashInsert(newBuf, newBucketCount, newBucketMask, storedHash, offset);
                        rehashed++;
                    }
                }
            }
        }

        newBuf.putInt(8, rehashed);
        newBuf.putInt(12, rehashed); // occupiedSlots = entryCount after rehash (no tombstones)

        entryCount = rehashed;
        occupiedSlots = rehashed;

        // Write new index to disk via temp file + atomic rename
        File newFile = new File(indexFile.getPath() + ".new");
        Files.write(newFile.toPath(), newBytes);
        Files.move(newFile.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

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

    /** Inserts a bucket into a temporary in-memory ByteBuffer during growIndex rehash. */
    private static void rehashInsert(ByteBuffer buf, int bc, int mask, long hash, long offset) {
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

    /** Updates entryCount + occupiedSlots in the index header and fsyncs. */
    private void flushHeader() throws IOException {
        FileChannel ch = indexChannel;
        if (ch == null) {
            return;
        }
        headerFlushBuf.clear();
        headerFlushBuf.putInt(entryCount);
        headerFlushBuf.putInt(occupiedSlots);
        headerFlushBuf.flip();
        ch.write(headerFlushBuf, 8);
        ch.force(false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data file I/O
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Reads and deserializes a value from the data file using positional read (pread).
     * Thread-safe: {@link FileChannel#read(ByteBuffer, long)} does not modify channel position.
     *
     * <p>Per-entry corruption (truncated read, invalid {@code valueLen}, tail mismatch,
     * deserializer failure) returns {@code null} so the cache reports a miss and self-heals on
     * the next {@code put} for that key. {@link IOException} from the underlying channel still
     * propagates — those indicate structural / file-system level problems that the caller's
     * {@code rebuild()} path is meant for.</p>
     */
    @Nullable
    private V readValue(FileChannel ch, long offset) throws IOException {
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

        // Read value bytes plus the 4-byte tail into a single buffer.
        int needed = valueLen + ENTRY_TAIL_SIZE;
        byte[] valueBuf = rb.ensureReadCapacity(needed);

        int available = rb.specBuf.remaining();
        if (available >= needed) {
            rb.specBuf.get(valueBuf, 0, needed);
        } else {
            rb.specBuf.get(valueBuf, 0, available);
            ByteBuffer remainder = ByteBuffer.wrap(valueBuf, available, needed - available);
            int extra = ch.read(remainder, offset + ENTRY_HEADER_SIZE + available);
            if (extra < needed - available) {
                return null;
            }
        }

        // Verify tail. Bytes [valueLen, valueLen + 4) of valueBuf hold the on-disk tail.
        int tail = ByteBuffer.wrap(valueBuf, valueLen, ENTRY_TAIL_SIZE).getInt();
        int expectedTail = ENTRY_HEADER_SIZE + valueLen;
        if (tail != expectedTail) {
            LOGGER.warn("{} entry at offset {} has bad tail (expected {}, got {}) — torn write? Treating as miss.",
                this, offset, expectedTail, tail);
            return null;
        }

        rb.decoderInput.setData(valueBuf, valueLen);
        rb.decoder.restart(rb.decoderInput);
        try {
            return valueSerializer.read(rb.decoder);
        } catch (Exception e) {
            // Per-entry deserialization failure — likely a torn write whose tail happened to
            // match (rare), or a serializer bug. Either way, treat as a cache miss; the next
            // put for this key will overwrite the broken bucket.
            LOGGER.warn("{} entry at offset {} failed to deserialize — treating as miss.", this, offset, e);
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Utilities
    // ═══════════════════════════════════════════════════════════════════════

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
    public String toString() {
        return "cache " + baseFile.getName() + " (" + baseFile + ")";
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean supportsConcurrentReads() {
        return true;
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
     * A {@link ByteArrayOutputStream} subclass that exposes its backing array so the cache can
     * write the buffered entry to the data file via positional I/O without an extra copy.
     */
    private static final class EntryBuffer extends ByteArrayOutputStream {
        EntryBuffer(int initialCapacity) {
            super(initialCapacity);
        }

        byte[] buffer() {
            return buf;
        }
    }
}
