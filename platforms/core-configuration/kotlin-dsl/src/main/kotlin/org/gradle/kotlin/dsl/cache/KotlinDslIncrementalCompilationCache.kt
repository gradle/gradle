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

import org.gradle.internal.hash.Hashing
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories


/**
 * On-disk cache of per-script IC working state, stable script-source paths, and stable
 * script-output paths. Backed by a [org.gradle.cache.PersistentCache] under
 * `<gradleUserHome>/caches/<gradleVersion>/kotlin-dsl-ic/` and owned by
 * [KotlinDslIncrementalCompilationStore]. The content-addressed classpath snapshots are a separate
 * concern, held by [KotlinDslClasspathEntrySnapshotCache].
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
 *
 * Known limitations:
 *  - Per-entry contents under [scriptsCacheDirectory], [scriptSourcesCacheDirectory] and
 *    [scriptOutputsCacheDirectory] grow without bound; Gradle's user-home cleanup reclaims whole
 *    cache directories, not entries within an active one. See
 *    [KotlinDslIncrementalCompilationStore]'s cleanup TODO for the plan.
 *  - The cache root lock does *not* serialize per-script writes. Concurrent compiles of the same
 *    scriptIdentity from two processes sharing `GRADLE_USER_HOME` can corrupt that script's IC
 *    working state; BTA detects the inconsistency on the next compile and recovers by falling
 *    back to a full recompile.
 */
@ServiceScope(Scope.UserHome::class)
class KotlinDslIncrementalCompilationCache(
    val scriptsCacheDirectory: Path,
    val scriptSourcesCacheDirectory: Path,
    val scriptOutputsCacheDirectory: Path,
) {
    /**
     * Returns BTA's per-script IC working-state directory identified by [scriptIdentity], creating
     * it if needed. The directory is owned entirely by BTA — it can (and does, on rebuild
     * fallback) wipe contents under here, so nothing else should write into this subtree. The
     * stable script-source files live under a sibling directory; see [scriptSourceFile].
     */
    fun scriptCacheDirectory(scriptIdentity: String): Path =
        scriptsCacheDirectory.resolve(dirNameFor(scriptIdentity)).also { it.createDirectories() }

    /**
     * Whether incremental compilation is worth configuring for [scriptIdentity]. IC only pays off
     * once a prior compile has left state to build on, so successive compiles of a script progress
     * through three passes:
     *  1. cold — nothing cached yet → skip IC; full compile.
     *  2. bootstrap — prior outputs only → run IC; full compile that records IC state.
     *  3. incremental — IC working state exists → run IC; recompile only what changed.
     *
     * Only the cold pass skips IC: it would rebuild everything anyway, so attaching IC there just
     * pays for snapshotting and bookkeeping that buys nothing.
     */
    fun shouldConfigureIncrementalCompilation(scriptIdentity: String): Boolean {
        val dirNameFor = dirNameFor(scriptIdentity)
        val hasIncrementalState = hasPriorState(scriptsCacheDirectory.resolve(dirNameFor))   // pass 3: incremental
        val hasPriorOutputs = hasPriorState(scriptOutputsCacheDirectory.resolve(dirNameFor)) // pass 2: bootstrap
        return hasIncrementalState || hasPriorOutputs                                        // neither → pass 1: cold (skip IC)
    }

    /**
     * Returns the stable per-[scriptIdentity] directory into which the Kotlin compiler writes class
     * outputs. Persists across builds so BTA's incremental compilation can reuse prior outputs even
     * when the kotlin-dsl workspace cache (one layer up) invalidates and hands compile a fresh
     * destination per cache key. Callers copy the contents into the workspace destination after the
     * compile completes; see `BTACompiler.compile`.
     */
    fun scriptOutputsDirectory(scriptIdentity: String): Path =
        scriptOutputsCacheDirectory.resolve(dirNameFor(scriptIdentity)).also { it.createDirectories() }

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
        scriptSourcesCacheDirectory.resolve(dirNameFor(scriptIdentity)).also { it.createDirectories() }.resolve(fileName)

    private fun dirNameFor(scriptIdentity: String): String =
        Hashing.hashString(scriptIdentity).toString()

    private fun hasPriorState(dir: Path): Boolean =
        Files.isDirectory(dir) && Files.newDirectoryStream(dir).use { it.iterator().hasNext() }
}
