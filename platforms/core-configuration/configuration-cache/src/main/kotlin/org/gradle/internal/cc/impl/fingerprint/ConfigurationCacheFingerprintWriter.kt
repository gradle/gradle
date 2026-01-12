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

import com.google.common.collect.Sets.newConcurrentHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.gradle.api.Describable
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl.Expiry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ChangingValueDependencyResolutionListener
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileCollectionObservationListener
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.properties.GradlePropertiesListener
import org.gradle.api.internal.properties.GradlePropertyScope
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.internal.provider.sources.EnvironmentVariableValueSource
import org.gradle.api.internal.provider.sources.EnvironmentVariablesPrefixedByValueSource
import org.gradle.api.internal.provider.sources.FileContentValueSource
import org.gradle.api.internal.provider.sources.SystemPropertiesPrefixedByValueSource
import org.gradle.api.internal.provider.sources.SystemPropertyValueSource
import org.gradle.api.internal.provider.sources.process.ProcessOutputValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.util.PatternSet
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.groovy.scripts.internal.ScriptSourceListener
import org.gradle.initialization.buildsrc.BuildSrcDetector
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildoption.FeatureFlag
import org.gradle.internal.buildoption.FeatureFlagListener
import org.gradle.internal.cc.base.logger
import org.gradle.internal.cc.impl.CoupledProjectsListener
import org.gradle.internal.cc.impl.InputTrackingState
import org.gradle.internal.cc.impl.UndeclaredBuildInputListener
import org.gradle.internal.cc.impl.Workarounds
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprint.InputFile
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprint.InputFileSystemEntry
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprint.ValueSource
import org.gradle.internal.cc.impl.services.ConfigurationCacheEnvironment
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.execution.InputVisitor
import org.gradle.internal.execution.InputVisitor.InputFileValueSupplier
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkExecutionTracker
import org.gradle.internal.execution.WorkInputListener
import org.gradle.internal.execution.WorkInputListeners
import org.gradle.internal.extensions.core.fileSystemEntryType
import org.gradle.internal.extensions.core.uri
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.hash.HashCode
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.scripts.ScriptExecutionListener
import org.gradle.internal.scripts.ScriptFileResolvedListener
import org.gradle.internal.scripts.ScriptFileResolverListeners
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.service.scopes.ParallelListener
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.tooling.provider.model.internal.ToolingModelProjectDependencyListener
import org.gradle.util.Path
import java.io.Closeable
import java.io.File
import java.net.URI
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference


/**
 * A dispatcher for various fingerprint-related events.
 * It forwards the events into the [ConfigurationCacheFingerprintWriter] if the latter is active (== registered itself within this class).
 *
 * **DO NOT USE LISTENERMANAGER TO SEND EVENTS TO THIS CLASS!**.
 * See the implementation comments for the justification.
 */
@ServiceScope(Scope.BuildTree::class)
internal
class ConfigurationCacheFingerprintEventHandler(
    private val workInputListeners: WorkInputListeners,
    private val scriptFileResolverListeners: ScriptFileResolverListeners
) :
// For these listeners this class is the only "real" implementation.
// Event sources get our instance through ServiceRegistry.
    ChangingValueDependencyResolutionListener,
    ConfigurationCacheEnvironment.Listener,
    CoupledProjectsListener,
    FeatureFlagListener,
    FileCollectionObservationListener,
    FileResourceListener,
    GradlePropertiesListener,
    ScriptExecutionListener,
    ScriptSourceListener,
    ToolingModelProjectDependencyListener,
    UndeclaredBuildInputListener,
    ValueSourceProviderFactory.ComputationListener,
    ValueSourceProviderFactory.ValueListener,

    // These listeners have to be registered separately:
    ScriptFileResolvedListener, // 2 impl, another is some kind of broadcaster wrapper (Global) Events sent in global, but consumed here, thus the wrapper
    WorkInputListener, // 2 impl, separate registration (Global); sent in Build scope, consumed in BuildSession and BuildTree. Why the separate registrar?

    // Interfaces not involved with event dispatch.
    Closeable {

    // IMPORTANT: Why isn't this class use ListenerManager as the transport?
    // Broadcasting an event (calling a method on a listener) through LM involves holding a lock.
    // Not taking contention into account, that lock may cause deadlocks with other locks involved in processing events.
    // ListenerManager also forbids reentrancy (emitting an event while processing an event of the same type).
    // Some CC fingerprinting events are inherently reentrant, e.g. handling ValueSource.obtain() call may trigger another ValueSource to be obtained.
    // And last but not least, the versatility of the ListenerManager has a cost of 8-10 times more expensive broadcast compared to direct method call.
    // As for most of the events this is the only implementation, the price of the versatility isn't justified.
    @Volatile
    var delegate: ConfigurationCacheFingerprintWriter? = null

    init {
        workInputListeners.addListener(this)
        scriptFileResolverListeners.addListener(this)
    }

    override fun close() {
        workInputListeners.removeListener(this)
        scriptFileResolverListeners.removeListener(this)
    }

    override fun <T : Any, P : ValueSourceParameters> valueObtained(
        obtainedValue: ValueSourceProviderFactory.ValueListener.ObtainedValue<T, P>,
        source: org.gradle.api.provider.ValueSource<T, P>
    ) {
        delegate?.valueObtained(obtainedValue, source)
    }

    override fun beforeValueObtained() {
        delegate?.beforeValueObtained()
    }

    override fun afterValueObtained() {
        delegate?.afterValueObtained()
    }

    override fun onExecute(work: UnitOfWork, relevantBehaviors: EnumSet<InputBehavior>) {
        delegate?.onExecute(work, relevantBehaviors)
    }

    override fun onScriptClassLoaded(source: ScriptSource, scriptClass: Class<*>) {
        delegate?.onScriptClassLoaded(source)
    }

    override fun systemPropertyRead(key: String, value: Any?, consumer: String?) {
        delegate?.systemPropertyRead(key, value, consumer)
    }

    override fun systemPropertyChanged(key: Any, value: Any?, consumer: String?) {
        delegate?.systemPropertyChanged(key, value, consumer)
    }

    override fun systemPropertyRemoved(key: Any, consumer: String?) {
        delegate?.systemPropertyRemoved(key)
    }

    override fun systemPropertiesCleared(consumer: String?) {
        delegate?.systemPropertiesCleared()
    }

    override fun envVariableRead(key: String, value: String?, consumer: String?) {
        delegate?.envVariableRead(key, value, consumer)
    }

    override fun fileOpened(file: File, consumer: String?) {
        delegate?.fileOpened(file, consumer)
    }

    override fun fileObserved(file: File, consumer: String?) {
        delegate?.fileObserved(file)
    }

    override fun fileObserved(file: File) {
        delegate?.fileObserved(file)
    }

    override fun fileSystemEntryObserved(file: File, consumer: String?) {
        delegate?.fileSystemEntryObserved(file, consumer)
    }

    override fun directoryChildrenObserved(directory: File, consumer: String?) {
        delegate?.directoryChildrenObserved(directory, consumer)
    }

    override fun directoryChildrenObserved(file: File) {
        delegate?.directoryChildrenObserved(file)
    }

    override fun startParameterProjectPropertiesObserved() {
        delegate?.startParameterProjectPropertiesObserved()
    }

    override fun onDynamicVersionSelection(
        requested: ModuleComponentSelector,
        expiry: Expiry,
        versions: Set<ModuleVersionIdentifier>
    ) {
        delegate?.onDynamicVersionSelection(requested, expiry, versions)
    }

    override fun onChangingModuleResolve(moduleId: ModuleComponentIdentifier, expiry: Expiry) {
        delegate?.onChangingModuleResolve(moduleId, expiry)
    }

    override fun onProjectReference(referrer: ProjectState, target: ProjectState) {
        delegate?.onProjectReference(referrer, target)
    }

    override fun onToolingModelDependency(consumer: ProjectState, target: ProjectState) {
        delegate?.onToolingModelDependency(consumer, target)
    }

    override fun onScriptFileResolved(scriptFile: File) {
        delegate?.onScriptFileResolved(scriptFile)
    }

    override fun flagRead(flag: FeatureFlag) {
        delegate?.flagRead(flag)
    }

    override fun fileCollectionObserved(fileCollection: FileCollectionInternal) {
        delegate?.fileCollectionObserved(fileCollection)
    }

    override fun scriptSourceObserved(scriptSource: ScriptSource) {
        delegate?.scriptSourceObserved(scriptSource)
    }

    override fun onGradlePropertiesLoaded(propertyScope: GradlePropertyScope, propertiesDir: File) {
        delegate?.onGradlePropertiesLoaded(propertyScope, propertiesDir)
    }

    override fun onGradlePropertyAccess(propertyScope: GradlePropertyScope, propertyName: String, propertyValue: Any?) {
        delegate?.onGradlePropertyAccess(propertyScope, propertyName, propertyValue)
    }

    override fun onGradlePropertiesByPrefix(
        propertyScope: GradlePropertyScope,
        prefix: String,
        snapshot: Map<String, String>
    ) {
        delegate?.onGradlePropertiesByPrefix(propertyScope, prefix, snapshot)
    }

    override fun systemPropertiesPrefixedBy(prefix: String, snapshot: Map<String, String?>) {
        delegate?.systemPropertiesPrefixedBy(prefix, snapshot)
    }

    override fun systemProperty(name: String, value: String?) {
        delegate?.systemProperty(name, value)
    }

    override fun envVariablesPrefixedBy(prefix: String, snapshot: Map<String, String?>) {
        delegate?.envVariablesPrefixedBy(prefix, snapshot)
    }

    override fun envVariable(name: String, value: String?) {
        delegate?.envVariable(name, value)
    }
}

@Suppress("LargeClass")
@ParallelListener
internal
class ConfigurationCacheFingerprintWriter(
    private val host: Host,
    buildScopedContext: CloseableWriteContext,
    projectScopedContext: CloseableWriteContext,
    private val fileCollectionFactory: FileCollectionFactory,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val workExecutionTracker: WorkExecutionTracker,
    private val inputTrackingState: InputTrackingState,
    private val buildStateRegistry: BuildStateRegistry,
) {

    interface Host {
        val isEncrypted: Boolean
        val encryptionKeyHashCode: HashCode
        val isFineGrainedPropertyTracking: Boolean
        val startParameterProperties: Map<String, Any?>
        val gradleUserHomeDir: File
        val allInitScripts: List<File>
        val buildStartTime: Long
        val cacheIntermediateModels: Boolean
        val modelAsProjectDependency: Boolean
        val ignoreInputsDuringConfigurationCacheStore: Boolean
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

    private
    val propertyTracking: PropertyTracking

    // set to null, once the snapshot has been written, if ever
    private
    val startParameterProjectProperties: AtomicReference<Map<String, Any?>?>

    init {
        val isFineGrainedPropertyTracking = host.isFineGrainedPropertyTracking
        propertyTracking = when {
            isFineGrainedPropertyTracking -> FineGrainedPropertyTracking()
            else -> {
                logger.info("Configuration Cache fine-grained property tracking is disabled.")
                NoPropertyTracking
            }
        }
        buildScopedSink.initScripts(host.allInitScripts)
        buildScopedSink.write(
            ConfigurationCacheFingerprint.GradleEnvironment(
                host.gradleUserHomeDir,
                jvmFingerprint(),
                host.ignoreInputsDuringConfigurationCacheStore,
                host.instrumentationAgentUsed,
                host.ignoredFileSystemCheckInputs
            )
        )

        // defensive copy, since the original state is mutable
        val startParameterPropertiesSnapshot = host.startParameterProperties.toMap()
        startParameterProjectProperties = if (isFineGrainedPropertyTracking) {
            AtomicReference(startParameterPropertiesSnapshot)
        } else {
            addStartParameterProjectPropertiesToFingerprint(startParameterPropertiesSnapshot)
            AtomicReference(null)
        }
    }

    private
    sealed interface PropertyTracking {

        fun shouldTrackPropertyAccess(propertyScope: GradlePropertyScope, propertyName: String): Boolean

        fun shouldTrackPropertiesByPrefix(propertyScope: GradlePropertyScope, prefix: String): Boolean
    }

    private
    object NoPropertyTracking : PropertyTracking {
        override fun shouldTrackPropertyAccess(propertyScope: GradlePropertyScope, propertyName: String): Boolean =
            false

        override fun shouldTrackPropertiesByPrefix(propertyScope: GradlePropertyScope, prefix: String): Boolean =
            false
    }

    private
    class FineGrainedPropertyTracking : PropertyTracking {

        private
        val gradleProperties = ConcurrentHashMap<GradlePropertyScope, MutableSet<String>>()

        private
        val gradlePropertiesByPrefix = ConcurrentHashMap<GradlePropertyScope, MutableSet<String>>()

        override fun shouldTrackPropertyAccess(propertyScope: GradlePropertyScope, propertyName: String): Boolean =
            (shouldTrackGradlePropertyInput(gradleProperties, propertyScope, propertyName)
                && !Workarounds.isIgnoredStartParameterProperty(propertyName))

        override fun shouldTrackPropertiesByPrefix(propertyScope: GradlePropertyScope, prefix: String): Boolean =
            shouldTrackGradlePropertyInput(gradlePropertiesByPrefix, propertyScope, prefix)

        private
        fun shouldTrackGradlePropertyInput(
            keysPerScope: ConcurrentHashMap<GradlePropertyScope, MutableSet<String>>,
            propertyScope: GradlePropertyScope,
            propertyKey: String
        ): Boolean = keysPerScope
            .computeIfAbsent(propertyScope) { newConcurrentHashSet() }
            .add(propertyKey)
    }

    /**
     * Stops all writers.
     *
     * **MUST ALWAYS BE CALLED**
     */
    fun close() {
        synchronized(this) {
            captureBuildSrcPresence()
            closestChangingValue?.let {
                buildScopedSink.write(it)
            }
        }
        CompositeStoppable.stoppable(buildScopedWriter, projectScopedWriter).stop()
    }

    private
    fun captureBuildSrcPresence() {
        buildStateRegistry.visitBuilds { buildState ->
            val candidateBuildSrc = File(buildState.buildRootDir, SettingsInternal.BUILD_SRC)
            val valid = BuildSrcDetector.isValidBuildSrcBuild(candidateBuildSrc)
            if (!valid) {
                buildScopedSink.write(ConfigurationCacheFingerprint.MissingBuildSrcDir(candidateBuildSrc))
            }
        }
    }

    private
    fun addStartParameterProjectPropertiesToFingerprint(startParameterPropertiesSnapshot: Map<String, Any?>) {
        buildScopedSink.write(ConfigurationCacheFingerprint.StartParameterProjectProperties(startParameterPropertiesSnapshot))
    }

    fun scriptSourceObserved(scriptSource: ScriptSource) {
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

    fun onDynamicVersionSelection(requested: ModuleComponentSelector, expiry: Expiry, versions: Set<ModuleVersionIdentifier>) {
        // Only consider repositories serving at least one version of the requested module.
        // This is meant to avoid repetitively expiring cache entries due to a 404 response for the requested module metadata
        // from one of the configured repositories.
        if (versions.isEmpty()) return
        val expireAt = host.buildStartTime + expiry.keepFor.toMillis()
        onChangingValue(ConfigurationCacheFingerprint.DynamicDependencyVersion(requested.displayName, expireAt))
    }

    fun onChangingModuleResolve(moduleId: ModuleComponentIdentifier, expiry: Expiry) {
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
    fun isExecutingWork() = workExecutionTracker.isExecutingTaskOrTransformAction

    fun fileObserved(file: File) {
        if (isInputTrackingDisabled()) {
            return
        }
        // Ignore consumer for now, only used by Gradle internals and so shouldn't appear in the report.
        captureFile(file)
    }

    fun directoryChildrenObserved(file: File) {
        if (isInputTrackingDisabled()) {
            return
        }
        sink().captureDirectoryChildren(file)
    }

    fun directoryChildrenObserved(directory: File, consumer: String?) {
        if (isInputTrackingDisabled() || isExecutingWork()) {
            return
        }
        sink().captureDirectoryChildren(directory)
        reportUniqueDirectoryChildrenInput(directory, consumer)
    }

    fun fileSystemEntryObserved(file: File, consumer: String?) {
        if (isInputTrackingDisabled() || isExecutingWork()) {
            return
        }
        sink().captureFileSystemEntry(file)
        reportUniqueFileSystemEntryInput(file, consumer)
    }

    fun systemPropertyChanged(key: Any, value: Any?, consumer: String?) {
        sink().systemPropertyChanged(key, value, locationFor(consumer))
    }

    fun systemPropertyRemoved(key: Any) {
        sink().systemPropertyRemoved(key)
    }

    fun systemPropertiesCleared() {
        sink().systemPropertiesCleared()
    }

    fun systemPropertyRead(key: String, value: Any?, consumer: String?) {
        if (isInputTrackingDisabled()) {
            return
        }
        addSystemPropertyToFingerprint(key, value, consumer)
    }

    fun startParameterProjectPropertiesObserved() {
        startParameterProjectProperties.getAndSet(null)?.let {
            addStartParameterProjectPropertiesToFingerprint(it)
        }
    }

    private
    fun addSystemPropertyToFingerprint(key: String, value: Any?, consumer: String? = null) {
        sink().systemPropertyRead(key, value)
        reportUniqueSystemPropertyInput(key, consumer)
    }

    fun envVariableRead(key: String, value: String?, consumer: String?) {
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

    fun fileOpened(file: File, consumer: String?) {
        if (isInputTrackingDisabled() || isExecutingWork()) {
            // Ignore files that are read as part of the task actions. These should really be task
            // inputs. Otherwise, we risk fingerprinting files such as
            // - temporary files that will be gone at the end of the build.
            // - files in the output directory, for incremental tasks or tasks that remove stale outputs
            return
        }
        captureFile(file)
        reportUniqueFileInput(file, consumer)
    }

    fun fileCollectionObserved(fileCollection: FileCollectionInternal) {
        if (isInputTrackingDisabled() || isExecutingWork()) {
            // See #fileOpened() above
            return
        }
        captureWorkInputs(host.location(null).toString()) { it(fileCollection) }
    }

    fun systemPropertiesPrefixedBy(prefix: String, snapshot: Map<String, String?>) {
        if (isInputTrackingDisabled()) {
            return
        }
        addSystemPropertiesPrefixedByToFingerprint(prefix, snapshot)
    }

    fun systemProperty(name: String, value: String?) {
        systemPropertyRead(name, value, null)
    }

    private
    fun addSystemPropertiesPrefixedByToFingerprint(prefix: String, snapshot: Map<String, String?>) {
        buildScopedSink.write(ConfigurationCacheFingerprint.SystemPropertiesPrefixedBy(prefix, snapshot))
    }

    fun envVariablesPrefixedBy(prefix: String, snapshot: Map<String, String?>) {
        if (isInputTrackingDisabled()) {
            return
        }
        addEnvVariablesPrefixedByToFingerprint(prefix, snapshot)
    }

    fun envVariable(name: String, value: String?) {
        envVariableRead(name, value, null)
    }

    private
    fun addEnvVariablesPrefixedByToFingerprint(prefix: String, snapshot: Map<String, String?>) {
        buildScopedSink.write(ConfigurationCacheFingerprint.EnvironmentVariablesPrefixedBy(prefix, snapshot))
    }

    fun beforeValueObtained() {
        // Do not track additional inputs while computing a value of the value source.
        inputTrackingState.disableForCurrentThread()
    }

    fun afterValueObtained() {
        inputTrackingState.restoreForCurrentThread()
    }

    fun <T : Any, P : ValueSourceParameters> valueObtained(
        obtainedValue: ValueSourceProviderFactory.ValueListener.ObtainedValue<T, P>,
        source: org.gradle.api.provider.ValueSource<T, P>
    ) {
        obtainedValue.value.failure.ifPresent { exception: Throwable ->
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

            is SystemPropertyValueSource.Parameters -> {
                addSystemPropertyToFingerprint(parameters.propertyName.get(), obtainedValue.value.get())
            }

            is SystemPropertiesPrefixedByValueSource.Parameters -> {
                val prefix = parameters.prefix.get()
                addSystemPropertiesPrefixedByToFingerprint(
                    prefix,
                    obtainedValue.value.get()?.uncheckedCast()
                        ?: emptyMap()
                )
                reportUniqueSystemPropertiesPrefixedByInput(prefix)
            }

            is EnvironmentVariableValueSource.Parameters -> {
                addEnvVariableToFingerprint(
                    parameters.variableName.get(),
                    obtainedValue.value.get() as? String
                )
            }

            is EnvironmentVariablesPrefixedByValueSource.Parameters -> {
                val prefix = parameters.prefix.get()
                addEnvVariablesPrefixedByToFingerprint(
                    prefix,
                    obtainedValue.value.get()?.uncheckedCast()
                        ?: emptyMap()
                )
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

    fun onScriptClassLoaded(source: ScriptSource) {
        source.resource.file?.let {
            captureFile(it)
        }
    }

    fun onExecute(work: UnitOfWork, relevantBehaviors: EnumSet<InputBehavior>) {
        captureWorkInputs(work, relevantBehaviors)
    }

    private
    fun captureFile(file: File) {
        sink().captureFile(file)
    }

    private
    fun captureWorkInputs(work: UnitOfWork, relevantInputBehaviors: EnumSet<InputBehavior>) {
        captureWorkInputs(work.displayName) { visitStructure ->
            work.visitMutableInputs(object : InputVisitor {
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

    fun <T> runCollectingFingerprintForProject(project: ProjectIdentity, keepAlive: Boolean, action: () -> T): T {
        val previous = projectForThread.get()
        val projectSink = sinksForProject.computeIfAbsent(project.buildTreePath) {
            ProjectScopedSink(host, project, projectScopedWriter)
        }
        projectForThread.set(projectSink)
        try {
            return action()
        } finally {
            if (!keepAlive) {
                sinksForProject.remove(project.buildTreePath)
            }
            projectForThread.set(previous)
        }
    }

    fun projectObserved(consumingProjectPath: Path?, targetProjectPath: Path) {
        if (consumingProjectPath != null) {
            onProjectDependency(consumingProjectPath, targetProjectPath)
        }
    }

    fun onProjectReference(referrer: ProjectState, target: ProjectState) {
        if (referrer.identityPath == target.identityPath)
            return

        if (host.cacheIntermediateModels) {
            val dependency = ProjectSpecificFingerprint.CoupledProjects(referrer.identityPath, target.identityPath)
            if (projectDependencies.add(dependency)) {
                projectScopedWriter.write(dependency)
            }
        }
    }

    fun onToolingModelDependency(consumer: ProjectState, target: ProjectState) {
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

    fun flagRead(flag: FeatureFlag) {
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

        fun systemPropertyChanged(key: Any, value: Any?, trace: PropertyTrace) {
            undeclaredSystemProperties.remove(key)
            write(ConfigurationCacheFingerprint.SystemPropertyChanged(key, value), trace)
        }

        fun systemPropertyRemoved(key: Any) {
            undeclaredSystemProperties.remove(key)
            write(ConfigurationCacheFingerprint.SystemPropertyRemoved(key))
        }

        fun systemPropertiesCleared() {
            undeclaredSystemProperties.clear()
            write(ConfigurationCacheFingerprint.SystemPropertiesCleared)
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
        project: ProjectIdentity,
        private val writer: ScopedFingerprintWriter<ProjectSpecificFingerprint>
    ) : Sink(host) {

        private
        val projectIdentityPath = project.buildTreePath

        init {
            writer.write(
                ProjectSpecificFingerprint.ProjectIdentity(
                    project.buildTreePath,
                    project.buildPath,
                    project.projectPath
                )
            )
        }

        override fun write(value: ConfigurationCacheFingerprint, trace: PropertyTrace?) {
            writer.write(ProjectSpecificFingerprint.ProjectFingerprint(projectIdentityPath, value), trace)
        }
    }

    fun onScriptFileResolved(scriptFile: File) {
        fileObserved(scriptFile)
    }

    fun onGradlePropertiesLoaded(
        propertyScope: GradlePropertyScope,
        propertiesDir: File
    ) {
        buildScopedSink.write(
            ConfigurationCacheFingerprint.GradlePropertiesLoaded(
                propertyScope,
                propertiesDir
            )
        )
    }

    fun onGradlePropertiesByPrefix(
        propertyScope: GradlePropertyScope,
        prefix: String,
        snapshot: Map<String, String>
    ) {
        if (propertyTracking.shouldTrackPropertiesByPrefix(propertyScope, prefix)) {
            // TODO:isolated consider tracking per project
            buildScopedSink.write(
                ConfigurationCacheFingerprint.GradlePropertiesPrefixedBy(
                    propertyScope,
                    prefix,
                    snapshot
                )
            )
            reportGradlePropertiesByPrefixInput(propertyScope, prefix)
        }
    }

    fun onGradlePropertyAccess(
        propertyScope: GradlePropertyScope,
        propertyName: String,
        propertyValue: Any?
    ) {
        if (!Workarounds.isIgnoredStartParameterProperty(propertyName)
            && propertyTracking.shouldTrackPropertyAccess(propertyScope, propertyName)
        ) {
            // TODO:isolated could tracking per project
            buildScopedSink.write(
                ConfigurationCacheFingerprint.GradleProperty(
                    propertyScope,
                    propertyName,
                    propertyValue
                )
            )
            reportGradlePropertyInput(propertyScope, propertyName)
        }
    }

    private
    fun shouldTrackGradlePropertyInput(
        keysPerScope: ConcurrentHashMap<GradlePropertyScope, MutableSet<String>>,
        propertyScope: GradlePropertyScope,
        propertyKey: String
    ): Boolean = keysPerScope
        .computeIfAbsent(propertyScope) {
            ObjectOpenHashSet()
        }.let { keys ->
            synchronized(keys) {
                keys.add(propertyKey)
            }
        }

    private
    fun reportGradlePropertyInput(
        propertyScope: GradlePropertyScope,
        propertyName: String,
        consumer: String? = null
    ) {
        if (propertyName.startsWith("org.gradle.")) {
            // Don't include builtin Gradle properties in the report
            return
        }
        val location = locationFor(consumer)
        if (location === PropertyTrace.Unknown || location === PropertyTrace.Gradle) {
            // Don't include property accesses coming from the Gradle runtime itself (e.g., version, group, buildDir, etc.)
            return
        }
        reportInput(scopedLocation(propertyScope, location), null) {
            text("Gradle property ")
            reference(propertyName)
        }
    }

    private
    fun reportGradlePropertiesByPrefixInput(
        propertyScope: GradlePropertyScope,
        prefix: String,
        consumer: String? = null
    ) {
        val location = locationFor(consumer)
        if (location === PropertyTrace.Unknown || location === PropertyTrace.Gradle) {
            // Don't include property accesses coming from the Gradle runtime itself (e.g., systemProp.*)
            return
        }
        reportInput(scopedLocation(propertyScope, location), null) {
            text("Gradle property ") // To avoid introducing a separate subtree in the report
            reference("$prefix*")
        }
    }

    private
    fun scopedLocation(
        propertyScope: GradlePropertyScope,
        location: PropertyTrace
    ) = PropertyTrace.Project(
        path = when (propertyScope) {
            is GradlePropertyScope.Project -> propertyScope.projectIdentity.buildTreePath.asString()
            is GradlePropertyScope.Build -> propertyScope.buildIdentifier.buildPath
            else -> error("Unexpected property scope $propertyScope")
        },
        trace = location
    )
}


internal
fun jvmFingerprint() =
    listOf(
        System.getProperty("java.vm.name"),
        System.getProperty("java.vm.vendor"),
        System.getProperty("java.vm.version")
    ).joinToString(separator = "|")
