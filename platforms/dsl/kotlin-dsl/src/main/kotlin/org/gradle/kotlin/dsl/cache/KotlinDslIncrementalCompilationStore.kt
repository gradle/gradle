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
import org.gradle.cache.FineGrainedCacheCleanupStrategyFactory
import org.gradle.cache.FineGrainedMarkAndSweepCacheCleanupStrategy.FineGrainedCacheEntrySoftDeleter
import org.gradle.cache.FineGrainedPersistentCache
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Closeable
import java.io.File
import java.time.Instant


/**
 * Owns the [FineGrainedPersistentCache] backing [KotlinDslIncrementalCompilationCache], rooted at
 * `<gradleUserHome>/caches/<gradleVersion>/kotlin-dsl/ic/`. Holds only the mutable, per-script
 * incremental-compilation state.
 *
 * The cache is opened lazily, on the first compilation: builds whose scripts are all up to date
 * (compile avoidance) skip the open — unless cleanup is due, in which case the cache is opened
 * eagerly (at most once per cleanup interval): cleanup only ever visits open caches, so entries
 * of no-longer-compiled scripts must not wait for some other compilation to reclaim them.
 *
 * Per-script entries (`<scriptHash>/`, one cache key each) are reclaimed by the mark-and-sweep LRU
 * cleanup wired here. The cache touches each entry on use (see
 * [KotlinDslIncrementalCompilationCache.withScriptState]) via [fileAccessTracker], and clears
 * its soft-delete marker through [softDeleter] so entries in active use survive; the cleanup itself
 * runs on [close].
 */
@ServiceScope(Scope.UserHome::class)
internal class KotlinDslIncrementalCompilationStore(
    private val cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    fileAccessTimeJournal: FileAccessTimeJournal,
    private val cacheConfigurations: CacheConfigurationsInternal,
    cacheCleanupStrategyFactory: FineGrainedCacheCleanupStrategyFactory,
) : Closeable {

    private val cleanupStrategy = cacheCleanupStrategyFactory.markAndSweepCleanupStrategy(
        cacheConfigurations.createdResources.entryRetentionTimestampSupplier,
        cacheConfigurations.cleanupFrequency::get
    )

    // Resolved without opening the cache; entry paths derive from it eagerly and cheaply.
    val baseDir: File = cacheBuilderFactory.baseDirForCache(CACHE_KEY)

    private val lazyCache = lazy {
        cacheBuilderFactory
            .createFineGrainedCacheBuilder(CACHE_KEY)
            .withDisplayName("Kotlin DSL incremental compilation cache")
            .withCleanupStrategy(cleanupStrategy)
            .open()
    }

    val cache: FineGrainedPersistentCache by lazyCache

    val fileAccessTracker: FileAccessTracker = SingleDepthFileAccessTracker(fileAccessTimeJournal, baseDir, 1)

    val softDeleter: FineGrainedCacheEntrySoftDeleter by lazy { cleanupStrategy.getSoftDeleter(cache) }

    init {
        if (cleanupIsDue()) {
            lazyCache.value
        }
    }

    // Mirrors the due check in DefaultCacheCleanupExecutor: a missing gc.properties means the cache
    // was never used. The cleanup itself re-checks against the frequency configured by then.
    private fun cleanupIsDue(): Boolean {
        val gcFile = baseDir.resolve(".internal/gc.properties")
        return gcFile.isFile && cacheConfigurations.cleanupFrequency.get().requiresCleanup(Instant.ofEpochMilli(gcFile.lastModified()))
    }

    override fun close() {
        if (lazyCache.isInitialized()) {
            lazyCache.value.close()
        }
    }
}

private const val CACHE_KEY = "kotlin-dsl/ic"
