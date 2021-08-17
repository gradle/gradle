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

package org.gradle.configurationcache

import org.gradle.api.internal.BuildDefinition
import org.gradle.cache.CacheBuilder
import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CleanupActionFactory
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup
import org.gradle.cache.internal.SingleDepthFilesFinder
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.cache.scopes.BuildTreeScopedCache
import org.gradle.configurationcache.extensions.unsafeLazy
import org.gradle.internal.Factory
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import java.io.File
import java.io.InputStream
import java.io.OutputStream


@ServiceScope(Scopes.BuildTree::class)
internal
class ConfigurationCacheRepository(
    cacheRepository: BuildTreeScopedCache,
    cacheCleanupFactory: CleanupActionFactory,
    private val fileAccessTimeJournal: FileAccessTimeJournal,
    private val fileSystem: FileSystem
) : Stoppable {

    fun useForFingerprintCheck(cacheKey: String, check: (File) -> String?): CheckedFingerprint =
        withBaseCacheDirFor(cacheKey) { cacheDir ->
            val fingerprint = cacheDir.fingerprintFile
            when {
                !fingerprint.isFile -> CheckedFingerprint.NotFound
                else -> {
                    when (val invalidReason = check(fingerprint)) {
                        null -> {
                            markAccessed(fingerprint) // Prevent cleanup before state load
                            CheckedFingerprint.Valid
                        }
                        else -> CheckedFingerprint.Invalid(invalidReason)
                    }
                }
            }
        }

    sealed class CheckedFingerprint {
        object NotFound : CheckedFingerprint()
        object Valid : CheckedFingerprint()
        class Invalid(val reason: String) : CheckedFingerprint()
    }

    fun <T> useForStateLoad(cacheKey: String, stateType: StateType, action: (ConfigurationCacheStateFile) -> T): T {
        return withBaseCacheDirFor(cacheKey) { cacheDir ->
            action(
                ReadableConfigurationCacheStateFile(cacheDir.stateFile(stateType))
            )
        }
    }

    fun useForStore(cacheKey: String, stateType: StateType, action: (Layout) -> Unit) {
        withBaseCacheDirFor(cacheKey) { cacheDir ->
            // TODO GlobalCache require(!cacheDir.isDirectory)
            cacheDir.mkdirs()
            chmod(cacheDir, 448) // octal 0700
            markAccessed(cacheDir)
            val stateFiles = mutableListOf<File>()
            val rootStateFile = WriteableConfigurationCacheStateFile(cacheDir.stateFile(stateType), stateFiles::add)
            val layout = Layout(cacheDir.fingerprintFile, rootStateFile)
            try {
                action(layout)
            } finally {
                (sequenceOf(layout.fingerprint) + stateFiles.asSequence())
                    .filter(File::isFile)
                    .forEach {
                        chmod(it, 384) // octal 0600
                    }
            }
        }
    }

    class Layout(
        val fingerprint: File,
        val state: ConfigurationCacheStateFile
    )

    override fun stop() {
        cache.close()
    }

    private
    inner class ReadableConfigurationCacheStateFile(
        private val file: File
    ) : ConfigurationCacheStateFile {

        override fun outputStream(): OutputStream =
            throw UnsupportedOperationException()

        override fun inputStream(): InputStream =
            file.also(::markAccessed).inputStream()

        override fun stateFileForIncludedBuild(build: BuildDefinition): ConfigurationCacheStateFile =
            ReadableConfigurationCacheStateFile(
                includedBuildFileFor(file, build)
            )
    }

    private
    inner class WriteableConfigurationCacheStateFile(
        private val file: File,
        private val onFileAccess: (File) -> Unit
    ) : ConfigurationCacheStateFile {

        override fun outputStream(): OutputStream =
            file.also(onFileAccess).outputStream()

        override fun inputStream(): InputStream =
            throw UnsupportedOperationException()

        override fun stateFileForIncludedBuild(build: BuildDefinition): ConfigurationCacheStateFile =
            WriteableConfigurationCacheStateFile(
                includedBuildFileFor(file, build),
                onFileAccess
            )
    }

    private
    fun includedBuildFileFor(parentStateFile: File, build: BuildDefinition) =
        parentStateFile.run {
            resolveSibling("$name.${build.name}")
        }

    private
    val cleanupDepth = 1

    private
    val cleanupMaxAgeDays = LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES

    private
    val cache = cacheRepository
        .crossVersionCache("configuration-cache")
        .withDisplayName("Configuration Cache")
        .withOnDemandLockMode() // Don't need to lock anything until we use the caches
        .withLruCacheCleanup(cacheCleanupFactory)
        .open()

    private
    fun CacheBuilder.withOnDemandLockMode() =
        withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.OnDemand))

    private
    fun CacheBuilder.withLruCacheCleanup(cleanupActionFactory: CleanupActionFactory): CacheBuilder =
        withCleanup(
            cleanupActionFactory.create(
                LeastRecentlyUsedCacheCleanup(
                    SingleDepthFilesFinder(cleanupDepth),
                    fileAccessTimeJournal,
                    cleanupMaxAgeDays
                )
            )
        )

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
    fun <T> withBaseCacheDirFor(cacheKey: String, action: (File) -> T): T =
        cache.withFileLock(
            Factory {
                action(cache.baseDirFor(cacheKey))
            }
        )

    private
    fun PersistentCache.baseDirFor(cacheKey: String) =
        baseDir.resolve(cacheKey)

    private
    val File.fingerprintFile
        get() = resolve("fingerprint.bin")

    private
    fun File.stateFile(stateType: StateType) = resolve("${stateType.name.toLowerCase()}.bin")
}
