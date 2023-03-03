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

package org.gradle.configurationcache.fingerprint

import org.gradle.api.Describable
import org.gradle.api.internal.GeneratedSubclasses.unpackType
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.configurationcache.CheckedFingerprint
import org.gradle.configurationcache.extensions.fileSystemEntryType
import org.gradle.configurationcache.extensions.filterKeysByPrefix
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.logger
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.util.NumberUtil.ordinal
import org.gradle.util.Path
import java.io.File
import java.net.URI
import java.util.function.Consumer


internal
typealias InvalidationReason = String


internal
class ConfigurationCacheFingerprintChecker(private val host: Host) {

    interface Host {
        val gradleUserHomeDir: File
        val allInitScripts: List<File>
        val startParameterProperties: Map<String, Any?>
        val buildStartTime: Long
        val invalidateCoupledProjects: Boolean
        val instrumentationAgentUsed: Boolean
        fun gradleProperty(propertyName: String): String?
        fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode
        fun hashCodeOf(file: File): HashCode?
        fun hashCodeOfDirectoryContent(file: File): HashCode?
        fun displayNameOf(fileOrDirectory: File): String
        fun instantiateValueSourceOf(obtainedValue: ObtainedValue): ValueSource<Any, ValueSourceParameters>
        fun isRemoteScriptUpToDate(uri: URI): Boolean
    }

    suspend fun ReadContext.checkBuildScopedFingerprint(): CheckedFingerprint {
        // TODO: log some debug info
        while (true) {
            when (val input = read()) {
                null -> break
                is ConfigurationCacheFingerprint -> {
                    // An input that is not specific to a project. If it is out-of-date, then invalidate the whole cache entry and skip any further checks
                    val reason = check(input)
                    if (reason != null) {
                        return CheckedFingerprint.EntryInvalid(reason)
                    }
                }
                else -> throw IllegalStateException("Unexpected configuration cache fingerprint: $input")
            }
        }
        return CheckedFingerprint.Valid
    }

    suspend fun ReadContext.checkProjectScopedFingerprint(): CheckedFingerprint {
        // TODO: log some debug info
        var firstReason: InvalidationReason? = null
        val projects = mutableMapOf<Path, ProjectInvalidationState>()
        while (true) {
            when (val input = read()) {
                null -> break
                is ProjectSpecificFingerprint.ProjectFingerprint -> input.run {
                    // An input that is specific to a project. If it is out-of-date, then invalidate that project's values and continue checking values
                    // Don't check a value for a project that is already out-of-date
                    val state = projects.entryFor(input.projectPath)
                    if (!state.isInvalid) {
                        val reason = check(input.value)
                        if (reason != null) {
                            if (firstReason == null) {
                                firstReason = reason
                            }
                            state.invalidate()
                        }
                    }
                }
                is ProjectSpecificFingerprint.ProjectDependency -> {
                    val consumer = projects.entryFor(input.consumingProject)
                    val target = projects.entryFor(input.targetProject)
                    target.consumedBy(consumer)
                }
                is ProjectSpecificFingerprint.CoupledProjects -> {
                    if (host.invalidateCoupledProjects) {
                        val referrer = projects.entryFor(input.referringProject)
                        val target = projects.entryFor(input.targetProject)
                        target.consumedBy(referrer)
                        referrer.consumedBy(target)
                    }
                }
                else -> throw IllegalStateException("Unexpected configuration cache fingerprint: $input")
            }
        }
        return if (firstReason == null) {
            CheckedFingerprint.Valid
        } else {
            CheckedFingerprint.ProjectsInvalid(firstReason!!, projects.entries.filter { it.value.isInvalid }.map { it.key }.toSet())
        }
    }

    suspend fun ReadContext.visitEntriesForProjects(reusedProjects: Set<Path>, consumer: Consumer<ProjectSpecificFingerprint>) {
        while (true) {
            when (val input = read()) {
                null -> break
                is ProjectSpecificFingerprint.ProjectFingerprint ->
                    if (reusedProjects.contains(input.projectPath)) {
                        consumer.accept(input)
                    }
                is ProjectSpecificFingerprint.ProjectDependency ->
                    if (reusedProjects.contains(input.consumingProject)) {
                        consumer.accept(input)
                    }
                is ProjectSpecificFingerprint.CoupledProjects ->
                    if (reusedProjects.contains(input.referringProject)) {
                        consumer.accept(input)
                    }
            }
        }
    }

    private
    fun MutableMap<Path, ProjectInvalidationState>.entryFor(path: Path) = getOrPut(path) { ProjectInvalidationState() }

    private
    fun check(input: ConfigurationCacheFingerprint): InvalidationReason? {
        when (input) {
            is ConfigurationCacheFingerprint.WorkInputs -> input.run {
                val currentFingerprint = host.fingerprintOf(fileSystemInputs)
                if (currentFingerprint != fileSystemInputsFingerprint) {
                    // TODO: summarize what has changed (see https://github.com/gradle/configuration-cache/issues/282)
                    return "an input to $workDisplayName has changed"
                }
            }
            is ConfigurationCacheFingerprint.InputFile -> input.run {
                if (hasFileChanged(file, hash)) {
                    return "file '${displayNameOf(file)}' has changed"
                }
            }
            is ConfigurationCacheFingerprint.DirectoryChildren -> input.run {
                if (hasDirectoryChanged(file, hash)) {
                    return "directory '${displayNameOf(file)}' has changed"
                }
            }
            is ConfigurationCacheFingerprint.InputFileSystemEntry -> input.run {
                val newType = fileSystemEntryType(file)
                if (newType != fileType) {
                    val prefix = "the file system entry '${displayNameOf(file)}'"
                    return when {
                        newType == FileType.Missing -> return "$prefix has been removed"
                        fileType == FileType.Missing -> return "$prefix has been created"
                        else -> "$prefix has changed"
                    }
                }
            }
            is ConfigurationCacheFingerprint.ValueSource -> input.run {
                val reason = checkFingerprintValueIsUpToDate(obtainedValue)
                if (reason != null) return reason
            }
            is ConfigurationCacheFingerprint.InitScripts -> input.run {
                val reason = checkInitScriptsAreUpToDate(fingerprints, host.allInitScripts)
                if (reason != null) return reason
            }
            is ConfigurationCacheFingerprint.UndeclaredSystemProperty -> input.run {
                if (System.getProperty(key) != value) {
                    return "system property '$key' has changed"
                }
            }
            is ConfigurationCacheFingerprint.UndeclaredEnvironmentVariable -> input.run {
                if (System.getenv(key) != value) {
                    return "environment variable '$key' has changed"
                }
            }
            is ConfigurationCacheFingerprint.ChangingDependencyResolutionValue -> input.run {
                if (host.buildStartTime >= expireAt) {
                    return reason
                }
            }
            is ConfigurationCacheFingerprint.RemoteScript -> input.run {
                if (!host.isRemoteScriptUpToDate(uri)) {
                    return "remote script $uri has changed"
                }
            }
            is ConfigurationCacheFingerprint.GradleEnvironment -> input.run {
                if (host.gradleUserHomeDir != gradleUserHomeDir) {
                    return "Gradle user home directory has changed"
                }
                if (jvmFingerprint() != jvm) {
                    return "JVM has changed"
                }
                if (host.startParameterProperties != startParameterProperties) {
                    return "the set of Gradle properties has changed"
                }
                if (host.instrumentationAgentUsed != instrumentationAgentUsed) {
                    val statusChangeString = when (instrumentationAgentUsed) {
                        true -> "is no longer available"
                        false -> "is now applied"
                    }
                    return "the instrumentation Java agent $statusChangeString"
                }
            }
            is ConfigurationCacheFingerprint.EnvironmentVariablesPrefixedBy -> input.run {
                val current = System.getenv().filterKeysByPrefix(prefix)
                if (current != snapshot) {
                    return "the set of environment variables prefixed by '$prefix' has changed"
                }
            }
            is ConfigurationCacheFingerprint.SystemPropertiesPrefixedBy -> input.run {
                val currentWithoutIgnored = System.getProperties().uncheckedCast<Map<String, Any>>().filterKeysByPrefix(prefix).filterKeys {
                    // remove properties that are known to be modified by the build logic at the moment of obtaining this, as their initial
                    // values doesn't matter.
                    snapshot[it] != ConfigurationCacheFingerprint.SystemPropertiesPrefixedBy.IGNORED
                }
                val snapshotWithoutIgnored = snapshot.filterValues {
                    // remove placeholders of modified properties to only compare relevant values.
                    it != ConfigurationCacheFingerprint.SystemPropertiesPrefixedBy.IGNORED
                }
                if (currentWithoutIgnored != snapshotWithoutIgnored) {
                    return "the set of system properties prefixed by '$prefix' has changed"
                }
            }
        }
        return null
    }

    private
    fun checkInitScriptsAreUpToDate(
        previous: List<ConfigurationCacheFingerprint.InputFile>,
        current: List<File>
    ): InvalidationReason? =
        when (val upToDatePrefix = countUpToDatePrefixOf(previous, current)) {
            previous.size -> {
                val added = current.size - upToDatePrefix
                when {
                    added == 1 -> "init script '${displayNameOf(current[upToDatePrefix])}' has been added"
                    added > 1 -> "init script '${displayNameOf(current[upToDatePrefix])}' and ${added - 1} more have been added"
                    else -> null
                }
            }
            current.size -> {
                val removed = previous.size - upToDatePrefix
                when {
                    removed == 1 -> "init script '${displayNameOf(previous[upToDatePrefix].file)}' has been removed"
                    removed > 1 -> "init script '${displayNameOf(previous[upToDatePrefix].file)}' and ${removed - 1} more have been removed"
                    else -> null
                }
            }
            else -> {
                when (val modifiedScript = current[upToDatePrefix]) {
                    previous[upToDatePrefix].file -> "init script '${displayNameOf(modifiedScript)}' has changed"
                    else -> "content of ${ordinal(upToDatePrefix + 1)} init script, '${displayNameOf(modifiedScript)}', has changed"
                }
            }
        }

    private
    fun countUpToDatePrefixOf(
        previous: List<ConfigurationCacheFingerprint.InputFile>,
        current: List<File>
    ): Int = current.zip(previous)
        .takeWhile { (initScript, fingerprint) -> isUpToDate(initScript, fingerprint.hash) }
        .count()

    private
    fun checkFingerprintValueIsUpToDate(obtainedValue: ObtainedValue): InvalidationReason? {
        return obtainedValue.value.map { fingerprintedValue ->
            val valueSource = host.instantiateValueSourceOf(obtainedValue)
            if (fingerprintedValue != valueSource.obtain()) {
                buildLogicInputHasChanged(valueSource)
            } else {
                null
            }
        }.getOrMapFailure { failure ->
            // This can only happen if someone ignored configuration cache problems and still stored the entry.
            // We're invalidating the cache to save the user a manual "rm -rf .gradle/configuration-cache", as there is no way out.
            logger.info("The build logic input of type ${obtainedValue.valueSourceType} cannot be checked because it failed when storing the entry", failure)
            buildLogicInputFailed(obtainedValue, failure)
        }
    }

    private
    fun hasFileChanged(file: File, originalHash: HashCode) =
        !isUpToDate(file, originalHash)

    private
    fun hasDirectoryChanged(file: File, originalHash: HashCode?) =
        host.hashCodeOfDirectoryContent(file) != originalHash

    private
    fun isUpToDate(file: File, originalHash: HashCode) =
        host.hashCodeOf(file) == originalHash

    private
    fun displayNameOf(file: File) =
        host.displayNameOf(file)

    private
    fun buildLogicInputHasChanged(valueSource: ValueSource<Any, ValueSourceParameters>): InvalidationReason =
        (valueSource as? Describable)?.let {
            it.displayName + " has changed"
        } ?: "a build logic input of type '${unpackType(valueSource).simpleName}' has changed"

    private
    fun buildLogicInputFailed(obtainedValue: ObtainedValue, failure: Throwable): InvalidationReason =
        "a build logic input of type '${obtainedValue.valueSourceType.simpleName}' failed when storing the entry with $failure"

    private
    class ProjectInvalidationState {
        // When true, the project is definitely invalid
        // When false, validity is not known
        private
        var invalid = false

        private
        val consumedBy = mutableSetOf<ProjectInvalidationState>()

        val isInvalid: Boolean
            get() = invalid

        fun consumedBy(consumer: ProjectInvalidationState) {
            if (invalid) {
                consumer.invalidate()
            } else {
                consumedBy.add(consumer)
            }
        }

        fun invalidate() {
            if (invalid) {
                return
            }
            invalid = true
            for (consumer in consumedBy) {
                consumer.invalidate()
            }
            consumedBy.clear()
        }
    }
}
