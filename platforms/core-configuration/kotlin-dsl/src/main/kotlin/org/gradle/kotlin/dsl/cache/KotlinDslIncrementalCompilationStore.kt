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

import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.nio.file.Path
import kotlin.io.path.createDirectories


/**
 * Owns the [PersistentCache] backing [KotlinDslIncrementalCompilationCache]; the public surface
 * (directories + snapshot-index API) is exposed by that class. Plays the role
 * [org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache] plays for the legacy
 * compile-avoidance fingerprinter.
 *
 * Layout under `<gradleUserHome>/caches/<gradleVersion>/kotlin-dsl-ic/`:
 *  - `scripts/<scriptHash>/...`           — BTA's per-script IC working state (BTA-owned;
 *                                           it wipes contents here on rebuild fallback, so
 *                                           nothing else writes into this subtree).
 *  - `script-sources/<scriptHash>/<filename>` — stable per-(scriptIdentity, stage) script-text
 *                                           materialisation that's handed to the compiler; kept
 *                                           in a sibling tree precisely so BTA's IC root cleanup
 *                                           doesn't delete it.
 *  - `script-outputs/<scriptHash>/...`    — stable per-(scriptIdentity, stage) compile outputs.
 *                                           The Kotlin compiler writes class files here; callers
 *                                           copy them to the workspace destination after compile.
 *                                           Lets BTA's IC see the previous build's outputs even
 *                                           though the workspace cache invalidates its destination
 *                                           on every cache-key miss.
 *  - `snapshots/<contentHash>.snapshot`   — content-addressed dependency snapshots, shared by
 *                                           compile avoidance and BTA IC.
 *  - `kotlinDslClasspathSnapshotIndex.bin` — IndexedCache backing file (content hash → ABI
 *                                           rollup hash; see [KotlinDslIncrementalCompilationCache]).
 *
 * The cache is opened with [FileLockManager.LockMode.OnDemand] (shared lock by default, exclusive
 * for IndexedCache writes); [close] is invoked by the service registry on shutdown. The lock is
 * cache-root level only — see the limitation notes on [KotlinDslIncrementalCompilationCache].
 */
@ServiceScope(Scope.UserHome::class)
internal class KotlinDslIncrementalCompilationStore(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    private val inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory,
) : Closeable {

    // TODO: wire fine-grained cache cleanup. Nothing below this cache root is currently pruned —
    //  Gradle's user-home cleanup only reclaims the whole `kotlin-dsl-ic/` directory after long
    //  disuse, and five things grow monotonically while it's in use:
    //   - `scripts/<scriptHash>/`: BTA's per-script IC working state.
    //   - `script-sources/<scriptHash>/`: stable script-text files per (scriptIdentity, stage).
    //   - `script-outputs/<scriptHash>/`: stable compile outputs per (scriptIdentity, stage).
    //   - `snapshots/<contentHash>.snapshot`: every unique jar/class-dir content ever seen.
    //   - `kotlinDslClasspathSnapshotIndex.bin`: one row per content hash; the in-memory part
    //     is capped at maxEntriesToKeepInMemory in KotlinDslIncrementalCompilationCache, the
    //     on-disk file isn't.
    //  Fix: mirror KotlinDslWorkspaceProvider — inject FineGrainedCacheCleanupStrategyFactory,
    //  FileAccessTimeJournal, and CacheConfigurationsInternal; open this cache with
    //  `.withCleanupStrategy(...)`; and update access times in
    //  KotlinDslIncrementalCompilationCache.snapshotAndAbiHashFor and in BTACompiler's
    //  scriptIcRootFor so LRU eviction picks the right things to drop. The same pass can also
    //  sweep orphan `*.snapshot.tmp` files left by JVM crashes mid-write (called out in the
    //  KotlinDslIncrementalCompilationCache doc).
    private val cache: PersistentCache = cacheBuilderFactory
        .createCacheBuilder("kotlin-dsl-ic")
        .withDisplayName("Kotlin DSL incremental compilation cache")
        .withInitialLockMode(FileLockManager.LockMode.OnDemand)
        .open()

    val scriptsCacheDirectory: Path = cache.baseDir.toPath().resolve("scripts").also { it.createDirectories() }

    val scriptSourcesCacheDirectory: Path = cache.baseDir.toPath().resolve("script-sources").also { it.createDirectories() }

    val scriptOutputsCacheDirectory: Path = cache.baseDir.toPath().resolve("script-outputs").also { it.createDirectories() }

    val snapshotsCacheDirectory: Path = cache.baseDir.toPath().resolve("snapshots").also { it.createDirectories() }

    /**
     * Creates an in-memory-decorated [IndexedCache] inside the underlying [PersistentCache].
     * Mirrors [org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache.createIndexedCache].
     */
    fun <K : Any, V : Any> createIndexedCache(
        parameters: IndexedCacheParameters<K, V>,
        maxEntriesToKeepInMemory: Int,
        cacheInMemoryForShortLivedProcesses: Boolean,
    ): IndexedCache<K, V> = cache.createIndexedCache(
        parameters.withCacheDecorator(
            inMemoryCacheDecoratorFactory.decorator(maxEntriesToKeepInMemory, cacheInMemoryForShortLivedProcesses)
        )
    )

    override fun close() {
        cache.close()
    }
}
