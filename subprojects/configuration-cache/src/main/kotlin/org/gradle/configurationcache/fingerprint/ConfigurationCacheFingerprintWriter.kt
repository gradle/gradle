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

import com.google.common.collect.Maps.newConcurrentMap
import com.google.common.collect.Sets.newConcurrentHashSet
import org.gradle.api.Describable
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.ProjectDependencyObservedListener
import org.gradle.api.internal.artifacts.configurations.dynamicversion.Expiry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ChangingValueDependencyResolutionListener
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedProjectConfiguration
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.sources.EnvironmentVariableValueSource
import org.gradle.api.internal.provider.sources.EnvironmentVariablesPrefixedByValueSource
import org.gradle.api.internal.provider.sources.FileContentValueSource
import org.gradle.api.internal.provider.sources.GradlePropertyValueSource
import org.gradle.api.internal.provider.sources.SystemPropertiesPrefixedByValueSource
import org.gradle.api.internal.provider.sources.SystemPropertyValueSource
import org.gradle.api.internal.provider.sources.process.ProcessOutputValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.util.PatternSet
import org.gradle.configurationcache.CoupledProjectsListener
import org.gradle.configurationcache.UndeclaredBuildInputListener
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint.InputFile
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprint.ValueSource
import org.gradle.configurationcache.problems.DocumentationSection
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.problems.StructuredMessage
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.services.ConfigurationCacheEnvironment
import org.gradle.configurationcache.services.EnvironmentChangeTracker
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.execution.TaskExecutionTracker
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkInputListener
import org.gradle.internal.execution.fingerprint.InputFingerprinter
import org.gradle.internal.execution.fingerprint.InputFingerprinter.InputVisitor
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.util.Path
import java.io.File
import java.util.EnumSet
import kotlin.reflect.KProperty


internal
class ConfigurationCacheFingerprintWriter(
    private val host: Host,
    buildScopedContext: DefaultWriteContext,
    projectScopedContext: DefaultWriteContext,
    private val fileCollectionFactory: FileCollectionFactory,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val taskExecutionTracker: TaskExecutionTracker,
    private val environmentChangeTracker: EnvironmentChangeTracker,
) : ValueSourceProviderFactory.ValueListener,
    ValueSourceProviderFactory.ComputationListener,
    WorkInputListener,
    ScriptExecutionListener,
    UndeclaredBuildInputListener,
    ChangingValueDependencyResolutionListener,
    ProjectDependencyObservedListener,
    CoupledProjectsListener,
    FileResourceListener,
    ConfigurationCacheEnvironment.Listener {

    interface Host {
        val gradleUserHomeDir: File
        val allInitScripts: List<File>
        val startParameterProperties: Map<String, Any?>
        val buildStartTime: Long
        val cacheIntermediateModels: Boolean
        fun fingerprintOf(fileCollection: FileCollectionInternal): HashCode
        fun hashCodeOf(file: File): HashCode?
        fun displayNameOf(file: File): String
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
    val sinksForProject = newConcurrentMap<Path, ProjectScopedSink>()

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
    val reportedValueSources = newConcurrentHashSet<String>()

    private
    var closestChangingValue: ConfigurationCacheFingerprint.ChangingDependencyResolutionValue? = null

    private
    var inputTrackingDisabledForThread by ThreadLocal.withInitial { false }

    init {
        val initScripts = host.allInitScripts
        buildScopedSink.initScripts(initScripts)
        buildScopedSink.write(
            ConfigurationCacheFingerprint.GradleEnvironment(
                host.gradleUserHomeDir,
                jvmFingerprint(),
                host.startParameterProperties
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

    override fun fileObserved(file: File) {
        if (inputTrackingDisabledForThread) {
            return
        }
        captureFile(file)
    }

    override fun systemPropertyRead(key: String, value: Any?, consumer: String?) {
        if (inputTrackingDisabledForThread || isSystemPropertyMutated(key)) {
            return
        }
        sink().systemPropertyRead(key, value)
        reportUniqueSystemPropertyInput(key, consumer)
    }

    override fun envVariableRead(key: String, value: String?, consumer: String?) {
        if (inputTrackingDisabledForThread) {
            return
        }
        sink().envVariableRead(key, value)
        reportUniqueEnvironmentVariableInput(key, consumer)
    }

    override fun fileOpened(file: File, consumer: String?) {
        if (inputTrackingDisabledForThread || taskExecutionTracker.currentTask.isPresent) {
            // Ignore files that are read as part of the task actions. These should really be task
            // inputs. Otherwise, we risk fingerprinting temporary files that will be gone at the
            // end of the build.
            return
        }
        captureFile(file)
        reportUniqueFileInput(file, consumer)
    }

    override fun fileCollectionObserved(fileCollection: FileCollection, consumer: String) {
        if (inputTrackingDisabledForThread) {
            return
        }
        captureWorkInputs(consumer) { it(fileCollection as FileCollectionInternal) }
    }

    override fun systemPropertiesPrefixedBy(prefix: String, snapshot: Map<String, String?>) {
        if (inputTrackingDisabledForThread) {
            return
        }
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
        if (inputTrackingDisabledForThread) {
            return
        }
        buildScopedSink.write(ConfigurationCacheFingerprint.EnvironmentVariablesPrefixedBy(prefix, snapshot))
    }

    override fun beforeValueObtained() {
        inputTrackingDisabledForThread = true
    }

    override fun afterValueObtained() {
        inputTrackingDisabledForThread = false
    }

    override fun <T : Any, P : ValueSourceParameters> valueObtained(
        obtainedValue: ValueSourceProviderFactory.ValueListener.ObtainedValue<T, P>,
        source: org.gradle.api.provider.ValueSource<T, P>
    ) {
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
            is SystemPropertyValueSource.Parameters -> {
                systemPropertyRead(parameters.propertyName.get(), obtainedValue.value.get(), null)
            }
            is SystemPropertiesPrefixedByValueSource.Parameters -> {
                val prefix = parameters.prefix.get()
                systemPropertiesPrefixedBy(prefix, obtainedValue.value.get().uncheckedCast())
                reportUniqueSystemPropertiesPrefixedByInput(prefix)
            }
            is EnvironmentVariableValueSource.Parameters -> {
                envVariableRead(parameters.variableName.get(), obtainedValue.value.get() as? String, null)
            }
            is EnvironmentVariablesPrefixedByValueSource.Parameters -> {
                val prefix = parameters.prefix.get()
                envVariablesPrefixedBy(prefix, obtainedValue.value.get().uncheckedCast())
                reportUniqueEnvironmentVariablesPrefixedByInput(prefix)
            }
            is ProcessOutputValueSource.Parameters -> {
                sink().write(ValueSource(obtainedValue.uncheckedCast()))
                reportExternalProcessOutputRead(ProcessOutputValueSource.Parameters.getExecutable(parameters))
            }
            else -> {
                sink().write(ValueSource(obtainedValue.uncheckedCast()))
                reportUniqueValueSourceInput(
                    displayName = when (source) {
                        is Describable -> source.displayName
                        else -> null
                    },
                    typeName = obtainedValue.valueSourceType.simpleName
                )
            }
        }
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

    override fun onExecute(work: UnitOfWork, relevantTypes: EnumSet<InputFingerprinter.InputPropertyType>) {
        captureWorkInputs(work, relevantTypes)
    }

    private
    fun captureFile(file: File) {
        sink().captureFile(file)
    }

    private
    fun captureWorkInputs(work: UnitOfWork, relevantTypes: EnumSet<InputFingerprinter.InputPropertyType>) {
        captureWorkInputs(work.displayName) { visitStructure ->
            work.visitRegularInputs(object : InputVisitor {
                override fun visitInputFileProperty(propertyName: String, type: InputFingerprinter.InputPropertyType, value: InputFingerprinter.FileValueSupplier) {
                    if (relevantTypes.contains(type)) {
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

    fun <T> collectFingerprintForProject(identityPath: Path, action: () -> T): T {
        val previous = projectForThread.get()
        val projectSink = sinksForProject.computeIfAbsent(identityPath) { ProjectScopedSink(host, identityPath, projectScopedWriter) }
        projectForThread.set(projectSink)
        try {
            return action()
        } finally {
            projectForThread.set(previous)
        }
    }

    override fun dependencyObserved(consumingProject: ProjectState?, targetProject: ProjectState, requestedState: ConfigurationInternal.InternalState, target: ResolvedProjectConfiguration) {
        if (host.cacheIntermediateModels && consumingProject != null) {
            val dependency = ProjectSpecificFingerprint.ProjectDependency(consumingProject.identityPath, targetProject.identityPath)
            if (projectDependencies.add(dependency)) {
                projectScopedWriter.write(dependency)
            }
        }
    }

    override fun onProjectReference(referrer: ProjectState, target: ProjectState) {
        if (host.cacheIntermediateModels) {
            val dependency = ProjectSpecificFingerprint.CoupledProjects(referrer.identityPath, target.identityPath)
            if (projectDependencies.add(dependency)) {
                projectScopedWriter.write(dependency)
            }
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

        override fun visitGenericFileTree(fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) {
            elements.addAll(fileTree)
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
    fun reportUniqueValueSourceInput(displayName: String?, typeName: String) {
        // We assume different types won't ever produce identical display names
        if (reportedValueSources.add(displayName ?: typeName)) {
            reportValueSourceInput(displayName, typeName)
        }
    }

    private
    fun reportValueSourceInput(displayName: String?, typeName: String) {
        reportInput(consumer = null, documentationSection = null) {
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
    fun reportFileInput(file: File, consumer: String?) {
        reportInput(consumer, null) {
            text("file ")
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
        host.reportInput(
            PropertyProblem(
                locationFor(consumer),
                StructuredMessage.build(messageBuilder),
                null,
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

        private
        val undeclaredSystemProperties = newConcurrentHashSet<String>()

        private
        val undeclaredEnvironmentVariables = newConcurrentHashSet<String>()

        fun captureFile(file: File) {
            if (!capturedFiles.add(file)) {
                return
            }
            write(inputFile(file))
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

        abstract fun write(value: ConfigurationCacheFingerprint)

        fun inputFile(file: File) =
            InputFile(
                file,
                host.hashCodeOf(file)
            )
    }

    private
    class BuildScopedSink(
        host: Host,
        private val writer: ScopedFingerprintWriter<ConfigurationCacheFingerprint>
    ) : Sink(host) {
        override fun write(value: ConfigurationCacheFingerprint) {
            writer.write(value)
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
        override fun write(value: ConfigurationCacheFingerprint) {
            writer.write(ProjectSpecificFingerprint.ProjectFingerprint(project, value))
        }
    }
}


internal
fun jvmFingerprint() = String.format(
    "%s|%s|%s",
    System.getProperty("java.vm.name"),
    System.getProperty("java.vm.vendor"),
    System.getProperty("java.vm.version")
)


private
operator fun <T> ThreadLocal<T>.getValue(thisRef: Any?, property: KProperty<*>) = get()


private
operator fun <T> ThreadLocal<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
