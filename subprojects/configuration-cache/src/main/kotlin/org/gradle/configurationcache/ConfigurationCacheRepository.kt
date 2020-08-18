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

import org.gradle.cache.CacheBuilder
import org.gradle.cache.CacheRepository
import org.gradle.cache.FileLockManager
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.CleanupActionFactory
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup
import org.gradle.cache.internal.SingleDepthFilesFinder
import org.gradle.cache.internal.filelock.LockOptionsBuilder
import org.gradle.configurationcache.extensions.unsafeLazy
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.Factory
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import java.io.File


internal
class ConfigurationCacheRepository(
    private val cacheRepository: CacheRepository,
    private val cacheCleanupFactory: CleanupActionFactory,
    private val fileAccessTimeJournal: FileAccessTimeJournal,
    private val startParameter: ConfigurationCacheStartParameter,
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
                            fileAccessTracker.markAccessed(fingerprint) // Prevent cleanup before state load
                            CheckedFingerprint.Valid
                        }
                        else -> CheckedFingerprint.Invalid(invalidReason)
                    }
                }
            }
        }

    fun useForStateLoad(cacheKey: String, action: (File) -> Unit) {
        withBaseCacheDirFor(cacheKey) { cacheDir ->
            val stateFile = cacheDir.stateFile
            fileAccessTracker.markAccessed(stateFile)
            action(stateFile)
        }
    }

    class Layout(val fingerprint: File, val state: File)

    fun useForStore(cacheKey: String, action: (Layout) -> Unit) {
        withBaseCacheDirFor(cacheKey) { cacheDir ->
            // TODO GlobalCache require(!cacheDir.isDirectory)
            cacheDir.mkdirs()
            fileSystem.chmod(cacheDir, 448) // octal 0700
            fileAccessTracker.markAccessed(cacheDir)
            val layout = Layout(cacheDir.fingerprintFile, cacheDir.stateFile)
            try {
                action(layout)
            } finally {
                sequenceOf(layout.state, layout.fingerprint)
                    .filter(File::isFile)
                    .forEach { fileSystem.chmod(it, 384) } // octal 0600
            }
        }
    }

    private
    val cacheRootDir
        get() = startParameter.rootDirectory.resolve(".gradle/configuration-cache")

    private
    val cleanupDepth = 1

    private
    val cleanupMaxAgeDays = LeastRecentlyUsedCacheCleanup.DEFAULT_MAX_AGE_IN_DAYS_FOR_RECREATABLE_CACHE_ENTRIES

    private
    val cache by unsafeLazy {
        cacheRepository.configurationCache()
            .withOnDemandLockMode() // Don't need to lock anything until we use the caches
            .withLruCacheCleanup()
            .open()
    }

    private
    fun CacheRepository.configurationCache() =
        cache(cacheRootDir).withDisplayName("Configuration Cache")

    private
    fun CacheBuilder.withOnDemandLockMode() =
        withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.OnDemand))

    private
    fun CacheBuilder.withLruCacheCleanup(): CacheBuilder =
        withCleanup(
            cacheCleanupFactory.create(
                LeastRecentlyUsedCacheCleanup(
                    SingleDepthFilesFinder(cleanupDepth),
                    fileAccessTimeJournal,
                    cleanupMaxAgeDays
                )
            )
        )

    override fun stop() {
        cache.close()
    }

    private
    val fileAccessTracker by unsafeLazy {
        SingleDepthFileAccessTracker(fileAccessTimeJournal, cacheRootDir, cleanupDepth)
    }

    sealed class CheckedFingerprint {
        object NotFound : CheckedFingerprint()
        object Valid : CheckedFingerprint()
        class Invalid(val reason: String) : CheckedFingerprint()
    }

    private
    fun <T> withBaseCacheDirFor(cacheKey: String, action: (File) -> T): T =
        cache.withFileLock(Factory {
            action(cache.baseDirFor(cacheKey))
        })

    private
    fun PersistentCache.baseDirFor(cacheKey: String) =
        baseDir.resolve(cacheKey)

    private
    val File.fingerprintFile
        get() = resolve("fingerprint.bin")

    private
    val File.stateFile
        get() = resolve("state.bin")
}
