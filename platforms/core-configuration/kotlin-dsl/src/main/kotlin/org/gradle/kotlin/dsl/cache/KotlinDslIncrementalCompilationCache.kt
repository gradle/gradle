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

package org.gradle.kotlin.dsl.cache

import org.gradle.cache.IndexedCache
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.createDirectories


/**
 * On-disk cache of BTA classpath snapshots, per-script IC working state, stable script-source
 * paths, and stable script-output paths. Backed by a [org.gradle.cache.PersistentCache] under
 * `<gradleUserHome>/caches/<gradleVersion>/kotlin-dsl-ic/` and owned by
 * [KotlinDslIncrementalCompilationStore].
 *
 * The directories persisted across builds:
 *  - [scriptsCacheDirectory] — one directory per scriptIdentity, owned by BTA: holds its
 *    incremental-compilation working state (source hashes, who-references-what, dirty-file
 *    tracking). BTA wipes contents here on rebuild fallback; nothing else writes into it.
 *  - [scriptSourcesCacheDirectory] — stable per-(scriptIdentity, stage) script-text files we
 *    hand the compiler. A sibling of [scriptsCacheDirectory] because BTA's IC root cleanup
 *    would otherwise delete it.
 *  - [scriptOutputsCacheDirectory] — stable per-scriptIdentity destination the compiler writes
 *    class files into. Persists across builds so BTA's IC can reuse prior outputs across
 *    workspace-cache-key changes; callers copy the contents into the workspace destination
 *    after the compile completes.
 *  - [snapshotsCacheDirectory] — content-addressed BTA classpath snapshots, one file per unique
 *    jar/class-directory content. Shared across all scripts AND across the two layers that
 *    consume classpath snapshots:
 *     1. compile avoidance — the workspace-cache key incorporates the per-entry ABI-rollup hash,
 *        so non-ABI changes in the classpath keep landing on a single cache entry and never
 *        invoke the compiler.
 *     2. BTA incremental compilation — when the workspace cache misses, BTA reads the same
 *        snapshots back off disk and uses them to decide which sources need recompilation.
 *
 * [snapshotIndex] is an in-memory-decorated map from a classpath entry's content hash to its
 * ABI-rollup hash. Storing the rollup here means the compile-avoidance fingerprinter never has
 * to read the snapshot back off disk on cache hits, and that the snapshot itself is computed
 * once per unique classpath content — whichever layer asks first generates both outputs.
 *
 * Known limitations:
 *  - Per-entry contents under [scriptsCacheDirectory], [scriptSourcesCacheDirectory],
 *    [scriptOutputsCacheDirectory] and [snapshotsCacheDirectory] grow without bound; Gradle's
 *    user-home cleanup reclaims whole cache directories, not entries within an active one. See
 *    [KotlinDslIncrementalCompilationStore]'s cleanup TODO for the plan.
 *  - The cache root lock does *not* serialize per-script writes. Concurrent compiles of the same
 *    scriptIdentity from two processes sharing `GRADLE_USER_HOME` can corrupt that script's IC
 *    working state; BTA detects the inconsistency on the next compile and recovers by falling
 *    back to a full recompile. The snapshot files are written via temp + atomic rename (see
 *    [snapshotAndAbiHashFor]) and are unaffected.
 *  - A JVM crash between [Files.createTempFile] and the atomic rename in [snapshotAndAbiHashFor]
 *    leaves an orphaned `*.snapshot.tmp` file under [snapshotsCacheDirectory] forever. Sweep on
 *    cache open or fold into the eventual cleanup strategy.
 *  - [snapshotAndAbiHashFor]'s `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)` is filesystem-dependent
 *    on Windows; SMB / older filesystems can throw [java.nio.file.AtomicMoveNotSupportedException].
 *    If hit, the simple fallback is to retry with `REPLACE_EXISTING` alone — concurrent writers
 *    then race non-atomically on the same target, but BTA's snapshot bytes are deterministic for
 *    a given content hash so the result is still consistent.
 */
@ServiceScope(Scope.UserHome::class)
class KotlinDslIncrementalCompilationCache(
    val scriptsCacheDirectory: Path,
    val scriptSourcesCacheDirectory: Path,
    val scriptOutputsCacheDirectory: Path,
    val snapshotsCacheDirectory: Path,
    private val snapshotIndex: IndexedCache<HashCode, HashCode>,
) {
    /**
     * Returns BTA's per-script IC working-state directory identified by [scriptIdentity], creating
     * it if needed. The directory is owned entirely by BTA — it can (and does, on rebuild
     * fallback) wipe contents under here, so nothing else should write into this subtree. The
     * stable script-source files live under a sibling directory; see [scriptSourceFile].
     */
    fun scriptCacheDirectory(scriptIdentity: String): Path =
        scriptsCacheDirectory.resolve(Hashing.hashString(scriptIdentity).toString()).also { it.createDirectories() }

    /**
     * Returns the stable per-[scriptIdentity] directory into which the Kotlin compiler writes class
     * outputs. Persists across builds so BTA's incremental compilation can reuse prior outputs even
     * when the kotlin-dsl workspace cache (one layer up) invalidates and hands compile a fresh
     * destination per cache key. Callers copy the contents into the workspace destination after the
     * compile completes; see `BTACompiler.compile`.
     */
    fun scriptOutputsDirectory(scriptIdentity: String): Path =
        scriptOutputsCacheDirectory.resolve(Hashing.hashString(scriptIdentity).toString()).also { it.createDirectories() }

    /**
     * Returns a stable per-(scriptIdentity, fileName) path at which the kotlin-dsl machinery can
     * materialise the script text it hands to the Kotlin compiler. The same `scriptIdentity` always
     * resolves to the same path across builds.
     *
     * Used in place of per-compile temporary files for two reasons:
     *  - The compiler API requires a `File`, but the text actually compiled at each stage is a
     *    transform of the original script (whitespace-erased fragments for stage 1, partially-
     *    evaluated body for stage 2) that doesn't exist on disk elsewhere.
     *  - With BTA incremental compilation, the source file's absolute path is a primary key in
     *    its source-snapshot and lookup DBs; random per-compile paths make every iteration look
     *    like a brand-new source and defeat IC. A stable path lets BTA's snapshot find real diffs.
     *
     * Lives under [scriptSourcesCacheDirectory] (a sibling of [scriptsCacheDirectory]) — outside
     * BTA's IC root, because BTA wipes its IC root on rebuild fallback.
     */
    fun scriptSourceFile(scriptIdentity: String, fileName: String): Path =
        scriptSourcesCacheDirectory.resolve(Hashing.hashString(scriptIdentity).toString()).also { it.createDirectories() }.resolve(fileName)

    /**
     * Returns the content-addressed snapshot file path and the ABI-rollup hash for the classpath
     * entry identified by [contentHash], producing both via [generate] if not yet cached.
     *
     * [generate] receives a unique temp path: it must write the BTA snapshot to that path and
     * return the ABI-rollup hash derived from the same in-memory snapshot. The cache renames the
     * temp file atomically into the final location, so concurrent producers and JVM crashes
     * mid-write cannot leave a partially-written file at the returned path.
     *
     * The first caller for a given [contentHash] does the BTA work; subsequent callers (whether
     * compile-avoidance fingerprinter or BTA incremental compilation, in this or any later build
     * sharing this `GRADLE_USER_HOME`) get the cached pair without invoking [generate].
     */
    fun snapshotAndAbiHashFor(contentHash: HashCode, generate: (Path) -> HashCode): SnapshotAndAbiHash {
        val snapshotFile = snapshotsCacheDirectory.resolve("$contentHash.snapshot")
        val abiHash = snapshotIndex.get(contentHash) { _ ->
            val tmp = Files.createTempFile(snapshotsCacheDirectory, "$contentHash.", ".snapshot.tmp")
            try {
                val abiRollup = generate(tmp)
                Files.move(tmp, snapshotFile, REPLACE_EXISTING, ATOMIC_MOVE)
                abiRollup
            } finally {
                Files.deleteIfExists(tmp)
            }
        }
        return SnapshotAndAbiHash(snapshotFile, abiHash)
    }

    class SnapshotAndAbiHash(val snapshotFile: Path, val abiHash: HashCode)
}
