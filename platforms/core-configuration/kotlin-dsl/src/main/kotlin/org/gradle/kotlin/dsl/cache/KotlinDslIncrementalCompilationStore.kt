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
import org.gradle.cache.PersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.nio.file.Path
import kotlin.io.path.createDirectories


/**
 * Owns the [PersistentCache] backing [KotlinDslIncrementalCompilationCache]; the public surface
 * (the per-script directories) is exposed by that class. Holds only the mutable, per-script
 * incremental-compilation state — the content-addressed classpath snapshots live in a separate
 * cache root owned by [KotlinDslClasspathEntrySnapshotStore].
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
 *
 * The cache is opened with [FileLockManager.LockMode.OnDemand]; [close] is invoked by the service
 * registry on shutdown. The lock is cache-root level only — see the limitation notes on
 * [KotlinDslIncrementalCompilationCache].
 */
@ServiceScope(Scope.UserHome::class)
internal class KotlinDslIncrementalCompilationStore(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
) : Closeable {

    // TODO: wire fine-grained cache cleanup. Nothing below this cache root is currently pruned —
    //  Gradle's user-home cleanup only reclaims the whole `kotlin-dsl-ic/` directory after long
    //  disuse, and three things grow monotonically while it's in use:
    //   - `scripts/<scriptHash>/`: BTA's per-script IC working state.
    //   - `script-sources/<scriptHash>/`: stable script-text files per (scriptIdentity, stage).
    //   - `script-outputs/<scriptHash>/`: stable compile outputs per (scriptIdentity, stage).
    //  Fix: mirror KotlinDslWorkspaceProvider — inject FineGrainedCacheCleanupStrategyFactory,
    //  FileAccessTimeJournal, and CacheConfigurationsInternal; open this cache with
    //  `.withCleanupStrategy(...)`; and update access times in BTACompiler's scriptIcRootFor so
    //  LRU eviction picks the right things to drop.
    private val cache: PersistentCache = cacheBuilderFactory
        .createCacheBuilder("kotlin-dsl-ic")
        .withDisplayName("Kotlin DSL incremental compilation cache")
        .withInitialLockMode(FileLockManager.LockMode.OnDemand)
        .open()

    val scriptsCacheDirectory: Path = cache.baseDir.toPath().resolve("scripts").also { it.createDirectories() }

    val scriptSourcesCacheDirectory: Path = cache.baseDir.toPath().resolve("script-sources").also { it.createDirectories() }

    val scriptOutputsCacheDirectory: Path = cache.baseDir.toPath().resolve("script-outputs").also { it.createDirectories() }

    override fun close() {
        cache.close()
    }
}
