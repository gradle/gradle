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
 * Owns the [PersistentCache] backing [KotlinDslClasspathEntrySnapshotCache].
 *
 * Layout under `<gradleUserHome>/caches/<gradleVersion>/kotlin-dsl-classpath-snapshots/`:
 *  - `snapshots/<contentHash>.snapshot`    - content-addressed BTA classpath snapshots.
 *  - `kotlinDslClasspathSnapshotIndex.bin` - IndexedCache backing file (content hash → ABI hash;
 *                                           see [KotlinDslClasspathEntrySnapshotCache]).
 *
 * Opened with [FileLockManager.LockMode.OnDemand] — a coarse cache-level lock (no per-key locking)
 * that serializes the index; the snapshot files are published lock-free via atomic rename in
 * [KotlinDslClasspathEntrySnapshotCache]. [close] is invoked by the service registry on shutdown.
 *
 * TODO: no disk cleanup. The `maxEntriesToKeepInMemory` passed to [createIndexedCache] caps only the
 *  index's in-memory layer (10_000 entries); the on-disk index and the `snapshots/` files are never
 *  pruned, so both grow without bound and only whole-directory user-home cleanup ever reclaims them.
 */
@ServiceScope(Scope.UserHome::class)
internal class KotlinDslClasspathEntrySnapshotStore(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    private val inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory,
) : Closeable {

    private val cache: PersistentCache = cacheBuilderFactory
        .createCacheBuilder("kotlin-dsl-classpath-snapshots")
        .withDisplayName("Kotlin DSL classpath snapshot cache")
        .withInitialLockMode(FileLockManager.LockMode.OnDemand)
        .open()

    val snapshotsCacheDirectory: Path = cache.baseDir.toPath().resolve("snapshots").also { it.createDirectories() }

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
