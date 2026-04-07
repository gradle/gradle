# LogHashPersistentIndexedCache — Possible Index Improvements

## 1. Drop the `lastAccess` field

Each bucket is 24 bytes: `[hash:8][offset:8][lastAccess:8]`. The `lastAccess` field is
**written but never read** anywhere in the codebase. Removing it:

- Shrinks bucket size from 24 to 16 bytes (33% less index memory)
- Improves cache-line utilization: 4 buckets per 64-byte cache line instead of 2.67
- Faster rehash, flush, and probing
- Simpler code

If LRU eviction is planned for the future, it can be added back then.

## 2. Separate control-byte array (Swiss table-style metadata)

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

## 3. Fix the `hash == 0` sentinel collision

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

## Summary

| Improvement                | Effort      | Memory impact              | Throughput impact            |
|----------------------------|-------------|----------------------------|------------------------------|
| Remove `lastAccess`        | Low         | -33% index                 | Better probe locality        |
| Control-byte array         | Medium      | +1 byte/bucket             | Much faster probing          |
| Fix hash==0                | Trivial     | None                       | Correctness fix              |
| Backward-shift deletion    | Medium      | Eliminates tombstone bloat | Shorter probe chains         |
| Data file compaction       | Medium-High | Bounded .dat growth        | Faster reads (less seeking)  |
| Fix entryCount drift       | Low         | None                       | Prevents premature growth    |
| Incremental flush          | High        | None                       | Faster flush for large caches|
| ~~Eliminate per-put allocs~~   | ~~Low~~         | ~~None~~                       | ~~Less GC pressure on writes~~ DONE |
| Merge into single file     | Medium      | None                       | One fewer fsync per flush    |
