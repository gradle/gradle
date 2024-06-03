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

import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.Describable
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.configurations.ProjectComponentObservationListener
import org.gradle.api.internal.artifacts.configurations.dynamicversion.Expiry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ChangingValueDependencyResolutionListener
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileCollectionObservationListener
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.sources.EnvironmentVariableValueSource
import org.gradle.api.internal.provider.sources.EnvironmentVariablesPrefixedByValueSource
import org.gradle.api.internal.provider.sources.FileContentValueSource
import org.gradle.api.internal.provider.sources.GradlePropertiesPrefixedByValueSource
import org.gradle.api.internal.provider.sources.GradlePropertyValueSource
import org.gradle.api.internal.provider.sources.SystemPropertiesPrefixedByValueSource
import org.gradle.api.internal.provider.sources.SystemPropertyValueSource
import org.gradle.api.internal.provider.sources.process.ProcessOutputValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.util.PatternSet
import org.gradle.configurationcache.CoupledProjectsListener
import org.gradle.configurationcache.InputTrackingState
import org.gradle.configurationcache.UndeclaredBuildInputListener
import org.gradle.internal.extensions.core.fileSystemEntryType
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.extensions.core.uri
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint.InputFile
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint.InputFileSystemEntry
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint.ValueSource
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.serialize.graph.DefaultWriteContext
import org.gradle.configurationcache.services.ConfigurationCacheEnvironment
import org.gradle.configurationcache.services.ConfigurationCacheEnvironmentChangeTracker
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.internal.ScriptSourceListener
import org.gradle.internal.buildoption.FeatureFlag
import org.gradle.internal.buildoption.FeatureFlagListener
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier
import org.gradle.internal.execution.UnitOfWork.InputVisitor
import org.gradle.internal.execution.WorkExecutionTracker
import org.gradle.internal.execution.WorkInputListener
import org.gradle.internal.hash.HashCode
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.internal.scripts.ScriptFileResolvedListener
import org.gradle.tooling.provider.model.internal.ToolingModelProjectDependencyListener
import org.gradle.util.Path
import java.io.File
import java.net.URI
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap


internal
class ConfigurationCacheFingerprintWriter(
    private val host: Host,
    buildScopedContext: DefaultWriteContext,
    projectScopedContext: DefaultWriteContext,
    private val fileCollectionFactory: FileCollectionFactory,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val workExecutionTracker: WorkExecutionTracker,
    private val environmentChangeTracker: ConfigurationCacheEnvironmentChangeTracker,
    private val inputTrackingState: InputTrackingState,
) : ValueSourceProviderFactory.ValueListener,
    ValueSourceProviderFactory.ComputationListener,
    WorkInputListener,
    ScriptExecutionListener,
    UndeclaredBuildInputListener,
    ChangingValueDependencyResolutionListener,
    ProjectComponentObservationListener,
    CoupledProjectsListener,
    ToolingModelProjectDependencyListener,
    FileResourceListener,
    ScriptFileResolvedListener,
    FeatureFlagListener,
    FileCollectionObservationListener,
    ScriptSourceListener,
    ConfigurationCacheEnvironment.Listener {

    interface Host {
        val isEncrypted: Boolean
        val encryptionKeyHashCode: HashCode
        val gradleUserHomeDir: File
        val allInitScripts: List<File>
        val startParameterProperties: Map<String, Any?>
        val buildStartTime: Long
        val cacheIntermediateModels: Boolean
        val modelAsProjectDependency: Boolean
        val ignoreInputsInConfigurationCacheTaskGraphWriting: Boolean
        val instrumentationAgentUsed: Boolean
        val ignoredFileSystemCheckInputs: String?
        fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode
        fun hashCodeOf(file: File): HashCode
        fun hashCodeOfDirectoryChildrenNames(file: File): HashCode
        fun displayNameOf(file: File): String
        fun reportProblem(exception: Throwable? = null, documentationSection: DocumentationSection? = null, message: StructuredMessageBuilder)
        fun reportInput(input: PropertyProblem)
        fun location(consumer: String?): PropertyTrace
    }

    private
    val buildScopedWriter = ScopedFingerprintWriter<ConfigurationCacheFingerprint>(buildScopedContext)

    private
    val buildScopedSink = BuildScopedSink(host, buildScopedWriter)

    private
    val projectScopedWriter = ScopedFingerprintWriter<ProjectSpecificFingerprint>(projectScopedContext)

    private
    val sinksForProject = ConcurrentHashMap<Path, ProjectScopedSink>()

    private
    val projectForThread = ThreadLocal<ProjectScopedSink>()

    private
    val projectDependencies = newConcurrentHashSet<ProjectSpecificFingerprint>()

    private
    val undeclaredSystemProperties = newConcurrentHashSet<String>()

    private
    val systemPropertiesPrefixedBy = newConcurrentHashSet<String>()

    private
    val undeclaredEnvironmentVariables = newConcurrentHashSet<String>()

    private
    val environmentVariablesPrefixedBy = newConcurrentHashSet<String>()

    private
    val reportedFiles = newConcurrentHashSet<File>()

    private
    val reportedDirectories = newConcurrentHashSet<File>()

    private
    val reportedFileSystemEntries = newConcurrentHashSet<File>()

    private
    val reportedValueSources = newConcurrentHashSet<String>()

    private
    var closestChangingValue: ConfigurationCacheFingerprint.ChangingDependencyResolutionValue? = null

    init {
        buildScopedSink.initScripts(host.allInitScripts)
        buildScopedSink.write(
            ConfigurationCacheFingerprint.GradleEnvironment(
                host.gradleUserHomeDir,
                jvmFingerprint(),
                host.startParameterProperties,
                host.ignoreInputsInConfigurationCacheTaskGraphWriting,
                host.instrumentationAgentUsed,
                host.ignoredFileSystemCheckInputs
            )
        )
    }

    /**
     * Stops all writers.
     *
     * **MUST ALWAYS BE CALLED**
     */
    fun close() {
        synchronized(this) {
            closestChangingValue?.let {
                buildScopedSink.write(it)
            }
        }
        CompositeStoppable.stoppable(buildScopedWriter, projectScopedWriter).stop()
    }

    override fun scriptSourceObserved(scriptSource: ScriptSource) {
        if (isInputTrackingDisabled()) {
            return
        }

        scriptSource.uri?.takeIf { it.isHttp }?.let { uri ->
            sink().captureRemoteScript(uri)
        }
    }

    /**
     * Returns `true` if [scheme][URI.scheme] starts with `http`.
     */
    private
    val URI.isHttp: Boolean
        get() = scheme.startsWith("http")

    override fun onDynamicVersionSelection(requested: ModuleComponentSelector, expiry: Expiry, versions: Set<ModuleVersionIdentifier>) {
        // Only consider repositories serving at least one version of the requested module.
        // This is meant to avoid repetitively expiring cache entries due to a 404 response for the requested module metadata
        // from one of the configured repositories.
        if (versions.isEmpty()) return
        val expireAt = host.buildStartTime + expiry.keepFor.toMillis()
        onChangingValue(ConfigurationCacheFingerprint.DynamicDependencyVersion(requested.displayName, expireAt))
    }

    override fun onChangingModuleResolve(moduleId: ModuleComponentIdentifier, expiry: Expiry) {
        val expireAt = host.buildStartTime + expiry.keepFor.toMillis()
        onChangingValue(ConfigurationCacheFingerprint.ChangingModule(moduleId.displayName, expireAt))
    }

    private
    fun onChangingValue(changingValue: ConfigurationCacheFingerprint.ChangingDependencyResolutionValue) {
        synchronized(this) {
            if (closestChangingValue == null || closestChangingValue!!.expireAt > changingValue.expireAt) {
                closestChangingValue = changingValue
            }
        }
    }

    private
    fun isInputTrackingDisabled() = !inputTrackingState.isEnabledForCurrentThread()

    private
    fun isExecutingWork() = workExecutionTracker.currentTask.isPresent || workExecutionTracker.isExecutingTransformAction

    override fun fileObserved(file: File) {
        fileObserved(file, null)
    }

    override fun fileObserved(file: File, consumer: String?) {
        if (isInputTrackingDisabled()) {
            return
        }
        // Ignore consumer for now, only used by Gradle internals and so shouldn't appear in the report.
        captureFile(file)
    }

    override fun directoryChildrenObserved(file: File) {
        if (isInputTrackingDisabled()) {
            return
        }
        sink().captureDirectoryChildren(file)
    }

    override fun directoryChildrenObserved(directory: File, consumer: String?) {
        if (isInputTrackingDisabled() || isExecutingWork()) {
            return
        }
        sink().captureDirectoryChildren(directory)
        reportUniqueDirectoryChildrenInput(directory, consumer)
    }

    override fun fileSystemEntryObserved(file: File, consumer: String?) {
        if (isInputTrackingDisabled() || isExecutingWork()) {
            return
        }
        sink().captureFileSystemEntry(file)
        reportUniqueFileSystemEntryInput(file, consumer)
    }

    override fun systemPropertyRead(key: String, value: Any?, consumer: String?) {
        if (isInputTrackingDisabled()) {
            return
        }
        addSystemPropertyToFingerprint(key, value, consumer)
    }

    private
    fun addSystemPropertyToFingerprint(key: String, value: Any?, consumer: String? = null) {
        if (isSystemPropertyMutated(key)) {
            // Mutated values of the system properties are not part of the fingerprint, as their value is
            // set at the configuration time. Everything that reads a mutated property value should be saved
            // as a fixed value.
            return
        }
        val propertyValue =
            if (isSystemPropertyLoaded(key)) {
                // Loaded values of the system properties are loaded from gradle.properties but never mutated.
                // Thus, as a configuration input is an old value of property at load moment.
                environmentChangeTracker.getLoadedPropertyOldValue(key)
            } else {
                value
            }

        sink().systemPropertyRead(key, propertyValue)
        reportUniqueSystemPropertyInput(key, consumer)
    }

    override fun envVariableRead(key: String, value: String?, consumer: String?) {
        if (isInputTrackingDisabled()) {
            return
        }
        addEnvVariableToFingerprint(key, value, consumer)
    }

    private
    fun addEnvVariableToFingerprint(key: String, value: String?, consumer: String? = null) {
        sink().envVariableRead(key, value)
        reportUniqueEnvironmentVariableInput(key, consumer)
    }

    override fun fileOpened(file: File, consumer: String?) {
        if (isInputTrackingDisabled() || isExecutingWork()) {
            // Ignore files that are read as part of the task actions. These should really be task
            // inputs. Otherwise, we risk fingerprinting files such as:
            // - temporary files that will be gone at the end of the build.
            // - files in the output directory, for incremental tasks or tasks that remove stale outputs
            return
        }
        captureFile(file)
        reportUniqueFileInput(file, consumer)
    }

    override fun fileCollectionObserved(fileCollection: FileCollectionInternal) {
        if (isInputTrackingDisabled() || isExecutingWork()) {
            // See #fileOpened() above
            return
        }
        captureWorkInputs(host.location(null).toString()) { it(fileCollection) }
    }

    override fun systemPropertiesPrefixedBy(prefix: String, snapshot: Map<String, String?>) {
        if (isInputTrackingDisabled()) {
            return
        }
        addSystemPropertiesPrefixedByToFingerprint(prefix, snapshot)
    }

    private
    fun addSystemPropertiesPrefixedByToFingerprint(prefix: String, snapshot: Map<String, String?>) {
        val filteredSnapshot = snapshot.mapValues { e ->
            if (isSystemPropertyMutated(e.key)) {
                ConfigurationCacheFingerprint.SystemPropertiesPrefixedBy.IGNORED
            } else {
                e.value
            }
        }
        buildScopedSink.write(ConfigurationCacheFingerprint.SystemPropertiesPrefixedBy(prefix, filteredSnapshot))
    }

    override fun envVariablesPrefixedBy(prefix: String, snapshot: Map<String, String?>) {
        if (isInputTrackingDisabled()) {
            return
        }
        addEnvVariablesPrefixedByToFingerprint(prefix, snapshot)
    }

    private
    fun addEnvVariablesPrefixedByToFingerprint(prefix: String, snapshot: Map<String, String?>) {
        buildScopedSink.write(ConfigurationCacheFingerprint.EnvironmentVariablesPrefixedBy(prefix, snapshot))
    }

    override fun beforeValueObtained() {
        // Do not track additional inputs while computing a value of the value source.
        inputTrackingState.disableForCurrentThread()
    }

    override fun afterValueObtained() {
        inputTrackingState.restoreForCurrentThread()
    }

    override fun <T : Any, P : ValueSourceParameters> valueObtained(
        obtainedValue: ValueSourceProviderFactory.ValueListener.ObtainedValue<T, P>,
        source: org.gradle.api.provider.ValueSource<T, P>
    ) {
        obtainedValue.value.failure.ifPresent { exception ->
            host.reportProblem(exception) {
                text("failed to compute value with custom source ")
                reference(obtainedValue.valueSourceType)
                source.displayNameIfAvailable?.let {
                    text(" ($it)")
                }
                text(" with ")
                text(exception.toString())
            }
        }

        // TODO(https://github.com/gradle/gradle/issues/22494) ValueSources become part of the fingerprint even if they are only obtained
        //  inside other value sources. This is not really necessary for the correctness and causes excessive cache invalidation.
        when (val parameters = obtainedValue.valueSourceParameters) {
            is FileContentValueSource.Parameters -> {
                parameters.file.orNull?.asFile?.let { file ->
                    // TODO - consider the potential race condition in computing the hash code here
                    captureFile(file)
                    reportUniqueFileInput(file)
                }
            }

            is GradlePropertyValueSource.Parameters -> {
                // The set of Gradle properties is already an input
            }

            is GradlePropertiesPrefixedByValueSource.Parameters -> {
                // The set of Gradle properties is already an input
            }

            is SystemPropertyValueSource.Parameters -> {
                addSystemPropertyToFingerprint(parameters.propertyName.get(), obtainedValue.value.get())
            }

            is SystemPropertiesPrefixedByValueSource.Parameters -> {
                val prefix = parameters.prefix.get()
                addSystemPropertiesPrefixedByToFingerprint(prefix, obtainedValue.value.get().uncheckedCast())
                reportUniqueSystemPropertiesPrefixedByInput(prefix)
            }

            is EnvironmentVariableValueSource.Parameters -> {
                addEnvVariableToFingerprint(parameters.variableName.get(), obtainedValue.value.get() as? String)
            }

            is EnvironmentVariablesPrefixedByValueSource.Parameters -> {
                val prefix = parameters.prefix.get()
                addEnvVariablesPrefixedByToFingerprint(prefix, obtainedValue.value.get().uncheckedCast())
                reportUniqueEnvironmentVariablesPrefixedByInput(prefix)
            }

            is ProcessOutputValueSource.Parameters -> {
                sink().write(ValueSource(obtainedValue.uncheckedCast()))
                reportExternalProcessOutputRead(ProcessOutputValueSource.Parameters.getExecutable(parameters))
            }

            else -> {
                // Custom ValueSource implementations may fail to serialize here.
                // Writing with explicit trace helps to avoid attributing these failures to "Gradle runtime".
                // TODO(mlopatkin): can we do even better and pinpoint the exact stacktrace in case of failure?
                val trace = locationFor(null)
                sink().write(ValueSource(obtainedValue.uncheckedCast()), trace)
                reportUniqueValueSourceInput(
                    trace,
                    displayName = source.displayNameIfAvailable,
                    typeName = obtainedValue.valueSourceType.simpleName
                )
            }
        }
    }

    private
    val org.gradle.api.provider.ValueSource<*, *>.displayNameIfAvailable: String?
        get() = when (this) {
            is Describable -> displayName
            else -> null
        }

    private
    fun isSystemPropertyLoaded(key: String): Boolean {
        return environmentChangeTracker.isSystemPropertyLoaded(key)
    }

    private
    fun isSystemPropertyMutated(key: String): Boolean {
        return environmentChangeTracker.isSystemPropertyMutated(key)
    }

    override fun onScriptClassLoaded(source: ScriptSource, scriptClass: Class<*>) {
        source.resource.file?.let {
            captureFile(it)
        }
    }

    override fun onExecute(work: UnitOfWork, relevantBehaviors: EnumSet<InputBehavior>) {
        captureWorkInputs(work, relevantBehaviors)
    }

    private
    fun captureFile(file: File) {
        sink().captureFile(file)
    }

    private
    fun captureWorkInputs(work: UnitOfWork, relevantInputBehaviors: EnumSet<InputBehavior>) {
        captureWorkInputs(work.displayName) { visitStructure ->
            work.visitRegularInputs(object : InputVisitor {
                override fun visitInputFileProperty(propertyName: String, behavior: InputBehavior, value: InputFileValueSupplier) {
                    if (relevantInputBehaviors.contains(behavior)) {
                        visitStructure(value.files as FileCollectionInternal)
                    }
                }
            })
        }
    }

    private
    inline fun captureWorkInputs(workDisplayName: String, content: ((FileCollectionInternal) -> Unit) -> Unit) {
        val fileSystemInputs = simplify(content)
        sink().write(
            ConfigurationCacheFingerprint.WorkInputs(
                workDisplayName,
                fileSystemInputs,
                host.fingerprintOf(fileSystemInputs)
            )
        )
    }

    private
    inline fun simplify(content: ((FileCollectionInternal) -> Unit) -> Unit): FileCollectionInternal {
        val simplifyingVisitor = SimplifyingFileCollectionStructureVisitor(directoryFileTreeFactory, fileCollectionFactory)
        content {
            it.visitStructure(simplifyingVisitor)
        }
        return simplifyingVisitor.simplify()
    }

    fun <T> runCollectingFingerprintForProject(identityPath: Path, action: () -> T): T {
        val previous = projectForThread.get()
        val projectSink = sinksForProject.computeIfAbsent(identityPath) { ProjectScopedSink(host, identityPath, projectScopedWriter) }
        projectForThread.set(projectSink)
        try {
            return action()
        } finally {
            projectForThread.set(previous)
        }
    }

    override fun projectObserved(consumingProjectPath: Path?, targetProjectPath: Path) {
        if (consumingProjectPath != null) {
            onProjectDependency(consumingProjectPath, targetProjectPath)
        }
    }

    override fun onProjectReference(referrer: ProjectState, target: ProjectState) {
        if (referrer.identityPath == target.identityPath)
            return

        if (host.cacheIntermediateModels) {
            val dependency = ProjectSpecificFingerprint.CoupledProjects(referrer.identityPath, target.identityPath)
            if (projectDependencies.add(dependency)) {
                projectScopedWriter.write(dependency)
            }
        }
    }

    override fun onToolingModelDependency(consumer: ProjectState, target: ProjectState) {
        if (host.modelAsProjectDependency) {
            onProjectDependency(consumer.identityPath, target.identityPath)
        }
    }

    private
    fun onProjectDependency(consumerPath: Path, targetPath: Path) {
        if (host.cacheIntermediateModels) {
            val dependency = ProjectSpecificFingerprint.ProjectDependency(consumerPath, targetPath)
            if (projectDependencies.add(dependency)) {
                projectScopedWriter.write(dependency)
            }
        }
    }

    override fun flagRead(flag: FeatureFlag) {
        flag.systemPropertyName?.let { propertyName ->
            sink().systemPropertyRead(propertyName, System.getProperty(propertyName))
        }
    }

    fun append(fingerprint: ProjectSpecificFingerprint) {
        // TODO - should add to report as an input
        projectScopedWriter.write(fingerprint)
    }

    private
    fun sink(): Sink = projectForThread.get() ?: buildScopedSink

    /**
     * Transform the collection into a sequence of files or directory trees and remove dynamic behaviour
     */
    private
    class SimplifyingFileCollectionStructureVisitor(
        private
        val directoryFileTreeFactory: DirectoryFileTreeFactory,
        private
        val fileCollectionFactory: FileCollectionFactory
    ) : FileCollectionStructureVisitor {
        private
        val elements = mutableListOf<Any>()

        override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) {
            elements.addAll(contents)
        }

        override fun visitFileTree(root: File, patterns: PatternSet, fileTree: FileTreeInternal) {
            elements.add(directoryFileTreeFactory.create(root, patterns))
        }

        override fun visitFileTreeBackedByFile(file: File, fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) {
            elements.add(file)
        }

        fun simplify(): FileCollectionInternal = fileCollectionFactory.resolving(elements)
    }

    private
    fun reportUniqueValueSourceInput(trace: PropertyTrace, displayName: String?, typeName: String) {
        // We assume different types won't ever produce identical display names
        if (reportedValueSources.add(displayName ?: typeName)) {
            reportValueSourceInput(trace, displayName, typeName)
        }
    }

    private
    fun reportValueSourceInput(trace: PropertyTrace, displayName: String?, typeName: String) {
        reportInput(trace, documentationSection = null) {
            text("value from custom source ")
            reference(typeName)
            displayName?.let {
                text(", ")
                text(it)
            }
        }
    }

    private
    fun reportUniqueFileInput(file: File, consumer: String? = null) {
        if (reportedFiles.add(file)) {
            reportFileInput(file, consumer)
        }
    }

    private
    fun reportUniqueDirectoryChildrenInput(directory: File, consumer: String?) {
        if (reportedDirectories.add(directory)) {
            reportDirectoryContentInput(directory, consumer)
        }
    }

    private
    fun reportUniqueFileSystemEntryInput(file: File, consumer: String?) {
        if (reportedFileSystemEntries.add(file)) {
            reportFileSystemEntryInput(file, consumer)
        }
    }

    private
    fun reportFileInput(file: File, consumer: String?) {
        reportInput(consumer, null) {
            text("file ")
            reference(host.displayNameOf(file))
        }
    }

    private
    fun reportDirectoryContentInput(directory: File, consumer: String?) {
        reportInput(consumer, null) {
            text("directory content ")
            reference(host.displayNameOf(directory))
        }
    }

    private
    fun reportFileSystemEntryInput(file: File, consumer: String?) {
        reportInput(consumer, null) {
            text("file system entry ")
            reference(host.displayNameOf(file))
        }
    }

    private
    fun reportExternalProcessOutputRead(executable: String) {
        reportInput(consumer = null, documentationSection = DocumentationSection.RequirementsExternalProcess) {
            text("output of external process ")
            reference(executable)
        }
    }

    private
    fun reportUniqueSystemPropertyInput(key: String, consumer: String?) {
        if (undeclaredSystemProperties.add(key)) {
            reportSystemPropertyInput(key, consumer)
        }
    }

    private
    fun reportSystemPropertyInput(key: String, consumer: String?) {
        reportInput(consumer, DocumentationSection.RequirementsSysPropEnvVarRead) {
            text("system property ")
            reference(key)
        }
    }

    private
    fun reportUniqueSystemPropertiesPrefixedByInput(prefix: String) {
        if (systemPropertiesPrefixedBy.add(prefix)) {
            reportSystemPropertiesPrefixedByInput(prefix)
        }
    }

    private
    fun reportSystemPropertiesPrefixedByInput(prefix: String) {
        reportInput(null, DocumentationSection.RequirementsSysPropEnvVarRead) {
            if (prefix.isNotEmpty()) {
                text("system properties prefixed by ")
                reference(prefix)
            } else {
                text("system properties")
            }
        }
    }

    private
    fun reportUniqueEnvironmentVariableInput(key: String, consumer: String?) {
        if (undeclaredEnvironmentVariables.add(key)) {
            reportEnvironmentVariableInput(key, consumer)
        }
    }

    private
    fun reportEnvironmentVariableInput(key: String, consumer: String?) {
        reportInput(consumer, DocumentationSection.RequirementsSysPropEnvVarRead) {
            text("environment variable ")
            reference(key)
        }
    }

    private
    fun reportUniqueEnvironmentVariablesPrefixedByInput(prefix: String) {
        if (environmentVariablesPrefixedBy.add(prefix)) {
            reportEnvironmentVariablesPrefixedByInput(prefix)
        }
    }

    private
    fun reportEnvironmentVariablesPrefixedByInput(prefix: String) {
        reportInput(null, DocumentationSection.RequirementsSysPropEnvVarRead) {
            if (prefix.isNotEmpty()) {
                text("environment variables prefixed by ")
                reference(prefix)
            } else {
                text("environment variables")
            }
        }
    }

    private
    fun reportInput(
        consumer: String?,
        documentationSection: DocumentationSection?,
        messageBuilder: StructuredMessage.Builder.() -> Unit
    ) {
        reportInput(locationFor(consumer), documentationSection, messageBuilder)
    }

    private
    fun reportInput(
        trace: PropertyTrace,
        documentationSection: DocumentationSection?,
        messageBuilder: StructuredMessage.Builder.() -> Unit
    ) {
        host.reportInput(
            PropertyProblem(
                trace,
                StructuredMessage.build(messageBuilder),
                documentationSection = documentationSection
            )
        )
    }

    private
    fun locationFor(consumer: String?) = host.location(consumer)

    private
    abstract class Sink(
        private val host: Host
    ) {
        val capturedFiles: MutableSet<File> = newConcurrentHashSet()
        val capturedDirectories: MutableSet<File> = newConcurrentHashSet()
        val capturedFileSystemEntries: MutableSet<File> = newConcurrentHashSet()

        private
        val undeclaredSystemProperties = newConcurrentHashSet<String>()

        private
        val undeclaredEnvironmentVariables = newConcurrentHashSet<String>()

        private
        val remoteScriptsUris = newConcurrentHashSet<URI>()

        fun captureFile(file: File) {
            if (!capturedFiles.add(file)) {
                return
            }
            write(inputFile(file))
        }

        fun captureDirectoryChildren(file: File) {
            if (!capturedDirectories.add(file)) {
                return
            }
            write(ConfigurationCacheFingerprint.DirectoryChildren(file, host.hashCodeOfDirectoryChildrenNames(file)))
        }

        fun captureRemoteScript(uri: URI) {
            if (remoteScriptsUris.add(uri)) {
                write(ConfigurationCacheFingerprint.RemoteScript(uri))
            }
        }

        fun captureFileSystemEntry(file: File) {
            if (!capturedFileSystemEntries.add(file)) {
                return
            }
            write(inputFileSystemEntry(file))
        }

        fun systemPropertyRead(key: String, value: Any?) {
            if (undeclaredSystemProperties.add(key)) {
                write(ConfigurationCacheFingerprint.UndeclaredSystemProperty(key, value))
            }
        }

        fun envVariableRead(key: String, value: String?) {
            if (undeclaredEnvironmentVariables.add(key)) {
                write(ConfigurationCacheFingerprint.UndeclaredEnvironmentVariable(key, value))
            }
        }

        abstract fun write(value: ConfigurationCacheFingerprint, trace: PropertyTrace? = null)

        fun inputFile(file: File) =
            InputFile(
                file,
                host.hashCodeOf(file)
            )

        fun inputFileSystemEntry(file: File) =
            InputFileSystemEntry(file, fileSystemEntryType(file))
    }

    private
    class BuildScopedSink(
        host: Host,
        private val writer: ScopedFingerprintWriter<ConfigurationCacheFingerprint>
    ) : Sink(host) {
        override fun write(value: ConfigurationCacheFingerprint, trace: PropertyTrace?) {
            writer.write(value, trace)
        }

        fun initScripts(initScripts: List<File>) {
            capturedFiles.addAll(initScripts)
            write(
                ConfigurationCacheFingerprint.InitScripts(
                    initScripts.map(::inputFile)
                )
            )
        }
    }

    private
    class ProjectScopedSink(
        host: Host,
        private val project: Path,
        private val writer: ScopedFingerprintWriter<ProjectSpecificFingerprint>
    ) : Sink(host) {
        override fun write(value: ConfigurationCacheFingerprint, trace: PropertyTrace?) {
            writer.write(ProjectSpecificFingerprint.ProjectFingerprint(project, value), trace)
        }
    }

    override fun onScriptFileResolved(scriptFile: File) {
        fileObserved(scriptFile)
    }
}


internal
fun jvmFingerprint() = String.format(
    "%s|%s|%s",
    System.getProperty("java.vm.name"),
    System.getProperty("java.vm.vendor"),
    System.getProperty("java.vm.version")
)
