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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.api.Describable
import org.gradle.api.internal.GeneratedSubclasses.unpackType
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.RenderingUtils.oxfordListOf
import org.gradle.internal.RenderingUtils.quotedOxfordListOf
import org.gradle.internal.cc.base.logger
import org.gradle.internal.cc.impl.CheckedFingerprint
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.extensions.core.fileSystemEntryType
import org.gradle.internal.extensions.stdlib.filterKeysByPrefix
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.util.NumberUtil.ordinal
import org.gradle.util.Path
import java.io.File
import java.net.URI
import java.util.function.Consumer


internal
typealias InvalidationReason = StructuredMessage


internal
class ConfigurationCacheFingerprintChecker(private val host: Host) {

    interface Host {
        val buildPath: Path
        val isEncrypted: Boolean
        val encryptionKeyHashCode: HashCode
        val gradleUserHomeDir: File
        val allInitScripts: List<File>
        val startParameterProperties: Map<String, Any?>
        val buildStartTime: Long
        val invalidateCoupledProjects: Boolean
        val ignoreInputsInConfigurationCacheTaskGraphWriting: Boolean
        val instrumentationAgentUsed: Boolean
        val ignoredFileSystemCheckInputs: String?
        fun gradleProperty(propertyName: String): String?
        fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode
        fun hashCodeAndTypeOf(file: File): Pair<HashCode, FileType>
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
                        return CheckedFingerprint.EntryInvalid(host.buildPath, reason)
                    }
                }

                else -> error("Unexpected configuration cache fingerprint: $input")
            }
        }
        return CheckedFingerprint.Valid
    }

    @Suppress("NestedBlockDepth")
    suspend fun ReadContext.checkProjectScopedFingerprint(): CheckedFingerprint {
        // TODO: log some debug info
        var firstInvalidatedPath: Path? = null
        val projects = hashMapOf<Path, ProjectInvalidationState>()
        while (true) {
            when (val input = read()) {
                null -> break
                is ProjectSpecificFingerprint.ProjectIdentity -> {
                    val state = projects.entryFor(input.identityPath)
                    state.buildPath = input.buildPath
                    state.projectPath = input.projectPath
                }
                is ProjectSpecificFingerprint.ProjectFingerprint -> {
                    // An input that is specific to a project. If it is out-of-date, then invalidate that project's values and continue checking values
                    // Don't check a value for a project that is already out-of-date
                    val state = projects.entryFor(input.projectIdentityPath)
                    if (!state.isInvalid) {
                        val reason = check(input.value)
                        if (reason != null) {
                            if (firstInvalidatedPath == null) {
                                firstInvalidatedPath = input.projectIdentityPath
                            }
                            state.invalidate(reason)
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

                else -> error("Unexpected configuration cache fingerprint: $input")
            }
        }
        return if (firstInvalidatedPath == null) {
            CheckedFingerprint.Valid
        } else {
            val invalidatedProjects = projects.filterValues { it.isInvalid }.mapValues {
                it.value.toProjectInvalidationData()
            }
            CheckedFingerprint.ProjectsInvalid(firstInvalidatedPath, invalidatedProjects)
        }
    }

    suspend fun ReadContext.visitEntriesForProjects(reusedProjects: Set<Path>, consumer: Consumer<ProjectSpecificFingerprint>) {
        while (true) {
            // TODO(mlopatkin): this implementation duplicates some inputs, e.g. a build file input is stored even if the project is reused.
            when (val input = read()) {
                null -> break

                is ProjectSpecificFingerprint.ProjectIdentity ->
                    if (reusedProjects.contains(input.identityPath)) {
                        consumer.accept(input)
                    }

                is ProjectSpecificFingerprint.ProjectFingerprint ->
                    if (reusedProjects.contains(input.projectIdentityPath)) {
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
    fun MutableMap<Path, ProjectInvalidationState>.entryFor(path: Path) = computeIfAbsent(path, ::ProjectInvalidationState)

    @Suppress("CyclomaticComplexMethod")
    private
    fun check(input: ConfigurationCacheFingerprint): InvalidationReason? = structuredMessageOrNull {
        when (input) {
            is ConfigurationCacheFingerprint.WorkInputs -> input.run {
                val currentFingerprint = host.fingerprintOf(fileSystemInputs)
                ifOrNull(currentFingerprint != fileSystemInputsFingerprint) {
                    // TODO: summarize what has changed (see https://github.com/gradle/configuration-cache/issues/282)
                    text("an input to $workDisplayName has changed")
                }
            }

            is ConfigurationCacheFingerprint.InputFile -> input.run {
                when (checkFileUpToDateStatus(file, hash)) {
                    FileUpToDateStatus.ContentsChanged -> text("file ").reference(displayNameOf(file)).text(" has changed")
                    FileUpToDateStatus.Removed -> text("file ").reference(displayNameOf(file)).text(" has been removed")
                    FileUpToDateStatus.TypeChanged -> text("file ").reference(displayNameOf(file)).text(" has been replaced by a directory")
                    FileUpToDateStatus.Unchanged -> null
                }
            }

            is ConfigurationCacheFingerprint.DirectoryChildren -> input.run {
                ifOrNull(hasDirectoryChanged(file, hash)) {
                    text("directory ").reference(displayNameOf(file)).text(" has changed")
                }
            }

            is ConfigurationCacheFingerprint.InputFileSystemEntry -> input.run {
                val newType = fileSystemEntryType(file)
                ifOrNull(newType != fileType) {
                    text("the file system entry ").reference(displayNameOf(file)).text(
                        when {
                            newType == FileType.Missing -> " has been removed"
                            fileType == FileType.Missing -> " has been created"
                            else -> " has changed"
                        }
                    )
                }
            }

            is ConfigurationCacheFingerprint.ValueSource -> input.run {
                val reason = checkFingerprintValueIsUpToDate(obtainedValue)
                reason?.let { message(it) }
            }

            is ConfigurationCacheFingerprint.InitScripts -> input.run {
                val reason = checkInitScriptsAreUpToDate(fingerprints, host.allInitScripts)
                reason?.let { message(it) }
            }

            is ConfigurationCacheFingerprint.UndeclaredSystemProperty -> input.run {
                ifOrNull(System.getProperty(key) != value) {
                    text("system property ").reference(key).text(" has changed")
                }
            }

            is ConfigurationCacheFingerprint.UndeclaredEnvironmentVariable -> input.run {
                ifOrNull(System.getenv(key) != value) {
                    text("environment variable ").reference(key).text(" has changed")
                }
            }

            is ConfigurationCacheFingerprint.ChangingDependencyResolutionValue -> input.run {
                ifOrNull(host.buildStartTime >= expireAt) {
                    text(reason)
                }
            }

            is ConfigurationCacheFingerprint.RemoteScript -> input.run {
                ifOrNull(!host.isRemoteScriptUpToDate(uri)) {
                    text("remote script $uri has changed")
                }
            }

            is ConfigurationCacheFingerprint.GradleEnvironment -> input.run {
                when {
                    host.gradleUserHomeDir != gradleUserHomeDir -> text("Gradle user home directory has changed")
                    jvmFingerprint() != jvm -> text("JVM has changed")
                    host.startParameterProperties != startParameterProperties ->
                        text("the set of Gradle properties has changed: ").text(detailedMessageForChanges(startParameterProperties, host.startParameterProperties))

                    host.ignoreInputsInConfigurationCacheTaskGraphWriting != ignoreInputsInConfigurationCacheTaskGraphWriting ->
                        text("the value of ignored configuration inputs flag (${StartParameterBuildOptions.ConfigurationCacheIgnoreInputsInTaskGraphSerialization.PROPERTY_NAME}) has changed")

                    host.instrumentationAgentUsed != instrumentationAgentUsed ->
                        text("the instrumentation Java agent ${if (instrumentationAgentUsed) "is no longer available" else "is now applied"}")

                    host.ignoredFileSystemCheckInputs != ignoredFileSystemCheckInputPaths ->
                        text("the set of paths ignored in file-system-check input tracking (${StartParameterBuildOptions.ConfigurationCacheIgnoredFileSystemCheckInputs.PROPERTY_NAME}) has changed")
                    else -> null
                }
            }

            is ConfigurationCacheFingerprint.EnvironmentVariablesPrefixedBy -> input.run {
                val current = System.getenv().filterKeysByPrefix(prefix)
                ifOrNull(current != snapshot) {
                    text("the set of environment variables prefixed by ").reference(prefix).text(" has changed: ").text(detailedMessageForChanges(snapshot, current))
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
                ifOrNull(currentWithoutIgnored != snapshotWithoutIgnored) {
                    text("the set of system properties prefixed by ").reference(prefix).text(" has changed: ").text(detailedMessageForChanges(snapshotWithoutIgnored, currentWithoutIgnored))
                }
            }
        }
    }

    private
    fun checkInitScriptsAreUpToDate(
        previous: List<ConfigurationCacheFingerprint.InputFile>,
        current: List<File>
    ): InvalidationReason? = structuredMessageOrNull {
        when (val upToDatePrefix = countUpToDatePrefixOf(previous, current)) {
            previous.size -> {
                val added = current.size - upToDatePrefix
                when {
                    added == 1 -> text("init script ").reference(displayNameOf(current[upToDatePrefix])).text(" has been added")
                    added > 1 -> text("init script ").reference(displayNameOf(current[upToDatePrefix])).text(" and ${added - 1} more have been added")
                    else -> null
                }
            }

            current.size -> {
                val removed = previous.size - upToDatePrefix
                when {
                    removed == 1 -> text("init script ").reference(displayNameOf(previous[upToDatePrefix].file)).text(" has been removed")
                    removed > 1 -> text("init script ").reference(displayNameOf(previous[upToDatePrefix].file)).text(" and ${removed - 1} more have been removed")
                    else -> null
                }
            }

            else -> {
                when (val modifiedScript = current[upToDatePrefix]) {
                    previous[upToDatePrefix].file -> text("init script ").reference(displayNameOf(modifiedScript)).text(" has changed")
                    else -> text("content of ${ordinal(upToDatePrefix + 1)} init script, ").reference(displayNameOf(modifiedScript)).text(", has changed")
                }
            }
        }
    }


    private
    fun countUpToDatePrefixOf(
        previous: List<ConfigurationCacheFingerprint.InputFile>,
        current: List<File>
    ): Int = current.zip(previous)
        .takeWhile { (initScript, fingerprint) -> isFileUpToDate(initScript, fingerprint.hash) }
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
    fun hasDirectoryChanged(file: File, originalHash: HashCode?) =
        host.hashCodeOfDirectoryContent(file) != originalHash

    private
    fun isFileUpToDate(file: File, originalHash: HashCode) =
        checkFileUpToDateStatus(file, originalHash) == FileUpToDateStatus.Unchanged

    private
    enum class FileUpToDateStatus {
        Unchanged,
        ContentsChanged,
        TypeChanged,
        Removed
    }

    private
    fun checkFileUpToDateStatus(file: File, originalHash: HashCode): FileUpToDateStatus {
        val (snapshotHash, snapshotType) = host.hashCodeAndTypeOf(file)
        if (snapshotHash == originalHash) {
            return FileUpToDateStatus.Unchanged
        }
        return when (snapshotType) {
            FileType.RegularFile -> FileUpToDateStatus.ContentsChanged
            FileType.Directory -> FileUpToDateStatus.TypeChanged
            FileType.Missing -> FileUpToDateStatus.Removed
        }
    }

    private
    fun displayNameOf(file: File) =
        host.displayNameOf(file)

    private
    fun buildLogicInputHasChanged(valueSource: ValueSource<Any, ValueSourceParameters>): InvalidationReason = StructuredMessage.forText(
        (valueSource as? Describable)?.let {
            it.displayName + " has changed"
        } ?: "a build logic input of type '${unpackType(valueSource).simpleName}' has changed"
    )

    private
    fun buildLogicInputFailed(obtainedValue: ObtainedValue, failure: Throwable): InvalidationReason = StructuredMessage.forText(
        "a build logic input of type '${obtainedValue.valueSourceType.simpleName}' failed when storing the entry with $failure"
    )

    /**
     * Builds a structured message with a given [block], but if null is returned from the block, discards the message.
     * @return built message or null if [block] returns null
     */
    private
    inline fun structuredMessageOrNull(block: StructuredMessage.Builder.() -> StructuredMessage.Builder?): StructuredMessage? =
        StructuredMessage.Builder().run { block() }?.build()

    private
    inline fun <T> ifOrNull(condition: Boolean, block: () -> T): T? {
        return if (condition) block() else null
    }

    private
    fun wereOrWas(values: Collection<String>, verb: String): String? =
        when (values.size) {
            0 -> null
            1 -> "'${values.single()}' was $verb"
            else -> "${quotedOxfordListOf(values, "and")} were $verb"
        }

    private
    fun <T> detailedMessageForChanges(oldValues: Map<String, T>, newValues: Map<String, T>): String {
        val added = newValues.keys - oldValues.keys
        val removed = oldValues.keys - newValues.keys
        val changed = oldValues.filter { (key, value) -> key in newValues && newValues[key] != value }.map { it.key }
        return oxfordListOf(
            listOfNotNull(
                wereOrWas(changed, "changed")?.let { if (changed.size == 1) "the value of $it" else "the values of $it" },
                wereOrWas(added, "added"),
                wereOrWas(removed, "removed")
            ),
            "and"
        )
    }

    private
    class ProjectInvalidationState(private val identityPath: Path) {
        var buildPath: Path? = null
        var projectPath: Path? = null

        // When not null, the project is definitely invalid
        // When null, validity is not known
        private
        var _invalidationReason: InvalidationReason? = null

        private
        val consumedBy = mutableSetOf<ProjectInvalidationState>()

        val isInvalid: Boolean
            get() = _invalidationReason != null

        val invalidationReason
            get() = _invalidationReason!!

        fun consumedBy(consumer: ProjectInvalidationState) {
            if (isInvalid) {
                invalidateConsumer(consumer)
            } else {
                consumedBy.add(consumer)
            }
        }

        fun invalidate(reason: StructuredMessageBuilder) {
            invalidate(StructuredMessage.Builder().apply(reason).build())
        }

        fun invalidate(reason: InvalidationReason) {
            if (isInvalid) {
                return
            }
            _invalidationReason = reason
            consumedBy.forEach(this::invalidateConsumer)
            consumedBy.clear()
        }

        private
        fun invalidateConsumer(consumer: ProjectInvalidationState) {
            consumer.invalidate {
                text("project dependency ")
                reference(identityPath.toString())
                text(" has changed")
            }
        }

        fun toProjectInvalidationData(): CheckedFingerprint.ProjectInvalidationData {
            val buildPath = this.buildPath
            val projectPath = this.projectPath
            require(buildPath != null) {
                "buildPath for project $identityPath wasn't loaded from the fingerprint"
            }
            require(projectPath != null) {
                "projectPath for project $identityPath wasn't loaded from the fingerprint"
            }
            return CheckedFingerprint.ProjectInvalidationData(buildPath, projectPath, invalidationReason)
        }
    }
}
