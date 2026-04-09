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

## 6. ~~`entryCount` drift on remove/re-insert~~ (IMPLEMENTED)

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

## 10. ~~Bounded index memory for large caches~~ (IMPLEMENTED — on-disk probing)

Currently implemented: fully on-disk probing with multi-bucket pread. O(1) memory
regardless of cache size. See item 11 for observed regression and mitigation.

### Observed cache sizes (gradle/gradle project, BTree .bin files = index + data)

**Global caches (~/.gradle/caches, per Gradle version):**

| Cache                | Typical size  | Max observed  |
|----------------------|---------------|---------------|
| fileHashes           | 8-17 MB       | 210 MB        |
| classAnalysis        | 66-198 MB     | 198 MB        |
| jarAnalysis          | —             | 45 MB         |
| file-access journal  | —             | 434 MB        |
| executionHistory     | —             | 10 MB         |
| metadata/kotlin-dsl  | < 1 KB        | < 1 KB        |

**Project-level caches (.gradle/ in the gradle/gradle repo):**

| Cache                | Typical size  | Max observed  |
|----------------------|---------------|---------------|
| executionHistory     | 25-342 MB     | **742 MB**    |
| modelsideeffects (CC)| 113-136 MB    | 136 MB        |
| fileHashes           | 1.7-17 MB     | 17 MB         |

Note: larger projects than gradle/gradle exist and may produce bigger caches.

**Estimated entry counts and LogHash index sizes (16 bytes/bucket):**

| Cache (BTree size)          | Est. entries | LogHash index size |
|-----------------------------|-------------|--------------------|
| metadata (< 1 KB)          | < 10         | 256 B              |
| fileHashes (17 MB)          | ~50K-200K   | 1-4 MB             |
| executionHistory (742 MB)   | ~10K-100K   | 256 KB - 2 MB      |
| classAnalysis (198 MB)      | ~50K-500K   | 1-8 MB             |
| fileHashes (210 MB, global) | ~500K-2M    | 8-32 MB            |
| file-access journal (434 MB)| Could be M+ | 16-64 MB+          |

The size distribution is bimodal: most cache types (metadata, kotlin-dsl, groovy-dsl)
are tiny (< 1 KB), but a few (fileHashes, classAnalysis, executionHistory) can reach
hundreds of MB with potentially millions of entries.

### Container / Kubernetes considerations

mmap'd files do NOT reduce real memory usage in containerized environments. Pages that
the OS brings into RAM count toward the container's RSS (Resident Set Size), which is
what Kubernetes uses for OOM killing via cgroup memory accounting. A 384 MB mmap'd
index under memory pressure can trigger OOM kill just as 384 MB of heap would — except
it's harder to reason about because it's outside JVM control (no GC, no `-Xmx` cap).

### Syscall comparison

| Implementation              | Syscalls per get() | Memory   | Concurrent reads? |
|-----------------------------|-------------------|----------|--------------------|
| BTree best (root cached)    | 2 (seek+read)     | ~500 KB  | No                 |
| BTree typical (depth 2)     | 4 (2× seek+read)  | ~500 KB  | No                 |
| LogHash in-memory index     | 1 (pread)         | O(n)     | Yes                |
| LogHash on-disk probing     | 2 (pread×2)       | O(1)     | Yes                |

## 11. Hybrid in-memory/on-disk index + minimum initial index size

### Problem

Pure on-disk probing (item 10) adds syscall overhead for small caches where the full
index easily fits in memory. Observed regression: +26 ms (3.67%) for "assemble storing
configuration cache state with hot daemon | smallJavaMultiProject".

Each `put()` now does 2 extra preads compared to the in-memory approach (one for
`probeIndexForHash`, one for `probeAndInsert`). For a write-heavy workload with ~500-1000
puts, that's 1000-2000 extra syscalls × ~1-5 us = ~1-10 ms. Combined with `growIndex()`
file creation overhead, this accounts for the regression.

### Fix A: Hybrid in-memory/on-disk

Load the index into memory when it is small (below a threshold), use on-disk probing
when it exceeds the threshold:

```java
private static final int INDEX_MEMORY_THRESHOLD = 1024 * 1024; // 1 MB (~60K entries)

private void loadIndex() {
    // ... validate header ...
    if (ch.size() <= INDEX_MEMORY_THRESHOLD) {
        // Small cache: load index into memory (0 extra syscalls on get/put)
        indexBuffer = loadFullIndex(ch);
    }
    // else: large cache, indexBuffer stays null -> on-disk probing
}
```

`get()`/`put()` check `indexBuffer != null` — if available, use in-memory probing
(same performance as original); otherwise use on-disk probing (O(1) memory).

This restores original performance for small caches while keeping O(1) memory for the
caches that actually need it (fileHashes with millions of entries, classAnalysis, etc.).

| Cache size | Approach | Memory | Syscalls per get() |
|------------|----------|--------|--------------------|
| < 1 MB index (~60K entries) | In-memory | O(n) but bounded | 1 (pread for value only) |
| > 1 MB index | On-disk probing | O(1) | 2 (pread index + pread value) |

### Fix B: Larger minimum initial index size

Currently `growIndex()` starts with `Math.max(16, ...)` buckets. For on-disk mode,
each growth requires creating a new file + rename + reopen channel. With 16 initial
buckets, a cache that stabilizes at 1000 entries goes through 8 growth cycles:

```
16 -> 32 -> 64 -> 128 -> 256 -> 512 -> 1024 -> 2048 (8 growths)
```

With `Math.max(8192, ...)` (128 KB file, covers ~6000 entries at 75% load):

```
8192 (1 growth — the initial creation)
```

Most Gradle caches never need to grow again after the initial creation. The 128 KB
initial file cost is negligible (a build with 20 caches uses ~2.5 MB total).

### Recommendation

Implement both: hybrid (Fix A) eliminates the syscall overhead for the majority of
caches; larger minimum size (Fix B) reduces growIndex churn during the early
put-heavy phase.

## Summary

| Improvement                | Effort      | Memory impact              | Throughput impact            |
|----------------------------|-------------|----------------------------|------------------------------|
| ~~Timestamp to data entry~~    | ~~Low~~         | ~~-33% index~~                 | ~~Better probe locality~~ DONE |
| ~~Control-byte array~~         | ~~Medium~~      | ~~+1 byte/bucket~~             | NOT NEEDED with on-disk probing |
| ~~Fix hash==0~~                | ~~Trivial~~     | ~~None~~                       | ~~Correctness fix~~ DONE              |
| Backward-shift deletion    | Medium      | Eliminates tombstone bloat | Shorter probe chains         |
| Data file compaction       | Medium-High | Bounded .dat growth        | Faster reads (less seeking)  |
| ~~Fix entryCount drift~~       | ~~Low~~         | ~~None~~                       | ~~Prevents premature growth~~ DONE    |
| Incremental flush          | High        | None                       | Faster flush for large caches|
| ~~Eliminate per-put allocs~~   | ~~Low~~         | ~~None~~                       | ~~Less GC pressure on writes~~ DONE |
| Merge into single file     | Medium      | None                       | One fewer fsync per flush    |
| ~~Bounded index memory~~       | ~~Medium-High~~ | ~~O(n) -> O(1) for large~~     | ~~Prevents OOM on large caches~~ DONE (on-disk probing) |
| Hybrid + min index size    | Medium      | Bounded (threshold)        | Restores perf for small caches |
