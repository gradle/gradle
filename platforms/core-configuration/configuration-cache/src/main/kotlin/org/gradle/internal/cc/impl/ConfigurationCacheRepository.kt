/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.cc.impl

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheCleanupStrategyFactory
import org.gradle.cache.CleanupAction
import org.gradle.cache.CleanupProgressMonitor
import org.gradle.cache.FileLockManager
import org.gradle.cache.internal.FilesFinder
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup
import org.gradle.cache.internal.SingleDepthFilesFinder
import org.gradle.cache.internal.streams.DefaultValueStore
import org.gradle.cache.internal.streams.ValueStore
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory
import org.gradle.internal.cc.impl.ConfigurationCacheRepository.ReadableConfigurationCacheStateFile
import org.gradle.internal.cc.impl.ConfigurationCacheStateStore.StateFile
import org.gradle.internal.cc.impl.SupersetIndexLookup.CompatibleEntry
import org.gradle.internal.cc.impl.SupersetIndexLookup.IndexedVariant
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import org.gradle.internal.extensions.stdlib.unsafeLazy
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.time.TimestampSuppliers
import org.gradle.util.Path
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.function.Supplier


@ServiceScope(Scope.BuildSession::class)
internal
class ConfigurationCacheRepository(
    cacheBuilderFactory: BuildTreeScopedCacheBuilderFactory,
    private val cacheCleanupStrategyFactory: CacheCleanupStrategyFactory,
    private val fileAccessTimeJournal: FileAccessTimeJournal,
    private val fileSystem: FileSystem
) : Stoppable {

    fun forKey(cacheKey: String): ConfigurationCacheStateStore {
        return StoreImpl(dirForEntry(cacheKey))
    }

    /**
     * Looks up a stored configuration cache entry compatible with [requestedTasks]
     * under [environmentKey], or returns `null` if no compatible entry exists.
     * <p>
     * Selection is delegated to [SupersetIndexLookup.selectBestMatch]: exact match on the
     * deduplicated requested-task list wins; otherwise the smallest strict-superset
     * variant whose stored task list contains the request as a subsequence
     * (relative order preserved) and whose `taskGraphAccessed` flag is `false`.
     * Among ties of the smallest size, the most recently accessed entry directory
     * wins (LRU via `fileAccessTimeJournal`).
     * <p>
     * If any token in [requestedTasks] starts with `-` (task argument or exclusion
     * like `-x foo`), the lookup short-circuits to `null` and the caller falls back
     * to the exact-match path. This is the v1 scope guard.
     * <p>
     * Acquires the cache file lock for the duration of the lookup. If a chosen
     * entry's directory has been removed out from under the index (e.g. by external
     * cleanup), the stale row is dropped and the index file is rewritten before
     * returning — the next candidate is then considered (self-heal).
     *
     * @param environmentKey identifies the group of entries that share the same
     *     configuration-cache environment
     * @param requestedTasks the current build's literal CLI task list
     * @return the chosen entry's full key and original stored task list, or `null`
     *     if no compatible entry exists
     */
    fun findCompatibleEntry(
        environmentKey: ConfigurationCacheEnvironmentKey,
        requestedTasks: List<String>
    ): CompatibleEntry? {
        // v1 args/exclusion guard: any token starting with '-' falls back to exact-match path.
        if (requestedTasks.any { it.startsWith("-") }) return null

        return withFileLockNullable {
            val indexFile = SupersetIndexFile(indexFileFor(environmentKey))
            val variants = indexFile.read().toMutableList()
            if (variants.isEmpty()) return@withFileLockNullable null

            var dirty = false
            val chosen = pickUsableVariant(variants, requestedTasks) { dirty = true }
            if (dirty) indexFile.write(variants)
            chosen?.let { CompatibleEntry(it.fullKey, it.requestedTasks) }
        }
    }

    /**
     * Repeatedly asks [pickWithTieBreak] for the next-best candidate and drops any
     * that aren't usable for [requestedTasks], returning the first one that is.
     * <p>
     * A candidate is dropped when either:
     *  - its entry directory has been removed out from under the index (stale row —
     *    self-heal: invoke [onStaleDirRemoved] so the caller can rewrite the index);
     *  - pruning it down to the request would leave a retained task pointing at a
     *    dropped task through `mustRunAfter` / finalizer (see
     *    [SupersetIndexLookup.hasDanglingMustRunAfter]). Exact-match candidates skip
     *    the dangle gate — no pruning happens, no edge can dangle. The entry stays
     *    in the index even when skipped here; it remains valid for exact-match
     *    reuse by a future request.
     * <p>
     * Returns `null` when [pickWithTieBreak] yields no further candidate.
     */
    private
    fun pickUsableVariant(
        variants: MutableList<IndexedVariant>,
        requestedTasks: List<String>,
        onStaleDirRemoved: () -> Unit
    ): IndexedVariant? {
        val requestedDistinct = requestedTasks.distinct()
        while (true) {
            val chosen = pickWithTieBreak(variants, requestedTasks) ?: return null
            val staleDir = !dirForEntry(chosen.fullKey).exists()
            val isExactMatch = !staleDir && chosen.requestedTasks == requestedDistinct
            val dangles = !staleDir && !isExactMatch && SupersetIndexLookup.hasDanglingMustRunAfter(
                chosen.mustRunAfterEdges, chosen.requestedTasks, requestedTasks
            )
            if (!staleDir && !dangles) return chosen
            variants.remove(chosen)
            if (staleDir) onStaleDirRemoved()
        }
    }

    /**
     * Records a stored cache entry under [environmentKey] so future builds with
     * overlapping task lists can discover it via [findCompatibleEntry].
     * <p>
     * Upserts by [fullKey]: an existing index row with the same `fullKey` is
     * replaced (it would have been written by a prior store generation under the
     * identical key — the new row supersedes it). The recorded [taskGraphAccessed]
     * flag determines whether the entry will be eligible for strict-superset
     * matches later — `true` makes the entry exact-match only.
     * <p>
     * Mirrors [findCompatibleEntry]'s args-guard: if any token in [requestedTasks]
     * starts with `-`, the entry is not recorded (no harm — the entry just won't
     * be findable as a superset later).
     * <p>
     * Acquires the cache file lock for the upsert + rewrite of the index file.
     *
     * @param environmentKey scope under which to record the entry
     * @param fullKey the stored entry's full cache key (also the entry directory name)
     * @param requestedTasks the requested-task list the entry was stored for
     * @param taskGraphAccessed whether user code observed the task graph during
     *     the originating build; `true` excludes the entry from later strict-superset
     *     matching (see [SupersetIndexLookup.selectBestMatch] rule 2)
     * @param mustRunAfterEdges mustRunAfter / finalizer edges between scheduled
     *     tasks (source identity-path → list of target identity-paths). Used by
     *     [findCompatibleEntry] to reject this entry from strict-superset matches
     *     whose pruning would dangle one of these edges
     */
    fun recordEntry(
        environmentKey: ConfigurationCacheEnvironmentKey,
        fullKey: String,
        requestedTasks: List<String>,
        taskGraphAccessed: Boolean,
        mustRunAfterEdges: Map<String, List<String>> = emptyMap()
    ) {
        if (requestedTasks.any { it.startsWith("-") }) return
        cache.withFileLock(Supplier {
            val indexFile = SupersetIndexFile(indexFileFor(environmentKey))
            val variants = indexFile.read().toMutableList()
            variants.removeIf { it.fullKey == fullKey }
            variants.add(IndexedVariant(fullKey, requestedTasks, taskGraphAccessed, mustRunAfterEdges))
            indexFile.write(variants)
        })
    }

    private
    fun pickWithTieBreak(
        variants: List<IndexedVariant>,
        requested: List<String>
    ): IndexedVariant? {
        val chosen = SupersetIndexLookup.selectBestMatch(variants, requested) ?: return null
        val requestedDistinct = requested.distinct()
        val isExact = chosen.requestedTasks == requestedDistinct
        if (isExact) return chosen
        // For tied smallest-supersets, prefer most-recently-accessed entry. Tied
        // candidates must also host the request as a subsequence so the LRU pick
        // doesn't drift to a same-size variant that orders the requested tasks
        // differently.
        val tieSize = chosen.requestedTasks.size
        return variants
            .filter { v ->
                !v.taskGraphAccessed &&
                    v.requestedTasks.size == tieSize &&
                    SupersetIndexLookup.isSubsequence(requestedDistinct, v.requestedTasks)
            }
            .maxByOrNull { fileAccessTimeJournal.getLastAccessTime(dirForEntry(it.fullKey)) }
            ?: chosen
    }

    private
    fun indexFileFor(environmentKey: ConfigurationCacheEnvironmentKey): File =
        cache.baseDir.resolve(INDEX_DIR_NAME).resolve("${environmentKey.string}.bin")

    interface CleanupContext {
        val eligibleFilesFinder: FilesFinder
        fun dirForEntry(entry: String): File
        fun applyCleanupAction(action: CleanupAction, monitor: CleanupProgressMonitor)
    }

    fun withExclusiveCleanupAccess(action: CleanupContext.() -> Unit) {
        cache.withFileLock {
            action(object : CleanupContext {
                override val eligibleFilesFinder: FilesFinder
                    get() = cleanupEligibleFilesFinder()

                override fun dirForEntry(entry: String): File =
                    this@ConfigurationCacheRepository.dirForEntry(entry)

                override fun applyCleanupAction(action: CleanupAction, monitor: CleanupProgressMonitor) {
                    action.clean(cache, monitor)
                }
            })
        }
    }

    abstract class Layout {
        abstract fun fileForRead(stateType: StateType): ConfigurationCacheStateFile
        abstract fun fileFor(stateType: StateType): ConfigurationCacheStateFile
    }

    private
    class WriteableLayout(
        private val cacheDir: File,
        private val onFileAccess: (File) -> Unit
    ) : Layout() {
        override fun fileForRead(stateType: StateType) =
            cacheDir.readableConfigurationCacheStateFile(stateType, onFileAccess = {}) // only track write-access

        override fun fileFor(stateType: StateType): ConfigurationCacheStateFile =
            WriteableConfigurationCacheStateFile(cacheDir.stateFile(stateType), stateType, onFileAccess)
    }

    internal
    class ReadableLayout(
        private val cacheDir: File,
        private val onFileAccess: (File) -> Unit
    ) : Layout() {
        override fun fileForRead(stateType: StateType) =
            cacheDir.readableConfigurationCacheStateFile(stateType, onFileAccess)

        override fun fileFor(stateType: StateType): ConfigurationCacheStateFile =
            cacheDir.readableConfigurationCacheStateFile(stateType, onFileAccess)
    }

    internal
    class ReadableConfigurationCacheStateFile(
        private val file: File,
        override val stateType: StateType,
        private val onFileAccess: (File) -> Unit
    ) : ConfigurationCacheStateFile {
        override val exists: Boolean
            get() = file.isFile

        override val stateFile: StateFile
            get() = StateFile(stateType, file)

        override fun outputStream(): OutputStream =
            throw UnsupportedOperationException()

        override fun inputStream(): InputStream =
            file.also(onFileAccess).inputStream()

        override fun delete() {
            throw UnsupportedOperationException()
        }

        override fun moveFrom(file: File) {
            throw UnsupportedOperationException()
        }

        override fun stateFileForIncludedBuild(build: BuildDefinition): ConfigurationCacheStateFile =
            ReadableConfigurationCacheStateFile(
                includedBuildFileFor(file, build),
                stateType,
                onFileAccess
            )

        override fun relatedStateFile(path: Path): ConfigurationCacheStateFile =
            ReadableConfigurationCacheStateFile(
                relatedStateFileFor(file, path),
                stateType,
                onFileAccess
            )

        override fun stateFileForSharedObjects(): ConfigurationCacheStateFile =
            ReadableConfigurationCacheStateFile(
                sharedObjectsFileFor(file),
                StateType.WorkShared,
                onFileAccess
            )
    }

    private
    class WriteableConfigurationCacheStateFile(
        private val file: File,
        override val stateType: StateType,
        private val onFileAccess: (File) -> Unit
    ) : ConfigurationCacheStateFile {
        override val exists: Boolean
            get() = false

        override val stateFile: StateFile
            get() = StateFile(stateType, file)

        override fun outputStream(): OutputStream =
            file.also(onFileAccess).outputStream()

        override fun inputStream(): InputStream =
            throw UnsupportedOperationException()

        override fun delete() {
            if (file.exists()) {
                Files.delete(file.toPath())
            }
        }

        override fun moveFrom(file: File) {
            Files.move(file.toPath(), this.file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        }

        override fun stateFileForIncludedBuild(build: BuildDefinition): ConfigurationCacheStateFile =
            WriteableConfigurationCacheStateFile(
                includedBuildFileFor(file, build),
                stateType,
                onFileAccess
            )

        override fun relatedStateFile(path: Path): ConfigurationCacheStateFile =
            WriteableConfigurationCacheStateFile(
                relatedStateFileFor(file, path),
                stateType,
                onFileAccess
            )

        override fun stateFileForSharedObjects(): ConfigurationCacheStateFile =
            WriteableConfigurationCacheStateFile(
                sharedObjectsFileFor(file),
                StateType.WorkShared,
                onFileAccess
            )
    }

    private
    inner class StoreImpl(
        private val baseDir: File
    ) : ConfigurationCacheStateStore {
        override fun assignSpoolFile(stateType: StateType): StateFile {
            Files.createDirectories(baseDir.toPath())
            val tempFile = Files.createTempFile(baseDir.toPath(), stateType.fileBaseName, ".tmp")
            return StateFile(stateType, tempFile.toFile())
        }

        override fun <T> createValueStore(stateType: StateType, writer: ValueStore.Writer<T>, reader: ValueStore.Reader<T>): ValueStore<T> {
            return DefaultValueStore(baseDir, stateType.fileBaseName, writer, reader)
        }

        override fun <T : Any> useForStateLoad(stateType: StateType, action: (ConfigurationCacheStateFile) -> T): ConfigurationCacheStateStore.StateAccessResult<T> {
            return useForStateLoad { action(fileFor(stateType)) }
        }

        override fun <T : Any> useForStateLoad(action: Layout.() -> T): ConfigurationCacheStateStore.StateAccessResult<T> {
            return withExclusiveAccessToCache(baseDir) { cacheDir ->
                markAccessed(cacheDir)
                // this needs to be thread-safe as we may have multiple adding threads
                val stateFiles = Collections.synchronizedList(mutableListOf<File>())
                val actionResult = action(ReadableLayout(cacheDir, stateFiles::add))
                ConfigurationCacheStateStore.StateAccessResult(actionResult, stateFiles.toList())
            }
        }

        override fun <T> useForStore(action: Layout.() -> T): ConfigurationCacheStateStore.StateAccessResult<T> =
            withExclusiveAccessToCache(baseDir) { cacheDir ->
                if (!cacheDir.isDirectory) {
                    Files.createDirectories(cacheDir.toPath())
                    chmod(cacheDir, 448) // octal 0700
                }
                markAccessed(cacheDir)
                // this needs to be thread-safe as we may have multiple adding threads
                val stateFiles = Collections.synchronizedList(mutableListOf<File>())
                val layout = WriteableLayout(cacheDir, stateFiles::add)
                val actionResult = try {
                    action(layout)
                } finally {
                    stateFiles.asSequence()
                        .filter(File::isFile)
                        .forEach {
                            chmod(it, 384) // octal 0600
                        }
                }

                ConfigurationCacheStateStore.StateAccessResult(actionResult, stateFiles.toList())
            }
    }

    private
    val cleanupDepth = 1

    private
    val cleanupMaxAgeDays = CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES

    private
    val cache = cacheBuilderFactory
        .createCrossVersionCacheBuilder("configuration-cache")
        .withDisplayName("Configuration Cache")
        .withInitialLockMode(FileLockManager.LockMode.OnDemand) // Don't need to lock anything until we use the caches
        .withLruCacheCleanup()
        .open()

    override fun stop() {
        cache.close()
    }

    private
    fun CacheBuilder.withLruCacheCleanup(): CacheBuilder =
        withCleanupStrategy(
            cacheCleanupStrategyFactory.daily(
                LeastRecentlyUsedCacheCleanup(
                    cleanupEligibleFilesFinder(),
                    fileAccessTimeJournal,
                    TimestampSuppliers.daysAgo(cleanupMaxAgeDays)
                )
            )
        )

    private
    fun cleanupEligibleFilesFinder(): FilesFinder {
        val delegate = SingleDepthFilesFinder(cleanupDepth)
        return object : FilesFinder {
            override fun find(baseDir: File, filter: java.io.FileFilter) =
                delegate.find(baseDir, filter).filter { it.name != INDEX_DIR_NAME }
        }
    }

    private
    val fileAccessTracker by unsafeLazy {
        SingleDepthFileAccessTracker(fileAccessTimeJournal, cache.baseDir, cleanupDepth)
    }

    private
    fun chmod(file: File, mode: Int) {
        fileSystem.chmod(file, mode)
    }

    private
    fun markAccessed(stateFile: File) {
        fileAccessTracker.markAccessed(stateFile)
    }

    private
    fun <T : Any> withExclusiveAccessToCache(baseDir: File, action: (File) -> T): T =
        cache.withFileLock(
            Supplier {
                action(baseDir)
            }
        )

    @Suppress("UNCHECKED_CAST")
    private
    fun <T> withFileLockNullable(action: () -> T?): T? {
        // Wrap result in an array (non-nullable) to avoid Runnable/Supplier ambiguity
        // that occurs when Supplier<T?> is used with a nullable T.
        val box = cache.withFileLock(Supplier { arrayOfNulls<Any>(1).also { it[0] = action() } })
        return box[0] as T?
    }

    private
    fun dirForEntry(cacheKey: String) =
        cache.baseDir.resolve(cacheKey)

    private companion object {
        private const val INDEX_DIR_NAME = "index"
    }
}


@VisibleForTesting
internal
fun File.readableConfigurationCacheStateFile(stateType: StateType, onFileAccess: (File) -> Unit) =
    ReadableConfigurationCacheStateFile(stateFile(stateType), stateType, onFileAccess)


private
fun File.stateFile(stateType: StateType) =
    resolve("${stateType.fileBaseName}.bin")


private
val StateType.fileBaseName: String
    get() = name.toDefaultLowerCase()


private
fun includedBuildFileFor(parentStateFile: File, build: BuildDefinition) =
    parentStateFile.run {
        resolveSibling("$name.${build.name}")
    }


private
fun relatedStateFileFor(parentStateFile: File, path: Path) =
    parentStateFile.run {
        resolveSibling("${path.segments().joinToString("_", if (path.isAbsolute) "_" else "")}.$name")
    }


private
fun sharedObjectsFileFor(parentStateFile: File) =
    parentStateFile.run {
        resolveSibling(".globals.$name")
    }
