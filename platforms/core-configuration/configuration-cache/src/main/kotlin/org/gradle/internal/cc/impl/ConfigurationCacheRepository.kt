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
import org.gradle.api.logging.Logging
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
import org.gradle.internal.cc.impl.SupersetIndexLookup.SupersetIndexEntry
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
     * Looks up a stored configuration cache entry compatible with [requestedCliTokens]
     * under [environmentKey], or returns `null` if no compatible entry exists. Acquires
     * the cache file lock for the duration of the lookup.
     * <p>
     * Selection is delegated to [SupersetIndexLookup.selectBestMatch]: exact match wins;
     * otherwise the smallest strict-superset entry whose stored task list contains the
     * request as a subsequence and whose `taskGraphAccessed` flag is `false`. Ties are
     * broken by LRU on the entry directory's last-access time.
     * <p>
     * Self-heals when a chosen entry's directory has been removed out from under the
     * index: the stale row is dropped, the index file is rewritten, and the next
     * candidate is considered.
     */
    fun findCacheEntry(
        environmentKey: ConfigurationCacheEnvironmentKey,
        requestedCliTokens: List<String>
    ): CompatibleEntry? {
        // Project-defaults invocations resolve at configuration time, not lookup time.
        // Also keeps the unsafe shape of `selectBestMatch` (empty request matches every
        // entry as a subsequence) unreachable from this entry point.
        if (requestedCliTokens.isEmpty()) {
            logger.debug("Superset index lookup skipped: empty requested-task list (project defaults are unknown at lookup time).")
            return null
        }
        // Tokens starting with '-' (task arguments / exclusions) affect scheduling
        // beyond what the index models. Fall back to the exact-match path.
        if (requestedCliTokens.any { it.startsWith("-") }) {
            logger.debug("Superset index lookup skipped: request {} contains a '-'-prefixed token (task argument / exclusion).", requestedCliTokens)
            return null
        }

        return withFileLockNullable {
            val indexFile = SupersetIndexFile(indexFileFor(environmentKey))
            val onDiskEntries = indexFile.read().toMutableList()
            if (onDiskEntries.isEmpty()) return@withFileLockNullable null

            val loopEntries = onDiskEntries.toMutableList()
            val staleDirsEvicted = mutableListOf<SupersetIndexEntry>()
            val chosen = pickUsableEntry(loopEntries, requestedCliTokens) { staleEntry ->
                staleDirsEvicted.add(staleEntry)
            }
            if (staleDirsEvicted.isNotEmpty()) {
                onDiskEntries.removeAll(staleDirsEvicted)
                indexFile.write(onDiskEntries)
            }
            chosen?.let {
                CompatibleEntry(it.fullKey, it.cliTokens, it.entryTaskIdentityPaths)
            }
        }
    }

    /**
     * Repeatedly asks [pickWithTieBreak] for the next-best candidate and drops any not
     * usable for [requestedCliTokens]. A candidate fails when (and is not an exact match):
     *  - its entry directory is missing on disk — reported via [onStaleDirRemoved] for
     *    persistent eviction (self-heal);
     *  - pruning would leave a retained task pointing at a dropped task through
     *    `mustRunAfter` / finalizer (see [SupersetIndexLookup.hasDanglingMustRunAfter]);
     *  - pruning would drop a task with a `@Destroys`-annotated property
     *    (see [SupersetIndexLookup.hasSideEffectingDroppedTask]).
     * <p>
     * Gate-rejected (non-stale) candidates are removed from the in-memory working list
     * only; they remain in the persisted index for future exact-match reuse.
     */
    private
    fun pickUsableEntry(
        entries: MutableList<SupersetIndexEntry>,
        requestedCliTokens: List<String>,
        onStaleDirRemoved: (SupersetIndexEntry) -> Unit
    ): SupersetIndexEntry? {
        val requestedDistinct = requestedCliTokens.distinct()
        while (true) {
            val chosen = pickWithTieBreak(entries, requestedCliTokens) ?: return null
            val staleDir = !dirForEntry(chosen.fullKey).exists()
            val isExactMatch = !staleDir && chosen.cliTokens == requestedDistinct
            val dropped: Set<String> = when {
                staleDir || isExactMatch -> emptySet()
                else -> droppedIdentityPaths(chosen, requestedDistinct.toSet())
            }
            val dangles = dropped.isNotEmpty() && SupersetIndexLookup.hasDanglingMustRunAfter(
                chosen.mustRunAfterEdges, dropped
            )
            val sideEffects = dropped.isNotEmpty() && !dangles && SupersetIndexLookup.hasSideEffectingDroppedTask(
                chosen.sideEffectingTaskIdentityPaths, dropped
            )
            if (!staleDir && !dangles && !sideEffects) return chosen
            if (logger.isDebugEnabled) {
                logger.debug(
                    "Superset candidate {} rejected for request {}: {}.",
                    chosen.fullKey,
                    requestedCliTokens,
                    when {
                        staleDir -> "entry directory missing on disk"
                        dangles -> "pruning would dangle a mustRunAfter/finalizer edge"
                        sideEffects -> "pruning would drop a task with a @Destroys-annotated property: ${chosen.sideEffectingTaskIdentityPaths.intersect(dropped)}"
                        else -> "unexpected"
                    }
                )
            }
            entries.remove(chosen)
            if (staleDir) onStaleDirRemoved(chosen)
        }
    }

    /**
     * Maps the candidate's CLI tokens that aren't in [requestedCli] to their paired
     * identity paths. Relies on [SupersetIndexEntry.hasOneToOneCliMapping] being true —
     * enforced by [SupersetIndexLookup.selectBestMatch] for non-exact selections.
     */
    private
    fun droppedIdentityPaths(entry: SupersetIndexEntry, requestedCli: Set<String>): Set<String> =
        entry.cliTokens
            .asSequence()
            .withIndex()
            .filter { (_, token) -> token !in requestedCli }
            .map { (i, _) -> entry.entryTaskIdentityPaths[i] }
            .toSet()

    /**
     * Records a stored cache entry under [environmentKey] so future builds can discover
     * it via [findCacheEntry]. Upserts by [fullKey] — an existing row is replaced.
     * Tokens starting with `-` (task arguments / exclusions) are skipped: the entry
     * is stored to disk but not findable as a superset later. Acquires the cache file lock.
     *
     * @see SupersetIndexEntry for the meaning of [taskGraphAccessed], [mustRunAfterEdges],
     *     and [sideEffectingTaskIdentityPaths]
     */
    fun recordEnvironmentKeyForCacheKey(
        environmentKey: ConfigurationCacheEnvironmentKey,
        fullKey: String,
        cliTokens: List<String>,
        entryTaskIdentityPaths: List<String>,
        taskGraphAccessed: Boolean,
        mustRunAfterEdges: Map<String, List<String>> = emptyMap(),
        sideEffectingTaskIdentityPaths: Set<String> = emptySet()
    ) {
        if (cliTokens.any { it.startsWith("-") }) {
            logger.debug("Superset index recording skipped for {}: cliTokens {} contain a '-'-prefixed token; entry will not be findable as a superset.", fullKey, cliTokens)
            return
        }
        cache.withFileLock(Supplier {
            val indexFile = SupersetIndexFile(indexFileFor(environmentKey))
            val entries = indexFile.read().toMutableList()
            entries.removeIf { it.fullKey == fullKey }
            entries.add(SupersetIndexEntry(
                fullKey, cliTokens, entryTaskIdentityPaths, taskGraphAccessed,
                mustRunAfterEdges, sideEffectingTaskIdentityPaths
            ))
            indexFile.write(entries)
        })
    }

    /**
     * Wraps [SupersetIndexLookup.selectBestMatch] with an LRU tie-break on entry-directory
     * last-access time among equal-sized strict-superset candidates. The eligibility
     * re-filter mirrors `selectBestMatch`'s strict-superset filters so the LRU pick stays
     * safe to prune. Exact-match candidates skip this step.
     */
    private
    fun pickWithTieBreak(
        entries: List<SupersetIndexEntry>,
        requested: List<String>
    ): SupersetIndexEntry? {
        val chosen = SupersetIndexLookup.selectBestMatch(entries, requested) ?: return null
        val requestedDistinct = requested.distinct()
        val isExact = chosen.cliTokens == requestedDistinct
        if (isExact) return chosen
        val tieSize = chosen.cliTokens.size
        return entries
            .filter { e ->
                !e.taskGraphAccessed &&
                    e.hasOneToOneCliMapping &&
                    e.cliTokens.size == tieSize &&
                    SupersetIndexLookup.isSubsequence(requestedDistinct, e.cliTokens)
            }
            .maxByOrNull { fileAccessTimeJournal.getLastAccessTime(dirForEntry(it.fullKey)) }
            ?: chosen
    }

    private
    fun indexFileFor(environmentKey: ConfigurationCacheEnvironmentKey): File =
        cache.baseDir.resolve(SUPERSET_INDEX_DIR_NAME).resolve("${environmentKey.string}.bin")

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
                delegate.find(baseDir, filter).filter { it.name != SUPERSET_INDEX_DIR_NAME }
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
        private val logger = Logging.getLogger(ConfigurationCacheRepository::class.java)
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
