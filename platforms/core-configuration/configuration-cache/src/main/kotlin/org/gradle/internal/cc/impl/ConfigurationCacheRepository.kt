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
                // TODO GlobalCache require(!cacheDir.isDirectory)
                Files.createDirectories(cacheDir.toPath())
                chmod(cacheDir, 448) // octal 0700
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
    fun cleanupEligibleFilesFinder() =
        SingleDepthFilesFinder(cleanupDepth)

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

    private
    fun dirForEntry(cacheKey: String) =
        cache.baseDir.resolve(cacheKey)
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
