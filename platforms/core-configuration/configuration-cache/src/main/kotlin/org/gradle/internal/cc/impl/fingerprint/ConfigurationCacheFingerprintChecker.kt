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

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.codehaus.groovy.vmplugin.VMPluginFactory
import org.gradle.api.Describable
import org.gradle.api.internal.GeneratedSubclasses.unpackType
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.properties.GradlePropertyScope
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.internal.RenderingUtils.oxfordListOf
import org.gradle.internal.RenderingUtils.quotedOxfordListOf
import org.gradle.internal.cc.base.logger
import org.gradle.internal.cc.impl.CheckedFingerprint
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheInputFileChecker.FileUpToDateStatus
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
class ConfigurationCacheFingerprintChecker(
    private val host: Host,
    /**
     * The system properties as the build that stored the entry saw them, reconstructed while the
     * build-scoped fingerprint is checked and then reused for every project.
     */
    private val systemProperties: VersionedSystemProperties
) {

    interface Host : ConfigurationCacheInputFileChecker.Host {
        val isEncrypted: Boolean
        val encryptionKeyHashCode: HashCode
        val gradleUserHomeDir: File
        val startParameterProperties: Map<String, Any?>
        val allInitScripts: List<File>
        val buildStartTime: Long
        val invalidateCoupledProjects: Boolean
        val ignoreInputsDuringConfigurationCacheStore: Boolean
        val instrumentationAgentUsed: Boolean
        val ignoredFileSystemCheckInputs: String?
        fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode
        fun hashCodeOfDirectoryContent(file: File): HashCode?
        fun instantiateValueSourceOf(obtainedValue: ObtainedValue): ValueSource<Any, ValueSourceParameters>
        fun isRemoteScriptUpToDate(uri: URI): Boolean
        fun hasValidBuildSrc(candidateBuildSrc: File): Boolean
        /**
         * Loads the Gradle properties from the given directory, returning the system properties this
         * installed, if any. See `GradlePropertiesController.loadGradleProperties`.
         */
        fun loadProperties(propertyScope: GradlePropertyScope, propertiesDir: File): Map<String, String>
        fun gradleProperty(propertyScope: GradlePropertyScope, propertyName: String): Any?
        fun gradlePropertiesPrefixedBy(propertyScope: GradlePropertyScope, prefix: String): Map<String, String>
    }

    private
    val inputFileChecker = ConfigurationCacheInputFileChecker(host)

    /**
     * State shared between the manifest pass ([readProjectFingerprintManifest]) and the per-project
     * fingerprint file passes ([checkProjectFingerprintFile]). A single checker instance is used to
     * check the whole project-scoped fingerprint of an entry.
     */
    private
    val projects = hashMapOf<Path, ProjectInvalidationState>()

    private
    val projectFingerprintFiles = LinkedHashSet<Path>()

    /**
     * The identity paths of the projects that have their own fingerprint file, in the order they were
     * declared in the manifest. Only populated after [readProjectFingerprintManifest].
     */
    val projectsWithFingerprintFile: Set<Path>
        get() = projectFingerprintFiles

    private
    var firstInvalidatedPath: Path? = null

    suspend fun ReadContext.checkBuildScopedFingerprint(): InvalidationReason? {
        // TODO: log some debug info
        ensureGroovyRuntimeInitialized()
        // Values that observed the system properties at a version we haven't reconstructed yet. The change
        // that produces that version is recorded in this same file, but not necessarily before them.
        val awaitingSystemProperties = mutableListOf<ConfigurationCacheFingerprint>()
        while (true) {
            when (val input = read()) {
                null -> break
                is ConfigurationCacheFingerprint -> {
                    // An input that is not specific to a project. If it is out-of-date, then invalidate the whole cache entry and skip any further checks
                    val reason = checkOrAwaitSystemProperties(input, awaitingSystemProperties)
                    if (reason != null) {
                        return reason
                    }
                }

                else -> error("Unexpected configuration cache fingerprint: $input")
            }
        }
        systemProperties.ingestionFinished()
        return awaitingSystemProperties.firstNotNullOfOrNull { check(it) }
    }

    /**
     * Checks the given value, unless it observed a version of the system properties that isn't known yet, in
     * which case it is set aside until the change producing that version is read.
     */
    private
    fun checkOrAwaitSystemProperties(
        input: ConfigurationCacheFingerprint,
        awaiting: MutableList<ConfigurationCacheFingerprint>
    ): InvalidationReason? {
        if (input is ReadsSystemProperties && !systemProperties.isReadyFor(input.systemPropertiesVersion)) {
            awaiting.add(input)
            return null
        }
        val reason = check(input)
        if (reason != null || input !is ChangesSystemProperties) {
            return reason
        }
        // This change may be the one some of the awaiting values were waiting for.
        val ready = awaiting.filter { systemProperties.isReadyFor((it as ReadsSystemProperties).systemPropertiesVersion) }
        awaiting.removeAll(ready)
        return ready.firstNotNullOfOrNull { check(it) }
    }

    /**
     * Reads the project-scoped fingerprint manifest: the identity of every project that has its own
     * fingerprint file, and the cross-project dependency/coupling relationships. The fingerprint values
     * are checked separately, per project, by [checkProjectFingerprintFile].
     */
    suspend fun ReadContext.readProjectFingerprintManifest() {
        // TODO: log some debug info
        while (true) {
            when (val input = read()) {
                null -> break
                is ProjectSpecificFingerprint.ProjectIdentity -> {
                    val state = projects.entryFor(input.identityPath)
                    state.buildPath = input.buildPath
                    state.projectPath = input.projectPath
                    projectFingerprintFiles.add(input.identityPath)
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
    }

    /**
     * Whether the given project still needs its fingerprint file checked, i.e. it hasn't already been
     * invalidated (for example, transitively via a dependency).
     */
    fun requiresProjectFingerprintCheck(projectPath: Path): Boolean = !projects.entryFor(projectPath).isInvalid

    /**
     * Checks the fingerprint values stored in a single project's fingerprint file. If any value is
     * out-of-date, the project is invalidated (which transitively invalidates its consumers).
     */
    suspend fun ReadContext.checkProjectFingerprintFile(projectPath: Path) {
        val state = projects.entryFor(projectPath)
        while (true) {
            when (val input = read()) {
                null -> break
                is ConfigurationCacheFingerprint -> {
                    // Don't check a value for a project that is already out-of-date
                    if (!state.isInvalid) {
                        val reason = check(input)
                        if (reason != null) {
                            if (firstInvalidatedPath == null) {
                                firstInvalidatedPath = projectPath
                            }
                            state.invalidate(reason)
                        }
                    }
                }

                else -> error("Unexpected configuration cache fingerprint: $input")
            }
        }
    }

    fun projectFingerprintInvalidationResult(): CheckedFingerprint.InvalidProjects? =
        firstInvalidatedPath?.let { path ->
            CheckedFingerprint.InvalidProjects(
                path,
                projects
                    .filterValues { it.isInvalid }
                    .mapValues { it.value.toProjectInvalidationData() }
            )
        }

    /**
     * Visits the manifest records (project identity and cross-project relationships) that belong to any
     * of the [reusedProjects]. The fingerprint values of a reused project are visited separately by
     * [visitProjectFingerprintFile].
     */
    suspend fun ReadContext.visitManifestEntriesForProjects(reusedProjects: Set<Path>, consumer: Consumer<ProjectSpecificFingerprint>) {
        while (true) {
            // TODO(mlopatkin): this implementation duplicates some inputs, e.g. a build file input is stored even if the project is reused.
            when (val input = read()) {
                null -> break

                is ProjectSpecificFingerprint.ProjectIdentity ->
                    if (reusedProjects.contains(input.identityPath)) {
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

                else -> error("Unexpected configuration cache fingerprint: $input")
            }
        }
    }

    /**
     * Visits all the fingerprint values stored in a single project's fingerprint file.
     */
    suspend fun ReadContext.visitProjectFingerprintFile(consumer: Consumer<ConfigurationCacheFingerprint>) {
        while (true) {
            when (val input = read()) {
                null -> break
                is ConfigurationCacheFingerprint -> consumer.accept(input)
                else -> error("Unexpected configuration cache fingerprint: $input")
            }
        }
    }

    private
    fun MutableMap<Path, ProjectInvalidationState>.entryFor(path: Path) = computeIfAbsent(path, ::ProjectInvalidationState)

    /**
     * Forces Groovy's runtime to initialize before checking anything, because checking an entry can
     * remove system properties from the process that is loading it.
     *
     * Removing or clearing JVM-standard properties such as `file.encoding` or `java.home` breaks the
     * static initializer of any class that reads them lazily. In particular, Groovy's `VMPluginFactory`
     * is initialized the first time the cached project model is realized (when a `DefaultProject` is
     * created); with `file.encoding` gone its initialization fails with "Null charset name", leaving the
     * class permanently unusable ("Could not initialize class org.codehaus.groovy.vmplugin.VMPluginFactory").
     *
     * A non-cached build never hits this because executing the Groovy build scripts initializes the
     * Groovy runtime before any such mutation runs; we mirror that ordering here. This only matters
     * in a fresh JVM (e.g. `--no-daemon`); with a reused daemon the runtime is already initialized.
     *
     * Doing this once up front, rather than at each site that changes the properties, keeps the guard
     * from being forgotten when a new one is added.
     */
    private
    fun ensureGroovyRuntimeInitialized() {
        VMPluginFactory.getPlugin()
    }

    /**
     * The system properties as of the given version, as an ordinary map.
     */
    private
    fun systemPropertiesAt(version: Long): Map<String, Any> =
        systemProperties.snapshotAt(version).associate { (key, value) -> key.toString() to value }

    /**
     * The value the given property had at the given version, as [System.getProperty] would report it: the
     * properties table can hold non-string values, but reading one back through the regular API yields null.
     */
    private
    fun systemPropertyAt(version: Long, key: String): String? =
        systemProperties.snapshotAt(version).get(key) as? String

    /**
     * Runs the given action with the process' system properties set to the state they were in at the given
     * version, restoring them afterwards.
     *
     * Only needed for the values that read the system properties themselves rather than through a recorded
     * value, i.e. value sources.
     */
    private
    fun <T> withSystemPropertiesAt(version: Long, action: () -> T): T {
        val current = System.getProperties()
        System.setProperties(systemProperties.snapshotAt(version).toProperties())
        try {
            return action()
        } finally {
            System.setProperties(current)
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
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
                inputFileChecker.run {
                    check(file, hash)
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
                // The value source reads the system properties directly, so it has to run against the state
                // they were in when it was obtained.
                val reason = withSystemPropertiesAt(systemPropertiesVersion) {
                    checkFingerprintValueIsUpToDate(obtainedValue)
                }
                reason?.let { message(it) }
            }

            is ConfigurationCacheFingerprint.InitScripts -> input.run {
                val reason = checkInitScriptsAreUpToDate(fingerprints, host.allInitScripts)
                reason?.let { message(it) }
            }

            is ConfigurationCacheFingerprint.SystemPropertyChanged -> input.run {
                systemProperties.setProperty(systemPropertiesVersion, key, value)
                null
            }

            is ConfigurationCacheFingerprint.SystemPropertyRemoved -> input.run {
                systemProperties.removeProperty(systemPropertiesVersion, key)
                null
            }

            is ConfigurationCacheFingerprint.SystemPropertiesCleared -> input.run {
                systemProperties.clearProperties(systemPropertiesVersion)
                null
            }

            is ConfigurationCacheFingerprint.UndeclaredSystemProperty -> input.run {
                ifOrNull(systemPropertyAt(systemPropertiesVersion, key) != value) {
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

            is ConfigurationCacheFingerprint.StartParameterProjectProperties -> input.run {
                ifOrNull(host.startParameterProperties != snapshot) {
                    text("the set of Gradle properties has changed: ").text(detailedMessageForChanges(snapshot, host.startParameterProperties))
                }
            }

            is ConfigurationCacheFingerprint.GradleEnvironment -> input.run {
                when {
                    host.gradleUserHomeDir != gradleUserHomeDir -> text("Gradle user home directory has changed")

                    jvmFingerprint() != jvm -> text("JVM has changed")

                    host.ignoreInputsDuringConfigurationCacheStore != ignoreInputsDuringConfigurationCacheStore ->
                        text("the value of ignored configuration inputs flag (${StartParameterBuildOptions.ConfigurationCacheIgnoreInputsDuringStore.PROPERTY_NAME}) has changed")

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
                val current = systemPropertiesAt(systemPropertiesVersion).filterKeysByPrefix(prefix)
                ifOrNull(current != snapshot) {
                    text("the set of system properties prefixed by ")
                        .reference(prefix)
                        .text(" has changed: ")
                        .text(detailedMessageForChanges(snapshot, current))
                }
            }

            is ConfigurationCacheFingerprint.MissingBuildSrcDir -> input.run {
                val hasBuildSrc = host.hasValidBuildSrc(buildSrcDir)
                ifOrNull(hasBuildSrc) {
                    text("a buildSrc build at ").reference(displayNameOf(buildSrcDir))
                        .text(" has been added")
                }
            }

            is ConfigurationCacheFingerprint.GradlePropertiesLoaded -> input.run {
                // Loading the properties installs the system properties they declare, which is part of the
                // environment the values recorded after this observed.
                systemProperties.installProperties(systemPropertiesVersion, host.loadProperties(propertyScope, propertiesDir))
                null
            }

            is ConfigurationCacheFingerprint.GradleProperty -> input.run {
                ifOrNull(propertyValue != host.gradleProperty(propertyScope, propertyName)) {
                    text("Gradle property ").reference(propertyName).text(" has changed")
                }
            }

            is ConfigurationCacheFingerprint.GradlePropertiesPrefixedBy -> input.run {
                val current = host.gradlePropertiesPrefixedBy(propertyScope, prefix)
                ifOrNull(snapshot != current) {
                    text("the set of Gradle properties prefixed by ")
                        .reference(prefix)
                        .text(" has changed: ")
                        .text(detailedMessageForChanges(snapshot, current))
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
        inputFileChecker.checkFileUpToDateStatus(file, originalHash) == FileUpToDateStatus.Unchanged

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
        val consumedBy = ObjectOpenHashSet<ProjectInvalidationState>()

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

        fun toProjectInvalidationData(): CheckedFingerprint.InvalidProject {
            val buildPath = this.buildPath
            val projectPath = this.projectPath
            require(buildPath != null) {
                "buildPath for project $identityPath wasn't loaded from the fingerprint"
            }
            require(projectPath != null) {
                "projectPath for project $identityPath wasn't loaded from the fingerprint"
            }
            return CheckedFingerprint.InvalidProject(buildPath, projectPath, invalidationReason)
        }
    }
}
