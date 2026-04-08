# LogHashPersistentIndexedCache — Possible Index Improvements

## 1. ~~Move timestamp from index to data entry~~ (IMPLEMENTED)

Each index bucket is 24 bytes: `[hash:8][offset:8][lastAccess:8]`. The `lastAccess`
field is **written but never read** anywhere in the codebase. Rather than simply
dropping it, move it to the data entry as a 2-byte `lastWriteDay` (days since epoch,
day-level precision is sufficient for cleanup of entries older than N days).

**Data entry** (header grows 12 -> 14 bytes):
```
[keyHash:8][valueLen:4][lastWriteDay:2][value:N][type:1]
```

**Index bucket** (pure lookup acceleration, no metadata):
```
[hash:8][offset:8]  = 16 bytes
```

Benefits:
- Shrinks bucket size from 24 to 16 bytes (33% less index memory)
- Improves cache-line utilization: 4 buckets per 64-byte cache line instead of 2.67
- Eliminates `System.currentTimeMillis()` syscall per `put()` — cache the current day
  once per session via `(short)(System.currentTimeMillis() / 86_400_000L)`
- `lastWriteDay` comes for free on `get()` — already covered by the 512-byte
  speculative pread of the data entry header
- Cleanup is a sequential data file scan: check each entry's `lastWriteDay`, rebuild
  the index with only fresh entries. Combines naturally with data file compaction
  (item 5)
- The index becomes a pure acceleration structure (hash -> offset); all metadata lives
  with the data it describes

## 2. ~~Separate control-byte array (Swiss table-style metadata)~~ (NOT NEEDED)

The current probe loop reads 8-byte hashes sequentially. A 1-byte fingerprint array
at the start of the index would dramatically improve probing:

```
Layout:
  [control: 1 byte x bucketCount]    (H2 fingerprint | EMPTY | DELETED)
  [slots:  16 bytes x bucketCount]   (hash:8 + offset:8)
```

- Probing scans 1-byte tags first — many fit per cache line
- Even without SIMD, sequential byte comparisons are very cache-friendly
- Clean EMPTY vs DELETED distinction in the control byte (no hash-is-zero sentinel trick)
- Only read the full 16-byte slot on fingerprint match

This is the core principle behind abseil's `flat_hash_map` and Meta's F14.

**Not needed**: with on-disk probing (item 10, now implemented), the bottleneck is the
pread syscall (~1-5 us), not in-buffer hash comparisons (~10-20 ns). The 128-byte
multi-bucket read already fits in L1 cache. Even for the in-memory case, 8 hash
comparisons in 128 bytes is within a single cache line.

## 3. ~~Fix the `hash == 0` sentinel collision~~ (IMPLEMENTED)

Currently `hash == 0` means "empty bucket". If a key genuinely hashes to 0, it can
never be stored — every lookup treats it as empty. Fix:

```java
long hash = hashKey(key);
if (hash == 0) hash = 1; // remap to non-zero sentinel
```

Extremely rare with 64-bit SipHash, but it is a correctness gap.

## 4. Backward-shift deletion instead of tombstones

Current `remove()` tombstones entries (`offset = -1`, hash stays). Problems:

- Tombstones lengthen probe chains permanently (only cleaned during `growIndex()`)
- `entryCount` is not decremented on remove, so remove-then-re-insert cycles inflate
  it, triggering premature growth
- A remove-heavy workload without inserts never triggers growth, so tombstones
  accumulate forever

**Backward-shift deletion**: when removing entry at position `i`, shift subsequent
entries in the same cluster backward to fill the gap. Probe chains stay compact, no
tombstone tracking needed, `entryCount` can be accurately decremented.

## 5. Data file compaction

The `.dat` file is append-only — updates and deletes leave dead space. For long-lived
caches with frequent updates, the file grows without bound.

Options:
- **Threshold-triggered rewrite**: during `flush()`, if dead-space ratio > 50%, rewrite
  live entries into a new `.dat` file and rebuild the index
- **Generation-based**: after N flushes, compact
- Track live bytes vs total bytes in the index header to cheaply detect bloat

## 6. `entryCount` drift on remove/re-insert

`remove()` does not decrement `entryCount`:

```
put(A)    -> entryCount = 1
remove(A) -> tombstone, entryCount still 1
put(A)    -> probeIndexContains returns false, entryCount goes to 2
```

Only `growIndex()` corrects this by recounting live entries. Fix: properly track live
vs dead counts, or adopt backward-shift deletion (item 4) which makes this trivial.

## 7. Incremental index flush

Every `flush()` rewrites the entire `.idx` file. For a cache with 100K entries
(~1.6 MB index at 16 bytes/bucket), this is fine. At 1M+ entries writing 16+ MB on
every flush becomes expensive.

Options:
- **Dirty-page bitmap**: track which 4KB/64KB pages were modified, only rewrite those
- **mmap**: `MappedByteBuffer`-backed index, let the OS handle dirty page writeback via
  `force()`. Zero-copy load on open (lazy paging). Main caveat: unmapping lifecycle
  in Java < 22

## 8. ~~Eliminate per-put allocations in `PositionalOutputStream`~~ (IMPLEMENTED)

Every `put()` call was creating several short-lived objects in the write path:

| Source                            | Line | Allocation                                      |
|-----------------------------------|------|-------------------------------------------------|
| `writeStream.write(ENTRY_PUT)`    | 228  | `new byte[1]` + `ByteBuffer.wrap()` (2 objects) |
| `writeEncoder.flush()`            | 222, 224 | `ByteBuffer.wrap()` per flush chunk (1-3 objects) |
| `ByteBuffer.wrap(entryHeader)`    | 233  | new HeapByteBuffer (1 object)                   |
| `System.currentTimeMillis()`      | 246  | syscall for `lastAccess` (never read back)      |

That was ~5-7 allocations + 1 useless syscall per `put()`. Across all caches on a
write-heavy build iteration, this created measurable GC pressure.

Implemented fixes:
- `PositionalOutputStream.write(int b)`: reusable `singleByte` array + pre-allocated
  `singleByteBuf` ByteBuffer — zero allocations
- `PositionalOutputStream.write(byte[], off, len)`: cached ByteBuffer reused when the
  backing array is the same (KryoBackedEncoder reuses its internal buffer, so this
  almost never reallocates)
- `entryHeaderBuffer`: pre-allocated ByteBuffer wrapping the `entryHeader` byte array,
  reused with `clear()` before each write

Still open: `System.currentTimeMillis()` syscall (covered by item 1 — drop `lastAccess`)

### Observed performance impact

Measured via `JavaIncrementalExecutionPerformanceTest.assemble for non-abi change with
configuration caching`. The test alternates between apply-change and revert-change
builds. After JVM warmup, the data shows a clear bimodal pattern:

```
Write-heavy direction (apply change):  ~610 ms  (slow)
Read-only direction (revert change):   ~500 ms  (matches baseline)
Baseline (BTree, no LogHash):          ~495 ms  (both directions)
```

The read path is unaffected — the overhead is entirely in write + flush. The ~95 ms
gap vs baseline comes from:
1. Per-put allocations generating GC pressure (this item)
2. Full index rewrite on flush, with 33% wasted on unused `lastAccess` (item 1)

Items 1 and 8 together are the highest-priority changes for closing this gap.

## 9. Merge data and index into a single file

Currently the cache uses two files (`.dat` + `.idx`). Merging into one file saves
a file descriptor, eliminates the atomic-rename dance on flush, and reduces fsyncs
from two to one.

### Option A: Data-first, index-at-end (recommended)

```
[Header: 16 B]          magic, version, dataEndOffset, bucketCount, entryCount
[Data entries (append)]  [hash:8][valueLen:4][value:N] ...
[Index (hash table)]     [hash:8][packed:8] x bucketCount
[Trailer: 8 B]          indexStartOffset
```

- **Open**: read trailer (last 8 bytes) -> seek to index -> load hash table.
- **Put**: append entry after data end, update in-memory index. Same as now.
- **Flush**: write index at dataEnd, write trailer, single `fsync`.
  No temp file creation, no atomic rename.
- **Crash during flush**: trailer still points to old index. Unflushed puts lost
  (same as current behavior). If trailer/index is corrupt, rebuild by scanning the
  data log — strictly better than current discard-everything `rebuild()`.

Flush savings vs current two-file design:
- One fewer `fsync` (the real win — typically 1-10 ms on SSD)
- No file creation + atomic rename for `.idx`
- One fewer file open/close per cache per session

### Option B: Log-only / Bitcask-style (simplest, small caches only)

```
[Data entries only]  [hash:8][valueLen:4][value:N] ...
```

No persistent index at all. On open, sequentially scan the data log to rebuild the
in-memory hash table. For a 1 MB cache on SSD, scan takes < 1 ms.

- **Flush**: just `fsync` the data channel (index is memory-only).
- **Tradeoff**: open time is O(file size). Fine for caches under a few MB; too slow
  for very large caches.

### Option C: Log with periodic checkpoints (best of both)

```
[Header: 16 B]           lastCheckpointOffset
[Log entries]             put/delete entries, interspersed with:
  ...
  [Checkpoint N]          full index snapshot appended at flush
  [Log entries]           entries since last checkpoint
  [Checkpoint N+1]        next flush
```

- **Open**: seek to last checkpoint, load index, replay only entries after it.
- **Flush**: append index snapshot to log.
- **Crash safety**: old checkpoints never overwritten, always recoverable.
- **Tradeoff**: file grows with old checkpoints; needs periodic compaction.

### Recommendation

Option A is the smallest change from the current two-file design and provides the
clearest performance win (one fewer fsync per flush). Options B and C are worth
considering if we also want better crash recovery (rebuild from data scan instead of
discarding everything).

## 10. Bounded index memory for large caches

The entire hash table index is loaded into heap memory. BTreePersistentIndexedCache
uses O(1) memory (Guava cache of ~100 blocks), but LogHash uses O(n):

| Entries  | Bucket count | Index in heap (24 B) | Index in heap (16 B) |
|----------|-------------|----------------------|----------------------|
| 1,000    | 2,048       | 49 KB                | 33 KB                |
| 10,000   | 16,384      | 384 KB               | 256 KB               |
| 100,000  | 131,072     | 3.0 MB               | 2.0 MB               |
| 1,000,000| 2,097,152   | 48 MB                | 32 MB                |
| 10,000,000| 16,777,216 | 384 MB               | 256 MB               |

BTreePersistentIndexedCache files can reach GB sizes. If LogHash is used for the same
caches, the in-memory index could cause OOM or excessive GC pressure.

### Mitigations (from cheapest to most complex)

**Option A: Cap entry count + eviction** — enforce a maximum number of entries and
evict old ones (using `lastWrite` day from item 1). If caches don't genuinely need
millions of live entries, this is the simplest fix and also bounds data file growth.

**Option B: mmap the index** — use `MappedByteBuffer` instead of heap `ByteBuffer`.
The OS pages in only the touched regions; the full index stays in virtual memory, not
heap. A 48 MB index with only hot buckets accessed would have ~few hundred KB resident.
Main caveats: mmap lifecycle in Java < 22 requires `Unsafe` or `Cleaner` hacks, and
**mmap is not safe in container environments** (see note below).

**Option C: On-disk probing with multi-bucket reads** — don't load the index into
memory at all. On each `get()`/`put()`, read a chunk of adjacent buckets in a single
pread:

```java
// Read 128 bytes = 8 buckets (at 16 bytes each) in one pread syscall.
// Covers nearly all probe chains at 75% load factor.
ByteBuffer probeBuf = threadLocalProbeBuf.get();
probeBuf.clear();
indexChannel.read(probeBuf, bucketOffset);
// Probe locally in the buffer — no additional syscalls needed.
```

Total syscalls per `get()`: **2 pread** (1 for index probe, 1 for value read). This
beats BTree's typical case of 4 syscalls (2× seek+read) and matches BTree's best case
of 2 — while also supporting concurrent reads (pread is thread-safe, unlike
RandomAccessFile's seek+read).

The OS page cache keeps hot index pages in memory automatically, so for warm caches
the index pread often hits the page cache (~100-200 ns) rather than going to disk. This
effectively gives in-memory performance for hot data without explicitly managing it.

Memory usage is O(1) regardless of cache size — just the thread-local probe buffer.

**Option D: Hybrid** — load the index into memory for small caches (e.g. < 50K
entries / ~1 MB index), fall back to on-disk probing for larger ones. Best of both
worlds: fast path for the common case, safe path for outliers. However, given that
the OS page cache gives on-disk probing near-memory performance for hot data, the
hybrid may be unnecessary complexity.

### Container / Kubernetes considerations

mmap'd files do NOT reduce real memory usage in containerized environments. Pages that
the OS brings into RAM count toward the container's RSS (Resident Set Size), which is
what Kubernetes uses for OOM killing via cgroup memory accounting. A 384 MB mmap'd
index under memory pressure can trigger OOM kill just as 384 MB of heap would — except
it's harder to reason about because it's outside JVM control (no GC, no `-Xmx` cap).

| Option | JVM heap | Container RSS | K8s safe? |
|--------|----------|---------------|-----------|
| A: Cap + eviction      | Bounded   | Bounded        | Yes |
| B: mmap                | Low       | **Unbounded**  | **No** — can OOM |
| C: On-disk probing     | O(1)      | O(1)           | Yes |
| D: Hybrid (heap/mmap)  | Bounded   | **Unbounded**  | **No** for large caches |

### Syscall comparison

| Implementation              | Syscalls per get() | Memory   | Concurrent reads? |
|-----------------------------|-------------------|----------|--------------------|
| BTree best (root cached)    | 2 (seek+read)     | ~500 KB  | No                 |
| BTree typical (depth 2)     | 4 (2× seek+read)  | ~500 KB  | No                 |
| LogHash in-memory index     | 1 (pread)         | O(n)     | Yes                |
| LogHash on-disk probing     | 2 (pread×2)       | O(1)     | Yes                |

On-disk probing matches BTree's best case on syscalls while beating it on concurrency
and memory predictability. With the OS page cache warming hot pages, the extra pread
for the index is typically served from memory (~100-200 ns) rather than disk.

### Recommendation

**Option C (on-disk probing with multi-bucket reads) is the recommended default.** It
provides O(1) memory, 2 pread syscalls per lookup, concurrent reads, and is safe in
all environments including containers. The OS page cache gives it near-memory
performance for hot data without explicit cache management.

Option A (cap + eviction) is still valuable as a complementary measure to bound data
file growth, but is no longer required to solve the memory problem.

Option B (mmap) is not recommended due to container/K8s RSS concerns and Java
lifecycle complexity.

## Summary

| Improvement                | Effort      | Memory impact              | Throughput impact            |
|----------------------------|-------------|----------------------------|------------------------------|
| ~~Timestamp to data entry~~    | ~~Low~~         | ~~-33% index~~                 | ~~Better probe locality~~ DONE |
| ~~Control-byte array~~         | ~~Medium~~      | ~~+1 byte/bucket~~             | NOT NEEDED with on-disk probing |
| ~~Fix hash==0~~                | ~~Trivial~~     | ~~None~~                       | ~~Correctness fix~~ DONE              |
| Backward-shift deletion    | Medium      | Eliminates tombstone bloat | Shorter probe chains         |
| Data file compaction       | Medium-High | Bounded .dat growth        | Faster reads (less seeking)  |
| Fix entryCount drift       | Low         | None                       | Prevents premature growth    |
| Incremental flush          | High        | None                       | Faster flush for large caches|
| ~~Eliminate per-put allocs~~   | ~~Low~~         | ~~None~~                       | ~~Less GC pressure on writes~~ DONE |
| Merge into single file     | Medium      | None                       | One fewer fsync per flush    |
| ~~Bounded index memory~~       | ~~Medium-High~~ | ~~O(n) -> O(1) for large~~     | ~~Prevents OOM on large caches~~ DONE (on-disk probing) |
