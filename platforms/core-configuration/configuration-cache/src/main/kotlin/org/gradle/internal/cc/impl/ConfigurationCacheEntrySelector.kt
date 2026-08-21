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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.properties.GradlePropertiesController
import org.gradle.internal.cc.base.logger
import org.gradle.internal.cc.base.serialize.HostServiceProvider
import org.gradle.internal.cc.base.serialize.service
import org.gradle.internal.cc.impl.fingerprint.ClassLoaderScopesFingerprintController
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.internal.cc.impl.fingerprint.InvalidationReason
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.cc.impl.serialize.FingerprintDeserializationException
import org.gradle.internal.cc.operations.EntrySearchResult
import org.gradle.internal.cc.operations.withFingerprintCheckOperations
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.util.Path
import java.io.File
import java.util.Properties


/**
 * Picks the cache entry to reuse, if any, by checking the stored fingerprint of each candidate.
 */
internal class ConfigurationCacheEntrySelector(
    private val startParameter: ConfigurationCacheStartParameter,
    private val cacheRepository: ConfigurationCacheRepository,
    private val candidateEntries: ConfigurationCacheCandidateEntries,
    private val cacheIO: ConfigurationCacheBuildTreeIO,
    private val cacheFingerprintController: ConfigurationCacheFingerprintController,
    private val classLoaderScopes: ClassLoaderScopesFingerprintController,
    private val virtualFileSystem: BuildLifecycleAwareVirtualFileSystem,
    private val buildOperationRunner: BuildOperationRunner,
    private val host: HostServiceProvider
) {
    fun selectEntry(): CheckedFingerprint = buildOperationRunner.withFingerprintCheckOperations {
        val searchResult = candidateEntries.searchForValidEntry(::checkCandidate)
        val checkedFingerprint = searchResult.checkedFingerprint
        if (checkedFingerprint is CheckedFingerprint.Valid) {
            candidateEntries.markMostRecentlyUsed(checkedFingerprint.entryId)
        }
        searchResult
    }

    private
    fun checkCandidate(candidateEntry: CandidateEntry): EntrySearchResult {
        // checking a single fingerprint
        val entryStore = cacheRepository.forKey(candidateEntry.id)
        return entryStore.useForStateLoad {
            checkedFingerprint(candidateEntry)
        }.value
    }

    private
    fun ConfigurationCacheRepository.Layout.checkedFingerprint(candidateEntry: CandidateEntry): EntrySearchResult {
        val entryDetails = cacheIO.readCacheEntryDetailsFrom(fileFor(StateType.Entry))
            ?: return EntrySearchResult(null, CheckedFingerprint.NotFound)
        // TODO:configuration-cache read only rootDirs at this point
        return EntrySearchResult(
            entryDetails.buildInvocationScopeId,
            checkFingerprint(candidateEntry, entryDetails.rootDirs)
        )
    }

    private
    fun ConfigurationCacheRepository.Layout.checkFingerprint(candidateEntry: CandidateEntry, rootDirs: List<File>): CheckedFingerprint {
        if (rootDirs.isNotEmpty() && startParameter.buildTreeRootDirectory !in rootDirs) {
            return CheckedFingerprint.Invalid(
                buildPath(),
                StructuredMessage.build {
                    text("the location of the build has changed from ")
                    reference(rootDirs.first().path)
                    text(" to ")
                    reference(startParameter.buildTreeRootDirectory.path)
                }
            )
        }

        // Register all included build root directories as watchable hierarchies,
        // so we can load the fingerprint for build scripts and other files from included builds
        // without violating file system invariants.
        registerWatchableBuildDirectories(rootDirs)

        val classLoaderScopesInvalidationReason = checkClassLoaderScopes()
        if (classLoaderScopesInvalidationReason != null) {
            return CheckedFingerprint.Invalid(buildPath(), classLoaderScopesInvalidationReason)
        }

        val systemPropertiesSnapshot = System.getProperties().clone()
        val result = runCatching { checkFingerprintAgainstLoadedProperties(candidateEntry) }
        if (result.getOrNull()?.isFullReuse != true) {
            // Restore system properties and force Gradle properties to be reloaded
            // so the Gradle properties files along with any Gradle property defining
            // system properties and environment variables are added to the new fingerprint.
            rollbackProperties(systemPropertiesSnapshot.uncheckedCast())
        }
        return result.getOrThrow()
    }

    private
    fun ConfigurationCacheRepository.Layout.checkClassLoaderScopes(): InvalidationReason? =
        fileFor(StateType.ClassLoaderScopes).let { stateFile ->
            classLoaderScopes.checkClassLoaderScopes {
                cacheIO.decoderFor(stateFile.stateType, stateFile::inputStream)
            }
        }

    private
    fun ConfigurationCacheRepository.Layout.checkFingerprintAgainstLoadedProperties(
        candidateEntry: CandidateEntry
    ): CheckedFingerprint =
        try {
            when (val invalidationReason = checkBuildScopedFingerprint(fileFor(StateType.BuildFingerprint))) {
                null -> {
                    // Build inputs are up-to-date, check project specific inputs
                    CheckedFingerprint.Valid(
                        candidateEntry.id,
                        checkProjectScopedFingerprint(fileFor(StateType.ProjectFingerprint))
                    )
                }

                else -> CheckedFingerprint.Invalid(buildPath(), invalidationReason)
            }
        } catch (e: FingerprintDeserializationException) {
            logger.info("Configuration cache entry discarded because a fingerprint value could not be loaded", e)
            CheckedFingerprint.Invalid(buildPath(), e.reason)
        }

    private
    fun checkBuildScopedFingerprint(fingerprintFile: ConfigurationCacheStateFile) =
        readFingerprintFile(fingerprintFile) { host ->
            cacheFingerprintController.run {
                checkBuildScopedFingerprint(host)
            }
        }

    private
    fun checkProjectScopedFingerprint(fingerprintFile: ConfigurationCacheStateFile) =
        readFingerprintFile(fingerprintFile) { host ->
            cacheFingerprintController.run {
                checkProjectScopedFingerprint(host)
            }
        }

    private
    fun <T> readFingerprintFile(
        fingerprintFile: ConfigurationCacheStateFile,
        action: suspend ReadContext.(ConfigurationCacheFingerprintController.Host) -> T
    ): T {
        val decoder = cacheIO.decoderFor(fingerprintFile.stateType, fingerprintFile::inputStream)
        return cacheIO.readFingerprintFrom(fingerprintFile.stateFile.name, decoder, action)
    }

    private
    fun buildPath(): Path =
        host.service<GradleInternal>().identityPath

    private
    fun registerWatchableBuildDirectories(buildDirs: Iterable<File>) {
        buildDirs.forEach(virtualFileSystem::registerWatchableHierarchy)
    }

    private
    val gradlePropertiesController: GradlePropertiesController
        get() = host.service()

    private
    fun rollbackProperties(systemPropertiesSnapshot: Properties) {
        gradlePropertiesController.unloadAll()
        System.setProperties(systemPropertiesSnapshot)
    }
}
