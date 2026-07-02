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

import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.cache.CacheCleanupStrategy
import org.gradle.cache.CacheCleanupStrategyFactory
import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CompositeCleanupAction
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup
import org.gradle.cache.internal.SingleDepthFilesFinder
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories


/**
 * Owns the [PersistentCache] backing [KotlinDslClasspathEntrySnapshotCache].
 *
 * Layout under `<gradleUserHome>/caches/<gradleVersion>/kotlin-dsl-classpath-snapshots/`:
 *  - `snapshots/<contentHash>.snapshot` - content-addressed BTA classpath snapshots (incremental compilation).
 *  - `snapshots/<contentHash>.abi`      - content-addressed ABI hashes (compile avoidance).
 *
 * Opened with [FileLockManager.LockMode.OnDemand]: a coarse lock taken only for cleanup; the entries
 * need none, being immutable and content-addressed and published lock-free via atomic rename.
 *
 * Cleanup is least-recently-used, scoped to `snapshots/` so the cache metadata at the root is left
 * alone: [fileAccessTracker] touches each file on use, and files unused past the retention period are
 * deleted. Hard-deleting immediately with no soft-delete grace window is safe here — unlike the
 * mutable [KotlinDslIncrementalCompilationStore], entries are immutable and regenerable, and a
 * snapshot reclaimed mid-build is handled by the compiler's full-compile fallback. [close] is invoked
 * by the service registry on shutdown.
 */
@ServiceScope(Scope.UserHome::class)
internal class KotlinDslClasspathEntrySnapshotStore(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    fileAccessTimeJournal: FileAccessTimeJournal,
    cacheConfigurations: CacheConfigurationsInternal,
    cacheCleanupStrategyFactory: CacheCleanupStrategyFactory,
) : Closeable {

    // Resolved without opening the cache, so the cleanup strategy can be scoped to it before open().
    private val snapshotsDir: File = cacheBuilderFactory.baseDirForCache(CACHE_KEY).resolve("snapshots")

    val snapshotsCacheDirectory: Path = snapshotsDir.toPath().also { it.createDirectories() }

    private val cache: PersistentCache = cacheBuilderFactory
        .createCacheBuilder(CACHE_KEY)
        .withDisplayName("Kotlin DSL classpath snapshot cache")
        .withInitialLockMode(FileLockManager.LockMode.OnDemand)
        .withCleanupStrategy(cleanupStrategy(fileAccessTimeJournal, cacheConfigurations, cacheCleanupStrategyFactory))
        .open()

    val fileAccessTracker: FileAccessTracker = SingleDepthFileAccessTracker(fileAccessTimeJournal, snapshotsDir, ENTRY_DEPTH)

    private fun cleanupStrategy(
        fileAccessTimeJournal: FileAccessTimeJournal,
        cacheConfigurations: CacheConfigurationsInternal,
        cacheCleanupStrategyFactory: CacheCleanupStrategyFactory,
    ): CacheCleanupStrategy =
        cacheCleanupStrategyFactory.create(
            CompositeCleanupAction.builder()
                .add(
                    snapshotsDir,
                    LeastRecentlyUsedCacheCleanup(
                        SingleDepthFilesFinder(ENTRY_DEPTH),
                        fileAccessTimeJournal,
                        cacheConfigurations.createdResources.entryRetentionTimestampSupplier
                    )
                )
                .build(),
            cacheConfigurations.cleanupFrequency::get
        )

    override fun close() {
        cache.close()
    }
}

private const val CACHE_KEY = "kotlin-dsl-classpath-snapshots"

private const val ENTRY_DEPTH = 1
