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

import org.gradle.cache.FineGrainedPersistentCache
import org.gradle.internal.hash.Hashing
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories


/**
 * On-disk cache of per-script incremental-compilation state, backed by a
 * [FineGrainedPersistentCache] under `<gradleUserHome>/caches/<gradleVersion>/kotlin-dsl-ic/` and
 * owned by [KotlinDslIncrementalCompilationStore].
 *
 * One entry per script, keyed by a hash of its identity, at `<scriptHash>/`:
 *  - `ic-state/` — BTA's IC working state (source hashes, who-references-what, dirty tracking). See [scriptCacheDirectory].
 *  - `outputs/` — the stable destination the compiler writes class files into.
 *  - `sources/` — stable script-text files handed to the compiler. See [scriptSourceFile].
 *
 * Concurrency: each compilation runs under the script's lock via [withScriptState].
 */
@ServiceScope(Scope.UserHome::class)
class KotlinDslIncrementalCompilationCache(
    private val cache: FineGrainedPersistentCache,
) {
    private val baseDir: Path = cache.baseDir.toPath()

    /**
     * Runs [action] holding [scriptIdentity]'s lock — exclusive across the processes sharing
     * this `GRADLE_USER_HOME`, so concurrent compiles of the same script can't corrupt its
     * read-modify-write IC state. Different scripts use different keys and don't contend.
     */
    fun <T> withScriptState(scriptIdentity: String, action: () -> T): T {
        var result: T? = null
        cache.useCache(dirNameFor(scriptIdentity)) { result = action() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /**
     * Returns BTA's per-script IC working-state directory, creating it if needed.
     */
    fun scriptCacheDirectory(scriptIdentity: String): Path =
        scriptEntry(scriptIdentity).resolve("ic-state").also { it.createDirectories() }

    /**
     * Returns the stable per-script directory the compiler writes class outputs into, creating it if needed.
     */
    fun scriptOutputsDirectory(scriptIdentity: String): Path =
        scriptEntry(scriptIdentity).resolve("outputs").also { it.createDirectories() }

    /**
     * Returns a stable per-(scriptIdentity, fileName) path for the script text handed to the
     * compiler. The same `scriptIdentity` always resolves to the same path across builds.
     */
    fun scriptSourceFile(scriptIdentity: String, fileName: String): Path =
        scriptEntry(scriptIdentity).resolve("sources").also { it.createDirectories() }.resolve(fileName)

    private fun scriptEntry(scriptIdentity: String): Path =
        baseDir.resolve(dirNameFor(scriptIdentity))

    private fun dirNameFor(scriptIdentity: String): String =
        Hashing.hashString(scriptIdentity).toString()

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
        val entry = scriptEntry(scriptIdentity)
        val hasIncrementalState = hasPriorState(entry.resolve("ic-state")) // pass 3: incremental
        val hasPriorOutputs = hasPriorState(entry.resolve("outputs"))      // pass 2: bootstrap
        return hasIncrementalState || hasPriorOutputs                               // neither → pass 1: cold (skip IC)
    }

    private fun hasPriorState(dir: Path): Boolean =
        Files.isDirectory(dir) && Files.newDirectoryStream(dir).use { it.iterator().hasNext() }
}
