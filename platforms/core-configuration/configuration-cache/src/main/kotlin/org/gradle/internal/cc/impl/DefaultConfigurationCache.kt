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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.provider.DefaultConfigurationTimeBarrier
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.logging.LogLevel
import org.gradle.configurationcache.ModelStoreResult
import org.gradle.configurationcache.WorkGraphLoadResult
import org.gradle.configurationcache.WorkGraphStoreResult
import org.gradle.configurationcache.withFingerprintCheckOperations
import org.gradle.configurationcache.withModelLoadOperation
import org.gradle.configurationcache.withModelStoreOperation
import org.gradle.configurationcache.withWorkGraphLoadOperation
import org.gradle.configurationcache.withWorkGraphStoreOperation
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildTreeModelSideEffect
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.cc.base.logger
import org.gradle.internal.cc.base.serialize.HostServiceProvider
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.base.serialize.service
import org.gradle.internal.cc.impl.cacheentry.EntryDetails
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.cc.impl.metadata.ProjectMetadataController
import org.gradle.internal.cc.impl.models.BuildTreeModelSideEffectStore
import org.gradle.internal.cc.impl.models.IntermediateModelController
import org.gradle.internal.cc.impl.problems.ConfigurationCacheProblems
import org.gradle.internal.cc.impl.services.ConfigurationCacheBuildTreeModelSideEffectExecutor
import org.gradle.internal.cc.impl.services.DeferredRootBuildGradle
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.configuration.inputs.InstrumentedInputs
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.extensions.core.get
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.serialize.graph.CloseableWriteContext
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier
import org.gradle.util.Path
import java.io.File
import java.io.OutputStream
import java.util.Locale


@Suppress("LongParameterList")
class DefaultConfigurationCache internal constructor(
    private val startParameter: ConfigurationCacheStartParameter,
    private val cacheKey: ConfigurationCacheKey,
    private val problems: ConfigurationCacheProblems,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val cacheRepository: ConfigurationCacheRepository,
    private val instrumentedInputAccessListener: InstrumentedInputAccessListener,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val buildActionModelRequirements: BuildActionModelRequirements,
    private val buildStateRegistry: BuildStateRegistry,
    private val virtualFileSystem: BuildLifecycleAwareVirtualFileSystem,
    private val buildOperationRunner: BuildOperationRunner,
    private val cacheFingerprintController: ConfigurationCacheFingerprintController,
    private val resolveStateFactory: LocalComponentGraphResolveStateFactory,
    /**
     * Force the [FileSystemAccess] service to be initialized as it initializes important static state.
     */
    @Suppress("unused")
    private val fileSystemAccess: FileSystemAccess,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    private val modelSideEffectExecutor: ConfigurationCacheBuildTreeModelSideEffectExecutor,
    private val deferredRootBuildGradle: DeferredRootBuildGradle
) : BuildTreeConfigurationCache, Stoppable {

    private
    lateinit var cacheAction: ConfigurationCacheAction

    // Have one or more values been successfully written to the entry?
    private
    var cacheEntryRequiresCommit = false

    private
    val host by lazy { deferredRootBuildGradle.gradle.services.get<HostServiceProvider>() }

    private
    val isolateOwnerHost: IsolateOwner by lazy { IsolateOwners.OwnerHost(host) }

    private
    val loadedSideEffects = mutableListOf<BuildTreeModelSideEffect>()

    private
    val storeDelegate = lazy { cacheRepository.forKey(cacheKey.string) }

    private
    val store by storeDelegate

    private
    val cacheIO by lazy { host.service<ConfigurationCacheBuildTreeIO>() }

    private
    val lazyBuildTreeModelSideEffects = lazy { BuildTreeModelSideEffectStore(isolateOwnerHost, cacheIO, store) }

    private
    val lazyIntermediateModels = lazy { IntermediateModelController(isolateOwnerHost, cacheIO, store, calculatedValueContainerFactory, cacheFingerprintController) }

    private
    val lazyProjectMetadata = lazy { ProjectMetadataController(isolateOwnerHost, cacheIO, resolveStateFactory, store, calculatedValueContainerFactory) }

    private
    val buildTreeModelSideEffects
        get() = lazyBuildTreeModelSideEffects.value

    private
    val intermediateModels
        get() = lazyIntermediateModels.value

    private
    val projectMetadata
        get() = lazyProjectMetadata.value

    private
    val gradlePropertiesController: GradlePropertiesController
        get() = host.service()

    override val isLoaded: Boolean
        get() = cacheAction == ConfigurationCacheAction.LOAD

    override fun initializeCacheEntry() {
        val (cacheAction, cacheActionDescription) = determineCacheAction()
        this.cacheAction = cacheAction
        problems.action(cacheAction, cacheActionDescription)
        // TODO:isolated find a way to avoid this late binding
        modelSideEffectExecutor.sideEffectStore = buildTreeModelSideEffects
    }

    override fun loadOrScheduleRequestedTasks(
        graph: BuildTreeWorkGraph,
        graphBuilder: BuildTreeWorkGraphBuilder?,
        scheduler: (BuildTreeWorkGraph) -> BuildTreeWorkGraph.FinalizedGraph
    ): BuildTreeConfigurationCache.WorkGraphResult {
        return if (isLoaded) {
            val finalizedGraph = loadWorkGraph(graph, graphBuilder, false)
            BuildTreeConfigurationCache.WorkGraphResult(
                finalizedGraph,
                wasLoadedFromCache = true,
                entryDiscarded = false
            )
        } else {
            runWorkThatContributesToCacheEntry {
                val finalizedGraph = scheduler(graph)
                saveWorkGraph()
                BuildTreeConfigurationCache.WorkGraphResult(
                    finalizedGraph,
                    wasLoadedFromCache = false,
                    entryDiscarded = problems.shouldDiscardEntry
                )
            }
        }
    }

    override fun loadRequestedTasks(graph: BuildTreeWorkGraph, graphBuilder: BuildTreeWorkGraphBuilder?): BuildTreeWorkGraph.FinalizedGraph {
        return loadWorkGraph(graph, graphBuilder, true)
    }

    override fun maybePrepareModel(action: () -> Unit) {
        if (isLoaded) {
            return
        }
        runWorkThatContributesToCacheEntry {
            action()
        }
    }

    override fun <T : Any> loadOrCreateModel(creator: () -> T): T {
        if (isLoaded) {
            runLoadedSideEffects()
            return loadModel().uncheckedCast()
        }

        return runWorkThatContributesToCacheEntry {
            val model = creator()
            saveModel(model)
            model
        }
    }

    private
    fun runLoadedSideEffects() {
        for (sideEffect in loadedSideEffects) {
            sideEffect.runSideEffect()
        }
    }

    override fun <T> loadOrCreateIntermediateModel(project: ProjectIdentity?, modelName: String, parameter: ToolingModelParameterCarrier?, creator: () -> T?): T? {
        return intermediateModels.loadOrCreateIntermediateModel(project, modelName, parameter, creator)
    }

    // TODO:configuration - split the component state, such that information for dependency resolution does not have to go through the store
    override fun loadOrCreateProjectMetadata(identityPath: Path, creator: () -> LocalComponentGraphResolveState): LocalComponentGraphResolveState {
        // We are preserving the original value if it had to be created,
        // because it carries information required by dependency resolution
        // to ensure project artifacts are actually created the first time around.
        // When the value is loaded from the store, the dependency information is lost.
        return projectMetadata.loadOrCreateOriginalValue(identityPath, creator)
    }

    override fun finalizeCacheEntry() {
        if (problems.shouldDiscardEntry) {
            store.useForStore { layout ->
                layout.fileFor(StateType.Entry).delete()
            }
            cacheEntryRequiresCommit = false
        } else if (cacheEntryRequiresCommit) {
            val projectUsage = collectProjectUsage()
            commitCacheEntry(projectUsage.reused)
            problems.projectStateStats(projectUsage.reused.size, projectUsage.updated.size)
            cacheEntryRequiresCommit = false
            // Can reuse the cache entry for the rest of this build invocation
            cacheAction = ConfigurationCacheAction.LOAD
        }
        try {
            cacheFingerprintController.stop()
        } finally {
            scopeRegistryListener.dispose()
        }
    }

    private
    fun collectProjectUsage(): ProjectUsage {
        val reusedProjects = mutableSetOf<Path>()
        val updatedProjects = mutableSetOf<Path>()
        intermediateModels.visitProjects(reusedProjects::add, updatedProjects::add)
        projectMetadata.visitProjects(reusedProjects::add) { }
        return ProjectUsage(reusedProjects, updatedProjects)
    }

    private
    data class ProjectUsage(val reused: Set<Path>, val updated: Set<Path>)

    private
    fun commitCacheEntry(reusedProjects: Set<Path>) {
        store.useForStore { layout ->
            writeConfigurationCacheFingerprint(layout, reusedProjects)
            val usedModels = intermediateModels.collectAccessedValues()
            val usedMetadata = projectMetadata.collectAccessedValues()
            val sideEffects = buildTreeModelSideEffects.collectSideEffects()
            cacheIO.writeCacheEntryDetailsTo(buildStateRegistry, usedModels, usedMetadata, sideEffects, layout.fileFor(StateType.Entry))
        }
    }

    private
    fun determineCacheAction(): Pair<ConfigurationCacheAction, StructuredMessage> = when {
        startParameter.recreateCache -> {
            val description = StructuredMessage.forText("Recreating configuration cache")
            logBootstrapSummary(description)
            ConfigurationCacheAction.STORE to description
        }

        startParameter.isRefreshDependencies -> {
            val description = formatBootstrapSummary(
                "%s as configuration cache cannot be reused due to %s",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--refresh-dependencies"
            )
            logBootstrapSummary(description)
            ConfigurationCacheAction.STORE to description
        }

        startParameter.isWriteDependencyLocks -> {
            val description = formatBootstrapSummary(
                "%s as configuration cache cannot be reused due to %s",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--write-locks"
            )
            logBootstrapSummary(description)
            ConfigurationCacheAction.STORE to description
        }

        startParameter.isUpdateDependencyLocks -> {
            val description = formatBootstrapSummary(
                "%s as configuration cache cannot be reused due to %s",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--update-locks"
            )
            logBootstrapSummary(description)
            ConfigurationCacheAction.STORE to description
        }

        else -> {
            when (val checkedFingerprint = checkFingerprint()) {
                is CheckedFingerprint.NotFound -> {
                    val description = formatBootstrapSummary(
                        "%s as no cached configuration is available for %s",
                        buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                        buildActionModelRequirements.configurationCacheKeyDisplayName.displayName
                    )
                    logBootstrapSummary(description)
                    ConfigurationCacheAction.STORE to description
                }

                is CheckedFingerprint.EntryInvalid -> {
                    val description = formatBootstrapSummary(
                        "%s as configuration cache cannot be reused because %s.",
                        buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                        checkedFingerprint.reason.render()
                    )
                    logBootstrapSummary(description)
                    ConfigurationCacheAction.STORE to description
                }

                is CheckedFingerprint.ProjectsInvalid -> {
                    val description = formatBootstrapSummary(
                        "%s as configuration cache cannot be reused because %s.",
                        buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                        checkedFingerprint.firstReason.render()
                    )
                    logBootstrapSummary(description)
                    ConfigurationCacheAction.UPDATE to description
                }

                is CheckedFingerprint.Valid -> {
                    val description = StructuredMessage.forText("Reusing configuration cache.")
                    logBootstrapSummary(description)
                    ConfigurationCacheAction.LOAD to description
                }
            }
        }
    }

    private
    fun formatBootstrapSummary(message: String, vararg args: Any?) =
        StructuredMessage.forText(String.format(Locale.US, message, *args))

    override fun stop() {
        val stoppable = CompositeStoppable.stoppable()
        stoppable.addIfInitialized(lazyBuildTreeModelSideEffects)
        stoppable.addIfInitialized(lazyIntermediateModels)
        stoppable.addIfInitialized(lazyProjectMetadata)
        stoppable.addIfInitialized(storeDelegate)
        stoppable.stop()
    }

    private
    fun CompositeStoppable.addIfInitialized(closeable: Lazy<*>) {
        if (closeable.isInitialized()) {
            add(closeable.value!!)
        }
    }

    private
    fun checkFingerprint(): CheckedFingerprint {
        return store.useForStateLoad { layout ->
            val entryFile = layout.fileFor(StateType.Entry)
            val entryDetails = cacheIO.readCacheEntryDetailsFrom(entryFile)
            buildOperationRunner.withFingerprintCheckOperations {
                if (entryDetails == null) {
                    // No entry file -> treat the entry as empty/missing/invalid
                    CheckedFingerprint.NotFound
                } else {
                    checkFingerprint(entryDetails, layout)
                }
            }
        }.value
    }

    private
    fun <T> runWorkThatContributesToCacheEntry(action: () -> T): T {
        prepareForWork()
        try {
            return action()
        } finally {
            doneWithWork()
        }
    }

    private
    fun prepareForWork() {
        prepareConfigurationTimeBarrier()
        startCollectingCacheFingerprint()
        InstrumentedInputs.setListener(instrumentedInputAccessListener)
    }

    private
    fun doneWithWork() {
        InstrumentedInputs.discardListener()
        cacheFingerprintController.stopCollectingFingerprint()
    }

    private
    fun saveModel(model: Any) {
        cacheEntryRequiresCommit = true

        if (startParameter.isIgnoreInputsDuringStore) {
            InstrumentedInputs.discardListener()
        }

        buildOperationRunner.withModelStoreOperation {
            val stateStoreResult = runAndStore(stateType = StateType.Model) { stateFile: ConfigurationCacheStateFile ->
                cacheIO.writeModelTo(model, stateFile)
            }
            ModelStoreResult(stateStoreResult.value)
        }

        crossConfigurationTimeBarrier()
    }

    private
    fun saveWorkGraph() {
        cacheEntryRequiresCommit = true

        if (startParameter.isIgnoreInputsDuringStore) {
            InstrumentedInputs.discardListener()
        }

        buildOperationRunner.withWorkGraphStoreOperation(cacheKey.string) {
            val stateStoreResult = runAndStore(stateType = StateType.Work) { stateFile: ConfigurationCacheStateFile ->
                writeConfigurationCacheState(stateFile)
            }
            WorkGraphStoreResult(stateStoreResult.accessedFiles, stateStoreResult.value)
        }

        crossConfigurationTimeBarrier()
    }

    private
    fun runAndStore(
        stateType: StateType,
        action: (ConfigurationCacheStateFile) -> Unit
    ): ConfigurationCacheStateStore.StateAccessResult<Throwable?> {

        return store.useForStore { layout ->
            try {
                val stateFile = layout.fileFor(stateType)
                action(stateFile)
                val storeFailure = problems.queryFailure()
                storeFailure
            } catch (error: Exception) {
                // Invalidate state on serialization errors
                problems.failingBuildDueToSerializationError()
                throw error
            }
        }
    }

    private
    data class LoadResultMetadata(val originInvocationId: String? = null)

    private
    fun loadModel(): Any = runAtConfigurationTime {
        // No need to record the `ClassLoaderScope` tree when loading
        scopeRegistryListener.dispose()

        buildOperationRunner.withModelLoadOperation {
            val storeLoadResult = store.useForStateLoad(StateType.Model) { stateFile: ConfigurationCacheStateFile ->
                cacheIO.readModelFrom(stateFile)
            }

            storeLoadResult.value
        }
    }

    private
    fun loadWorkGraph(
        graph: BuildTreeWorkGraph,
        graphBuilder: BuildTreeWorkGraphBuilder?,
        loadAfterStore: Boolean
    ): BuildTreeWorkGraph.FinalizedGraph = runAtConfigurationTime {

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        buildOperationRunner.withWorkGraphLoadOperation {
            val storeLoadResult = store.useForStateLoad(StateType.Work) { stateFile: ConfigurationCacheStateFile ->
                val (buildInvocationId, workGraph) = cacheIO.readRootBuildStateFrom(stateFile, loadAfterStore, graph, graphBuilder)
                LoadResultMetadata(buildInvocationId) to workGraph
            }
            val (intermediateLoadResult, actionResult) = storeLoadResult.value
            WorkGraphLoadResult(storeLoadResult.accessedFiles, intermediateLoadResult.originInvocationId) to actionResult
        }
    }

    private
    inline fun <T> runAtConfigurationTime(block: () -> T): T {
        prepareConfigurationTimeBarrier()
        val result = block()
        crossConfigurationTimeBarrier()
        return result
    }

    private
    fun prepareConfigurationTimeBarrier() {
        require(configurationTimeBarrier is DefaultConfigurationTimeBarrier)
        configurationTimeBarrier.prepare()
    }

    private
    fun crossConfigurationTimeBarrier() {
        require(configurationTimeBarrier is DefaultConfigurationTimeBarrier)
        configurationTimeBarrier.cross()
    }

    private
    fun writeConfigurationCacheState(stateFile: ConfigurationCacheStateFile) =
        cacheIO.writeRootBuildStateTo(stateFile)

    private
    fun writeConfigurationCacheFingerprint(layout: ConfigurationCacheRepository.Layout, reusedProjects: Set<Path>) {
        // Collect fingerprint entries for any projects whose state was reused from cache
        if (reusedProjects.isNotEmpty()) {
            readFingerprintFile(layout.fileForRead(StateType.ProjectFingerprint)) { host ->
                cacheFingerprintController.run {
                    collectFingerprintForReusedProjects(host, reusedProjects)
                }
            }
        }
        cacheFingerprintController.commitFingerprintTo(layout.fileFor(StateType.BuildFingerprint), layout.fileFor(StateType.ProjectFingerprint))
    }

    private
    fun startCollectingCacheFingerprint() {
        cacheFingerprintController.maybeStartCollectingFingerprint(
            store.assignSpoolFile(StateType.BuildFingerprint),
            store.assignSpoolFile(StateType.ProjectFingerprint)
        ) { stateFile ->
            cacheFingerprintWriteContextFor(stateFile.stateType, stateFile.file::outputStream) {
                profileNameFor(stateFile)
            }
        }
    }

    private
    fun profileNameFor(stateFile: ConfigurationCacheStateStore.StateFile) =
        stateFile.stateType.name.replace(Regex("\\p{Upper}")) { match ->
            " " + match.value.toDefaultLowerCase()
        }.drop(1)

    private
    fun cacheFingerprintWriteContextFor(
        stateType: StateType,
        outputStream: () -> OutputStream,
        profile: () -> String
    ): CloseableWriteContext {
        val (context, codecs) = cacheIO.writeContextFor("cacheFingerprintWriteContext", stateType, outputStream, profile)
        return context.apply {
            push(isolateOwnerHost, codecs.fingerprintTypesCodec())
        }
    }

    private
    fun checkFingerprint(entryDetails: EntryDetails, layout: ConfigurationCacheRepository.Layout): CheckedFingerprint {
        // Register all included build root directories as watchable hierarchies,
        // so we can load the fingerprint for build scripts and other files from included builds
        // without violating file system invariants.
        registerWatchableBuildDirectories(entryDetails.rootDirs)

        loadGradleProperties()

        return checkFingerprintAgainstLoadedProperties(entryDetails, layout).also { result ->
            if (result !== CheckedFingerprint.Valid) {
                // Force Gradle properties to be reloaded so the Gradle properties files
                // along with any Gradle property defining system properties and environment variables
                // are added to the new fingerprint.
                unloadGradleProperties()
            }
        }
    }

    private
    fun checkFingerprintAgainstLoadedProperties(entryDetails: EntryDetails, layout: ConfigurationCacheRepository.Layout): CheckedFingerprint {
        val result = checkBuildScopedFingerprint(layout.fileFor(StateType.BuildFingerprint))
        if (result !is CheckedFingerprint.Valid) {
            return result
        }

        // Build inputs are up-to-date, check project specific inputs

        val projectResult = checkProjectScopedFingerprint(layout.fileFor(StateType.ProjectFingerprint))
        if (projectResult is CheckedFingerprint.ProjectsInvalid) {
            intermediateModels.restoreFromCacheEntry(entryDetails.intermediateModels, projectResult)
            projectMetadata.restoreFromCacheEntry(entryDetails.projectMetadata, projectResult)
        }

        if (projectResult is CheckedFingerprint.Valid) {
            val sideEffects = buildTreeModelSideEffects.restoreFromCacheEntry(entryDetails.sideEffects)
            loadedSideEffects += sideEffects
        }

        return projectResult
    }

    private
    fun checkBuildScopedFingerprint(fingerprintFile: ConfigurationCacheStateFile): CheckedFingerprint {
        return readFingerprintFile(fingerprintFile) { host ->
            cacheFingerprintController.run {
                checkBuildScopedFingerprint(host)
            }
        }
    }

    private
    fun checkProjectScopedFingerprint(fingerprintFile: ConfigurationCacheStateFile): CheckedFingerprint {
        return readFingerprintFile(fingerprintFile) { host ->
            cacheFingerprintController.run {
                checkProjectScopedFingerprint(host)
            }
        }
    }

    private
    fun <T> readFingerprintFile(
        fingerprintFile: ConfigurationCacheStateFile,
        action: suspend ReadContext.(ConfigurationCacheFingerprintController.Host) -> T
    ): T =
        cacheIO.withReadContextFor(fingerprintFile) { codecs ->
            withIsolate(isolateOwnerHost, codecs.fingerprintTypesCodec()) {
                action(object : ConfigurationCacheFingerprintController.Host {
                    override val buildPath: Path
                        get() = host.service<GradleInternal>().identityPath
                    override val valueSourceProviderFactory: ValueSourceProviderFactory
                        get() = host.service()
                    override val gradleProperties: GradleProperties
                        get() = gradlePropertiesController.gradleProperties
                })
            }
        }

    private
    fun registerWatchableBuildDirectories(buildDirs: Iterable<File>) {
        buildDirs.forEach(virtualFileSystem::registerWatchableHierarchy)
    }

    private
    fun loadGradleProperties() {
        gradlePropertiesController.loadGradlePropertiesFrom(startParameter.settingsDirectory, true)
    }

    private
    fun unloadGradleProperties() {
        gradlePropertiesController.unloadGradleProperties()
    }

    private
    fun logBootstrapSummary(message: StructuredMessage) {
        logger.log(configurationCacheLogLevel, message.render())
    }

    private
    val configurationCacheLogLevel: LogLevel
        get() = startParameter.configurationCacheLogLevel
}
