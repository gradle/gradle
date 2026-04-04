# LogHash Persistent Indexed Cache — Implementation Plan

## Context

### Problem
Gradle's `BTreePersistentIndexedCache` calls `store.flush()` on every `put()` — each write triggers B-tree traversal + RandomAccessFile seeks to write dirty blocks. For "assemble storing configuration cache state with cold daemon | largeJavaMultiProjectNoBuildSrc", this causes thousands of per-put flushes during file hashing and classpath fingerprinting.

Replacing BTree with MapDB/MVStore yields **-1.7s (-19.22%)** on the CC store scenario but **+3.4s (+2.64%)** on "first use" due to class loading overhead (MapDB: 652KB/288 classes, MVStore: 354KB/167 classes) on cold JVM.

### Goal
A new `PersistentIndexedCache` implementation that:
- Eliminates per-put disk I/O (like MapDB/MVStore)
- Has zero additional class loading overhead (unlike MapDB/MVStore)
- Supports concurrent reads (bypasses ExclusiveCacheAccessingWorker queue)
- Provides entry-level cleanup of stale entries

### Workload Characteristics
- **Pure point lookups** — `get`, `put`, `remove` only. No range queries, no iteration, no ordering.
- **Single writer** (ExclusiveCacheAccessingWorker thread), concurrent readers
- **Read-heavy after warm-up** — Guava InMemoryDecoratedCache (300K+ entries) catches most reads
- **Write-heavy during cold daemon** — thousands of new entries on first build
- **Values are small** — FileInfo ~50 bytes, HashCode ~32 bytes, largest is execution history
- **Keys are hashed to long** — via existing `FastKeyHasher` (SipHash-2-4)
- **Crash tolerant** — these are caches, losing entries = recomputation, corruption = rebuild
- **`finishWork()` calls `flush()`** every lock cycle (~200ms idle or 5s max hold)

---

## Requirements

### R1: No per-put disk I/O
`put()` and `remove()` must be in-memory only. Disk writes happen only during `flush()` and `close()`.

### R2: Concurrent reads
`supportsConcurrentReads()` returns `true`. Reads bypass the ExclusiveCacheAccessingWorker queue via `getIfPresentDirectly()` in `AsyncCacheAccessDecoratedCache`.

### R3: Fast open — no per-entry allocations
`open()` must NOT allocate objects per cache entry. For 400K entries, loading must be O(1) allocations (a single ByteBuffer), not O(N) allocations (a ConcurrentHashMap with boxed Longs).

### R4: Separate index file
A compact binary hash table (`cache.idx`) maps keyHash (long) to file offset (long). Loaded into a single `ByteBuffer` on open. The data file (`cache.dat`) is append-only.

### R5: Time-based entry-level cleanup
Stale entries (files deleted, dependencies removed) must be cleaned up over time. Requirements:
- **Time-based eviction**: entries not accessed for N days (configurable, e.g. 30 days) are eligible for removal.
- **Gradle cleanup cycle integration**: cleanup can be triggered externally by Gradle's daily cleanup cycle (`DefaultCacheCleanupExecutor` → `CacheCleanupStrategy.clean()`), not only on `close()`.
- **Last-access timestamps**: the index file stores a `lastAccessMillis` per entry. Updated during compaction for entries in the `accessedKeys` set.
- **No two-file generational scheme**: the timestamp approach is more flexible and integrates with Gradle's existing N-days-based `LeastRecentlyUsedCacheCleanup` pattern.

### R6: Crash resilience
- Between flushes: data file has last-flushed state, index file has last-compacted state. Both valid.
- During flush (partial append to data file): on next open, entries referenced by the index are valid. Appended-but-not-indexed entries (flushedIndex was in-memory) are lost — acceptable, they'll be recomputed.
- During compaction (writing new `.new` files): old files still exist. On crash before rename, old files are used. On crash between renames, the cache detects inconsistency (index references invalid offset in data) and rebuilds from scratch.

### R6a: Cleanup integration with Gradle's cache cleanup cycle
- Expose a `cleanup(Instant removeUnusedEntriesOlderThan)` method on the cache.
- This method can be called from a `CacheCleanupStrategy` / `CleanupAction` registered with the `DefaultCacheCleanupExecutor`.
- Cleanup reads the index, removes entries where `lastAccessMillis < removeUnusedEntriesOlderThan`, and rewrites data + index files.
- The existing cleanup infrastructure triggers daily via `GradleUserHomeCleanupServices.beforeComplete()` → `DefaultCacheCoordinator.cleanup()` → `DefaultCacheCleanupExecutor.cleanup()`.

### R7: No new library dependencies
Implementation uses only classes already on the Gradle classpath. Zero new JARs in the distribution. Reuse existing `FastKeyHasher`, `KryoBackedEncoder`/`KryoBackedDecoder`, `Serializer<K>`/`Serializer<V>`.

### R8: Conform to existing interface
Implement `PersistentIndexedCache<K, V>` interface:
- `V get(K key)`, `void put(K key, V value)`, `void remove(K key)`
- `void flush()`, `void close()`, `boolean isOpen()`
- `void reset()`, `void clear()`, `void verify()`
- `boolean supportsConcurrentReads()` → `true`

### R9: Lifecycle compatibility
Must work correctly with `DefaultMultiProcessSafeIndexedCache` lifecycle:
- `finishWork()` → calls `flush()` between lock cycles
- `afterLockAcquire()` → if another process modified the cache, calls `close()` then reopens on next access
- `beforeLockRelease()` → captures file lock state
- Lazy open via `DefaultCacheCoordinator.doCreateCache()` supplier

---

## Architecture

### File Layout

```
<cache-dir>/
  <cacheName>.dat            ← append-only data log
  <cacheName>.idx            ← hash table index (rebuilt on compaction/cleanup)
```

### Data File Format (`cache.dat`)

Append-only sequence of entries:
```
[entry]:
  [8 bytes: keyHash (long)]
  [4 bytes: valueLen (int)]
  [valueLen bytes: serialized value]
  [1 byte: entry type — PUT=1, REMOVE=2]
```

The entry type byte is at the END so that a partial write (crash during append) results in a truncated entry that's detectable: if we can't read the full `8 + 4 + valueLen + 1` bytes, the entry is incomplete → truncate.

Duplicate keys may exist (append-only). During index rebuild, last entry wins.

### Index File Format (`cache.idx`)

Fixed-size hash table for O(1) lookups:
```
[header]:
  [4 bytes: magic number (0x47484153 = "GHAS")]
  [4 bytes: bucket count (N, must be power of 2)]
  [4 bytes: entry count]
  [4 bytes: reserved/padding]

[buckets]: N × 24 bytes
  [8 bytes: keyHash (long) — 0 means empty bucket]
  [8 bytes: offset in data file (long)]
  [8 bytes: lastAccessMillis (long) — timestamp of last get/put]
```

Total index size: 16 + N×24 bytes. Bucket count = next power of 2 above `entryCount * 4/3` (75% load factor for open addressing with linear probing).

For 50K entries: N ≈ 65536, size = 16 + 1.5MB. For 400K entries: N ≈ 524288, size = 16 + 12MB.

The `lastAccessMillis` field is set to `System.currentTimeMillis()` when the entry is written during compaction and the key is in the `accessedKeys` set. Entries not accessed this session retain their existing timestamp from the previous index.

### In-Memory Structure

```java
// Tier 1: buffered writes not yet on disk (small, hot)
ConcurrentHashMap<Long, byte[]> pendingWrites;

// Tier 2: flushed since last compaction (small, entries added during session)
ConcurrentHashMap<Long, Long> flushedIndex;    // keyHash → offset in data file

// Tier 3: main index from last compaction (bulk, read-only ByteBuffer)
ByteBuffer indexBuffer;                         // heap-loaded cache.idx
int bucketCount;
int bucketMask;                                 // bucketCount - 1 (for fast modulo)
static final int HEADER_SIZE = 16;
static final int BUCKET_SIZE = 24;              // keyHash(8) + offset(8) + lastAccess(8)

// Cleanup tracking: keys accessed (get or put) this session
Set<Long> accessedKeys = ConcurrentHashMap.newKeySet();
```

### Operations

**`open()`**
1. Read `cache.idx` into `ByteBuffer` — single allocation, ~1ms for 400K entries.
2. Validate header magic number. If invalid or file doesn't exist → start with empty cache.
3. Initialize empty `pendingWrites`, `flushedIndex`, `accessedKeys`.
4. Open `cache.dat` in append mode, set `appendPosition = file.length()`.
5. Validate data file consistency: check that `appendPosition` is sane (≥ 0). If data file missing but index exists → rebuild from scratch.

**`get(key)`**
1. `hash = FastKeyHasher.getHashCode(key)`
2. Check `pendingWrites.get(hash)` → if TOMBSTONE return null, if byte[] → deserialize + return
3. Check `flushedIndex.get(hash)` → if found, read value from data file at offset
4. Probe `indexBuffer` hash table → if found, read value from data file at offset
5. `accessedKeys.add(hash)` on any hit
6. Return null on miss

**`put(key, value)`**
1. `hash = FastKeyHasher.getHashCode(key)`
2. `pendingWrites.put(hash, serialize(value))`
3. `accessedKeys.add(hash)`
4. No disk I/O.

**`remove(key)`**
1. `pendingWrites.put(hash, TOMBSTONE)`

**`flush()`**
Called by `finishWork()` every lock cycle (~5s max).
1. For each entry in `pendingWrites`:
   - Append to `cache.dat`: keyHash + valueLen + value + entryType
   - `flushedIndex.put(keyHash, appendOffset)` (or remove if TOMBSTONE)
2. `pendingWrites.clear()`
3. Data file write is sequential (buffered OutputStream, one flush to disk).

**`close()`**
1. `flush()` — drain remaining pending writes.
2. Compact:
   a. Collect all live entries from all tiers (pendingWrites is empty after flush, flushedIndex + indexBuffer).
   b. For each entry:
      - If key in `accessedKeys` → set `lastAccessMillis = now`
      - If key NOT in `accessedKeys` → keep existing `lastAccessMillis` from index
   c. Write new `cache.dat.new` with all entries (sequentially, compact — no duplicates).
   d. Write new `cache.idx.new` hash table with updated timestamps.
   e. Atomic rotate: delete old files, rename `.new` → final names.
3. Close file handles, clear in-memory state.

Note: `close()` does NOT evict stale entries. It preserves all entries with their timestamps. Eviction is done by `cleanup()` (see below), which is triggered by Gradle's cleanup cycle.

**`cleanup(Instant removeUnusedEntriesOlderThan)`**
Triggered externally by Gradle's cache cleanup cycle (daily).
1. Read `cache.idx` into memory.
2. For each non-empty bucket: if `lastAccessMillis < removeUnusedEntriesOlderThan` → mark for removal.
3. If nothing to remove → return (no I/O).
4. Read values for surviving entries from `cache.dat`.
5. Rewrite `cache.dat.new` + `cache.idx.new` without removed entries.
6. Atomic rename.

This integrates with Gradle's `CacheCleanupStrategy` / `CleanupAction` pattern. The cleanup threshold (e.g. 30 days) is configurable at the cache builder level.

**`reset()`** — `close()` + `open()` (existing contract).

**`clear()`** — delete all files, reinitialize empty.

**`verify()`** — validate index magic number and entry count consistency.

### Cleanup Strategy

**How timestamps flow:**
- `put(key, value)` → `accessedKeys.add(hash)` (in-memory, no timestamp written yet)
- `get(key)` → `accessedKeys.add(hash)` on hit (in-memory)
- `close()` compaction → entries in `accessedKeys` get `lastAccessMillis = now`; others keep their existing timestamp
- `cleanup(olderThan)` → removes entries where `lastAccessMillis < olderThan`

**Guava cache interaction:**
On a warm daemon, most reads hit the Guava `InMemoryDecoratedCache` and never reach the persistent store's `get()`. This means entries could appear "not accessed" even though they're actively used. This is handled naturally:
- On daemon restart: Guava cache is cold → all entries are re-read from persistent store → `accessedKeys` tracks them → timestamps updated on next `close()`
- Between restarts: `finishWork()` calls `flush()` (not `close()`), so no compaction happens and timestamps aren't rewritten. The `accessedKeys` set accumulates until `close()`.

**Integration with Gradle cleanup cycle:**
```
GradleUserHomeCleanupServices.beforeComplete()
  → DefaultCacheCoordinator.cleanup()
    → DefaultCacheCleanupExecutor checks gc.properties (daily)
      → CacheCleanupStrategy.clean()
        → LogHashCacheCleanupAction.cleanup(removeUnusedEntriesOlderThan)
          → LogHashPersistentIndexedCache.cleanup(removeUnusedEntriesOlderThan)
```

The `LogHashCacheCleanupAction` is registered as a `CleanupAction` when the cache is created. It holds a reference to the cache and delegates to `cleanup()`.

**Eviction guarantee:** Entries not accessed (get or put) within the configured retention period (e.g. 30 days) are removed during the next daily cleanup cycle.

---

## Implementation Steps

### Step 1: Create `LogHashPersistentIndexedCache<K, V>`

**New file:** `platforms/core-execution/persistent-cache/src/main/java/org/gradle/cache/internal/btree/LogHashPersistentIndexedCache.java`

Implements `PersistentIndexedCache<K, V>`. Internal structure:
- Reuse `FastKeyHasher<K>` (existing, same package)
- Reuse `Serializer<V>` with `KryoBackedEncoder`/`KryoBackedDecoder` (existing)
- ThreadLocal serialization buffers (pattern from MapDBPersistentIndexedCache)
- `ConcurrentHashMap` for pendingWrites and flushedIndex
- `ByteBuffer` for the index hash table

### Step 2: Wire into `DefaultCacheCoordinator`

**Modify:** `platforms/core-execution/persistent-cache/src/main/java/org/gradle/cache/internal/DefaultCacheCoordinator.java`

Change `doCreateCache()` (line 363-365):
```java
<K, V> PersistentIndexedCache<K, V> doCreateCache(File cacheFile, Serializer<K> keySerializer, Serializer<V> valueSerializer) {
    return new LogHashPersistentIndexedCache<>(cacheFile, keySerializer, valueSerializer);
}
```

Note: `cacheFile` is currently `<cacheDir>/<cacheName>.bin`. We'll use the same base path but with `.dat` and `.idx` extensions.

### Step 2a: Create `LogHashCacheCleanupAction` (DEFERRED — not needed for initial performance testing)

**New file:** `platforms/core-execution/persistent-cache/src/main/java/org/gradle/cache/internal/LogHashCacheCleanupAction.java`

Implements `CleanupAction`. Wraps the `LogHashPersistentIndexedCache` and calls `cleanup(removeUnusedEntriesOlderThan)` on each indexed cache tracked by the coordinator. This action can be composed with existing cleanup actions via `CompositeCleanupAction.Builder`.

The `DefaultCacheCoordinator` registers this action alongside any existing cleanup strategy, so it runs during the daily cleanup cycle. The retention period (e.g. 30 days) is configurable.

### Step 3: Create shared abstract test base (DEFERRED — not needed for initial performance testing)

**New file:** `platforms/core-execution/persistent-cache/src/test/groovy/org/gradle/cache/internal/btree/AbstractPersistentIndexedCacheTest.java`

Extract common test patterns from `BTreeIndexedCacheTest` (477 lines) and `MVStorePersistentIndexedCacheTest` (351 lines):
- Basic put/get/remove
- Persistence across close/reopen
- Handling corrupt files
- Concurrent read safety (for implementations that support it)
- Flush behavior

### Step 4: Create `LogHashPersistentIndexedCacheTest`

**New file:** `platforms/core-execution/persistent-cache/src/test/groovy/org/gradle/cache/internal/btree/LogHashPersistentIndexedCacheTest.java`

Extends the shared abstract test + additional tests:
- Time-based entry cleanup (entries older than threshold evicted by `cleanup()`)
- Crash recovery (truncated data file, missing index, inconsistent state)
- Index rebuild / compaction correctness
- Three-tier lookup correctness (pendingWrites → flushedIndex → indexBuffer)
- Large cache performance (400K entries open/get/close cycle)

### Step 5: Remove MapDB and MVStore dependencies (DEFERRED — after performance validation)

**Modify:** `platforms/core-execution/persistent-cache/build.gradle.kts`
- Remove `implementation(libs.h2Mvstore)` (line 25)
- Remove `implementation(libs.mapdb)` (line 26)

**Delete:**
- `MapDBPersistentIndexedCache.java`
- `MVStorePersistentIndexedCache.java`
- `MVStorePersistentIndexedCacheTest.java`

(Keep `BTreePersistentIndexedCache.java` as fallback until LogHash is proven stable.)

---

## Key Files

| File | Action |
|---|---|
| `platforms/core-execution/persistent-cache/src/main/java/org/gradle/cache/internal/btree/LogHashPersistentIndexedCache.java` | **Create** — main implementation |
| `platforms/core-execution/persistent-cache/src/main/java/org/gradle/cache/internal/btree/PersistentIndexedCache.java` | Read-only — interface to implement |
| `platforms/core-execution/persistent-cache/src/main/java/org/gradle/cache/internal/btree/FastKeyHasher.java` | Read-only — reuse for key hashing |
| `platforms/core-execution/persistent-cache/src/main/java/org/gradle/cache/internal/DefaultCacheCoordinator.java` | **Modify** — `doCreateCache()` at line 363 |
| `platforms/core-execution/persistent-cache/src/main/java/org/gradle/cache/internal/DefaultMultiProcessSafeIndexedCache.java` | Read-only — lifecycle caller |
| `platforms/core-execution/persistent-cache/build.gradle.kts` | **Modify** — remove MapDB/MVStore deps |
| `platforms/core-execution/persistent-cache/src/test/groovy/org/gradle/cache/internal/btree/BTreeIndexedCacheTest.java` | Read-only — reference test patterns |
| `platforms/core-execution/persistent-cache/src/test/groovy/org/gradle/cache/internal/btree/LogHashPersistentIndexedCacheTest.java` | **Create** — tests |

---

## Verification

### Unit Tests
Run from `/Users/asodja/workspace/agents`:
```bash
./gradlew :persistent-cache:test --tests "*LogHashPersistentIndexedCacheTest*"
```

### Integration Tests
```bash
./gradlew :persistent-cache:test
```
Ensure all existing cache tests still pass (BTree tests should still work as BTree code is retained).

### Performance Tests
Run the two key scenarios and compare against baseline:
```bash
# CC store (should improve ~19%)
./gradlew :performance:performanceTest --tests "*JavaConfigurationCachePerformanceTest*" -PperformanceScenario="assemble storing configuration cache state with cold daemon"

# First use (should NOT regress)  
./gradlew :performance:performanceTest --tests "*JavaFirstUsePerformanceTest*" -PperformanceScenario="first use"
```

### Manual Smoke Test
```bash
# Build a large project with configuration cache
cd /tmp && git clone <large-project>
./gradlew --configuration-cache assemble  # First run: stores CC
./gradlew --configuration-cache assemble  # Second run: loads CC
# Verify both succeed and cache files are created
```
