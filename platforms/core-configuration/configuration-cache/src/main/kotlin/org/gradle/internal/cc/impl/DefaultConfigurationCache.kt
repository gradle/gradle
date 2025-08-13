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
import org.gradle.api.internal.properties.GradlePropertiesController
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.provider.DefaultConfigurationTimeBarrier
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.logging.LogLevel
import org.gradle.configurationcache.EntrySearchResult
import org.gradle.configurationcache.ModelStoreResult
import org.gradle.configurationcache.WorkGraphLoadResult
import org.gradle.configurationcache.WorkGraphStoreResult
import org.gradle.configurationcache.withFingerprintCheckOperations
import org.gradle.configurationcache.withModelLoadOperation
import org.gradle.configurationcache.withModelStoreOperation
import org.gradle.configurationcache.withWorkGraphLoadOperation
import org.gradle.configurationcache.withWorkGraphStoreOperation
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildTreeModelSideEffect
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.cc.base.logger
import org.gradle.internal.cc.base.serialize.HostServiceProvider
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.base.serialize.service
import org.gradle.internal.cc.impl.ConfigurationCacheAction.Load
import org.gradle.internal.cc.impl.ConfigurationCacheAction.SkipStore
import org.gradle.internal.cc.impl.ConfigurationCacheAction.Store
import org.gradle.internal.cc.impl.ConfigurationCacheAction.Update
import org.gradle.internal.cc.impl.extensions.withMostRecentEntry
import org.gradle.internal.cc.impl.fingerprint.ClassLoaderScopesFingerprintController
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintStartParameters
import org.gradle.internal.cc.impl.fingerprint.InvalidationReason
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
import java.util.Properties
import java.util.UUID


@Suppress("LongParameterList", "LargeClass")
class DefaultConfigurationCache internal constructor(
    private val startParameter: ConfigurationCacheStartParameter,
    private val cacheKey: ConfigurationCacheKey,
    private val problems: ConfigurationCacheProblems,
    private val scopeRegistryListener: ConfigurationCacheClassLoaderScopeRegistryListener,
    private val cacheRepository: ConfigurationCacheRepository,
    private val inputsAccessListener: ConfigurationCacheInputsListener,
    private val configurationTimeBarrier: ConfigurationTimeBarrier,
    private val buildActionModelRequirements: BuildActionModelRequirements,
    private val buildStateRegistry: BuildStateRegistry,
    private val virtualFileSystem: BuildLifecycleAwareVirtualFileSystem,
    private val buildOperationRunner: BuildOperationRunner,
    private val cacheFingerprintController: ConfigurationCacheFingerprintController,
    private val classLoaderScopes: ClassLoaderScopesFingerprintController,
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
    lateinit var entryId: String

    private
    val entryStoreDelegate = lazy { cacheRepository.forKey(entryId) }

    private
    val entryStore by entryStoreDelegate

    private
    val cacheIO by lazy { host.service<ConfigurationCacheBuildTreeIO>() }

    private
    val lazyBuildTreeModelSideEffects = lazy {
        BuildTreeModelSideEffectStore(
            isolateOwnerHost,
            cacheIO,
            entryStore
        )
    }

    private
    val lazyIntermediateModels = lazy {
        IntermediateModelController(
            isolateOwnerHost,
            cacheIO,
            entryStore,
            calculatedValueContainerFactory,
            cacheFingerprintController
        )
    }

    private
    val lazyProjectMetadata = lazy {
        ProjectMetadataController(
            isolateOwnerHost,
            cacheIO,
            resolveStateFactory,
            entryStore,
            calculatedValueContainerFactory
        )
    }

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
        get() = cacheAction is Load

    override fun initializeCacheEntry() {
        val (cacheAction, cacheActionDescription) = determineCacheAction()
        this.cacheAction = cacheAction
        this.entryId = when (cacheAction) {
            is Load -> cacheAction.entryId
            is Update -> cacheAction.entryId
            Store -> UUID.randomUUID().toString()
            // no cache entry key in this case
            SkipStore -> ""
        }
        initializeCacheEntrySideEffects(cacheAction)
        problems.action(cacheAction, cacheActionDescription)
    }

    private
    fun initializeCacheEntrySideEffects(cacheAction: ConfigurationCacheAction) {
        when (cacheAction) {
            is Load -> {
                val entryDetails = readEntryDetails()
                val sideEffects = buildTreeModelSideEffects.restoreFromCacheEntry(entryDetails.sideEffects)
                loadedSideEffects += sideEffects
            }

            is Update -> {
                val invalidProjects = cacheAction.invalidProjects
                val entryDetails = readEntryDetails()
                intermediateModels.restoreFromCacheEntry(entryDetails.intermediateModels, invalidProjects)
                projectMetadata.restoreFromCacheEntry(entryDetails.projectMetadata, invalidProjects)
            }

            Store -> {}

            SkipStore -> {}
        }
        // TODO:isolated find a way to avoid this late binding
        modelSideEffectExecutor.sideEffectStore = buildTreeModelSideEffects
    }

    private
    fun readEntryDetails() =
        entryStore.useForStateLoad(StateType.Entry) {
            cacheIO.readCacheEntryDetailsFrom(it)!!
        }.value

    override fun loadOrScheduleRequestedTasks(
        graph: BuildTreeWorkGraph,
        graphBuilder: BuildTreeWorkGraphBuilder?,
        scheduler: (BuildTreeWorkGraph) -> BuildTreeWorkGraph.FinalizedGraph
    ): BuildTreeConfigurationCache.WorkGraphResult {
        return when (cacheAction) {
            is Load -> {
                val finalizedGraph = loadWorkGraph(graph, graphBuilder, false)
                BuildTreeConfigurationCache.WorkGraphResult(
                    finalizedGraph,
                    wasLoadedFromCache = true,
                    entryDiscarded = false
                )
            }
            SkipStore -> {
                // build work graph without contributing to a cache entry
                val finalizedGraph = runAtConfigurationTime {
                    val result = scheduler(graph)
                    // force computation of degradation reasons at configuration time as it shouldn't be attempted at execution time
                    problems.shouldDegradeGracefully()
                    result
                }
                BuildTreeConfigurationCache.WorkGraphResult(
                    finalizedGraph,
                    wasLoadedFromCache = false,
                    entryDiscarded = true
                )
            }
            Store, is Update -> {
                runWorkThatContributesToCacheEntry {
                    val finalizedGraph = scheduler(graph)
                    val rootBuild = buildStateRegistry.rootBuild
                    degradeGracefullyOr { saveWorkGraph(rootBuild) }
                    crossConfigurationTimeBarrier()
                    BuildTreeConfigurationCache.WorkGraphResult(
                        finalizedGraph,
                        wasLoadedFromCache = false,
                        entryDiscarded = problems.shouldDiscardEntry
                    )
                }
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
            // Graceful degradation. We don't care about the models saving at the moment since it's happening
            // only when Isolated Projects enabled. That's it, there is no model saving in CC + noIP mode.
            saveModel(model)
            model
        }
    }

    private
    fun degradeGracefullyOr(action: () -> Unit) {
        if (!problems.shouldDegradeGracefully()) {
            action()
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
        when {
            cacheAction == SkipStore -> {
                // nothing to do
                require(!cacheEntryRequiresCommit)
            }
            problems.shouldDiscardEntry -> {
                discardEntry()
                cacheEntryRequiresCommit = false
            }
            cacheEntryRequiresCommit -> {
                val projectUsage = collectProjectUsage()
                commitCacheEntry(projectUsage.reused)
                problems.projectStateStats(projectUsage.reused.size, projectUsage.updated.size)
                cacheEntryRequiresCommit = false
                // Can reuse the cache entry for the rest of this build invocation
                cacheAction = Load(entryId)
            }
        }
        try {
            cacheFingerprintController.stop()
        } finally {
            scopeRegistryListener.dispose()
        }
    }

    private
    fun discardEntry() {
        updateCandidateEntries {
            minus(CandidateEntry(entryId))
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
        entryStore.useForStore {
            writeConfigurationCacheFingerprint(reusedProjects)
            val usedModels = intermediateModels.collectAccessedValues()
            val usedMetadata = projectMetadata.collectAccessedValues()
            val sideEffects = buildTreeModelSideEffects.collectSideEffects()
            cacheIO.writeCacheEntryDetailsTo(
                buildStateRegistry,
                usedModels,
                usedMetadata,
                sideEffects,
                fileFor(StateType.Entry)
            )
            classLoaderScopes.commit(fileFor(StateType.ClassLoaderScopes))
        }
        updateMostRecentEntry(entryId)
    }

    private
    fun determineCacheAction(): DescribedAction {
        val basicAction = determineBasicCacheAction()
        if (startParameter.isReadOnlyCache && !basicAction.action.isReadOnly) {
            // fall back to [SkipStore] if we are in read-only mode
            return SkipStore.withDescription(basicAction.description)
        }
        return basicAction
    }

    private
    fun determineBasicCacheAction(): DescribedAction = when {
        startParameter.recreateCache -> {
            val description = StructuredMessage.forText("Recreating configuration cache")
            logBootstrapSummary(description)
            Store.withDescription(description)
        }

        startParameter.isRefreshDependencies -> {
            val description = formatBootstrapSummary(
                "%s as configuration cache cannot be reused due to %s",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--refresh-dependencies"
            )
            logBootstrapSummary(description)
            Store.withDescription(description)
        }

        startParameter.isWriteDependencyLocks -> {
            val description = formatBootstrapSummary(
                "%s as configuration cache cannot be reused due to %s",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--write-locks"
            )
            logBootstrapSummary(description)
            Store.withDescription(description)
        }

        startParameter.isUpdateDependencyLocks -> {
            val description = formatBootstrapSummary(
                "%s as configuration cache cannot be reused due to %s",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--update-locks"
            )
            logBootstrapSummary(description)
            Store.withDescription(description)
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
                    Store.withDescription(description)
                }

                is CheckedFingerprint.Invalid -> {
                    val description = formatBootstrapSummary(
                        "%s as configuration cache cannot be reused because %s.",
                        buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                        checkedFingerprint.reason.render()
                    )
                    logBootstrapSummary(description)
                    Store.withDescription(description)
                }

                is CheckedFingerprint.Valid -> {
                    when (val invalid = checkedFingerprint.invalidProjects) {
                        null -> {
                            val description = StructuredMessage.forText("Reusing configuration cache.")
                            logBootstrapSummary(description)
                            Load(checkedFingerprint.entryId).withDescription(description)
                        }

                        else -> {
                            val description = formatBootstrapSummary(
                                "%s as configuration cache cannot be reused because %s.",
                                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                                invalid.first.reason.render()
                            )
                            logBootstrapSummary(description)
                            Update(checkedFingerprint.entryId, invalid).withDescription(description)
                        }
                    }
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
        stoppable.addIfInitialized(entryStoreDelegate)
        stoppable.stop()
    }

    private
    fun CompositeStoppable.addIfInitialized(closeable: Lazy<*>) {
        if (closeable.isInitialized()) {
            add(closeable.value!!)
        }
    }

    private
    fun checkFingerprint(): CheckedFingerprint = buildOperationRunner.withFingerprintCheckOperations {
        val candidates = loadCandidateEntries()
        val searchResult = searchForValidEntry(candidates)
        val checkedFingerprint = searchResult.checkedFingerprint
        if (checkedFingerprint is CheckedFingerprint.Valid) {
            updateMostRecentEntry(checkedFingerprint.entryId)
        }
        searchResult
    }

    private
    fun searchForValidEntry(candidates: List<CandidateEntry>): EntrySearchResult {
        var firstInvalidResult: EntrySearchResult? = null
        for (candidate in candidates) {
            val result = checkCandidate(candidate)
            when (result.checkedFingerprint) {
                is CheckedFingerprint.Valid -> {
                    return result
                }

                is CheckedFingerprint.Invalid -> {
                    if (firstInvalidResult == null) {
                        firstInvalidResult = result
                    }
                }

                CheckedFingerprint.NotFound -> continue
            }
        }
        return firstInvalidResult
            ?: EntrySearchResult(null, CheckedFingerprint.NotFound)
    }

    private
    fun loadCandidateEntries() = store.useForStateLoad {
        readCandidateEntries()
    }.value

    private
    fun updateMostRecentEntry(mostRecent: String) =
        updateCandidateEntries {
            withMostRecentEntry(
                CandidateEntry(mostRecent),
                startParameter.entriesPerKey
            )
        }

    private
    fun updateCandidateEntries(update: List<CandidateEntry>.() -> List<CandidateEntry>) = store.useForStore {
        val existingEntries = readCandidateEntries()
        val newEntries = update(existingEntries)
        if (existingEntries != newEntries) {
            writeCandidateEntries(newEntries)
            scheduleForCollection(existingEntries - newEntries.toHashSet())
        }
    }

    private
    fun scheduleForCollection(evictedEntries: List<CandidateEntry>) {
        if (evictedEntries.isNotEmpty()) {
            host.service<ConfigurationCacheEntryCollector>().let { collector ->
                evictedEntries.forEach { entry ->
                    collector.scheduleForCollection(entry.id)
                }
            }
        }
    }

    private
    fun ConfigurationCacheRepository.Layout.writeCandidateEntries(entries: List<CandidateEntry>) {
        cacheIO.writeCandidateEntries(fileFor(StateType.Candidates), entries)
    }

    private
    fun ConfigurationCacheRepository.Layout.readCandidateEntries() =
        cacheIO.readCandidateEntries(fileForRead(StateType.Candidates))

    private
    fun checkCandidate(candidateEntry: CandidateEntry): EntrySearchResult {
        // checking a single fingerprint
        val entryName = candidateEntry.id
        val entryStore = cacheRepository.forKey(entryName)
        return entryStore.useForStateLoad {
            checkedFingerprint(candidateEntry)
        }.value
    }

    private
    fun ConfigurationCacheRepository.Layout.checkedFingerprint(candidateEntry: CandidateEntry): EntrySearchResult =
        cacheIO.readCacheEntryDetailsFrom(fileFor(StateType.Entry))
            ?.let { entryDetails ->
                // TODO:configuration-cache read only rootDirs at this point
                EntrySearchResult(
                    entryDetails.buildInvocationScopeId,
                    checkFingerprint(candidateEntry, entryDetails.rootDirs)
                )
            } ?: EntrySearchResult(null, CheckedFingerprint.NotFound)

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
        val parameters = fingerprintStartParameters()
        classLoaderScopes.prepareForWriting(parameters)
        cacheFingerprintController.maybeStartCollectingFingerprint(parameters)
        InstrumentedInputs.setListener(inputsAccessListener)
    }

    private
    fun doneWithWork() {
        InstrumentedInputs.discardListener()
        cacheFingerprintController.stopCollectingFingerprint()
    }

    private
    fun fingerprintStartParameters(): ConfigurationCacheFingerprintStartParameters = object : ConfigurationCacheFingerprintStartParameters {
        override fun assignBuildScopedSpoolFile() = entryStore.assignSpoolFile(StateType.BuildFingerprint)
        override fun assignProjectScopedSpoolFile() = entryStore.assignSpoolFile(StateType.ProjectFingerprint)
        override fun assignClassLoaderScopesFile() = entryStore.assignSpoolFile(StateType.ClassLoaderScopes)
        override fun writerContextFor(stateFile: ConfigurationCacheStateStore.StateFile) =
            cacheFingerprintWriteContextFor(stateFile.stateType, stateFile.file::outputStream) {
                profileNameFor(stateFile)
            }

        override fun encoderFor(stateFile: ConfigurationCacheStateStore.StateFile) =
            cacheIO.encoderFor(stateFile.stateType, stateFile.file::outputStream)
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
    fun saveWorkGraph(rootBuild: BuildState) {
        cacheEntryRequiresCommit = true

        if (startParameter.isIgnoreInputsDuringStore) {
            InstrumentedInputs.discardListener()
        }

        buildOperationRunner.withWorkGraphStoreOperation(cacheKey.string) {
            val stateStoreResult = runAndStore(stateType = StateType.Work) { stateFile: ConfigurationCacheStateFile ->
                writeConfigurationCacheState(rootBuild, stateFile)
            }
            WorkGraphStoreResult(stateStoreResult.accessedFiles, stateStoreResult.value)
        }
    }

    private
    fun runAndStore(
        stateType: StateType,
        action: (ConfigurationCacheStateFile) -> Unit
    ): ConfigurationCacheStateStore.StateAccessResult<Throwable?> {

        return entryStore.useForStore {
            try {
                val stateFile = fileFor(stateType)
                action(stateFile)
                val storeFailure = problems.queryFailure()
                storeFailure
            } catch (error: Exception) {
                // Invalidate state on serialization errors
                problems.onStoreSerializationError()
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
            val storeLoadResult = entryStore.useForStateLoad(StateType.Model) { stateFile: ConfigurationCacheStateFile ->
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
            val storeLoadResult = entryStore.useForStateLoad(StateType.Work) { stateFile: ConfigurationCacheStateFile ->
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
    fun writeConfigurationCacheState(rootBuild: BuildState, stateFile: ConfigurationCacheStateFile) =
        cacheIO.writeRootBuildStateTo(rootBuild, stateFile)

    private
    fun ConfigurationCacheRepository.Layout.writeConfigurationCacheFingerprint(reusedProjects: Set<Path>) {
        // Collect fingerprint entries for any projects whose state was reused from cache
        if (reusedProjects.isNotEmpty()) {
            readFingerprintFile(fileForRead(StateType.ProjectFingerprint)) { host ->
                cacheFingerprintController.run {
                    collectFingerprintForReusedProjects(host, reusedProjects)
                }
            }
        }
        cacheFingerprintController.commitFingerprintTo(
            fileFor(StateType.BuildFingerprint),
            fileFor(StateType.ProjectFingerprint)
        )
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
    fun ConfigurationCacheRepository.Layout.checkFingerprint(candidateEntry: CandidateEntry, rootDirs: List<File>): CheckedFingerprint {
        // Register all included build root directories as watchable hierarchies,
        // so we can load the fingerprint for build scripts and other files from included builds
        // without violating file system invariants.
        registerWatchableBuildDirectories(rootDirs)

        val classLoaderScopesInvalidationReason = checkClassLoaderScopes()
        if (classLoaderScopesInvalidationReason != null) {
            return CheckedFingerprint.Invalid(buildPath(), classLoaderScopesInvalidationReason)
        }

        val systemPropertiesSnapshot = System.getProperties().clone()
        return checkFingerprintAgainstLoadedProperties(candidateEntry).also { result ->
            if (result !is CheckedFingerprint.Valid || result.invalidProjects != null) {
                // Restore system properties and force Gradle properties to be reloaded
                // so the Gradle properties files along with any Gradle property defining
                // system properties and environment variables are added to the new fingerprint.
                rollbackProperties(systemPropertiesSnapshot.uncheckedCast())
            }
        }
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
    ): T =
        cacheIO.withReadContextFor(fingerprintFile) { codecs ->
            withIsolate(isolateOwnerHost, codecs.fingerprintTypesCodec()) {
                action(object : ConfigurationCacheFingerprintController.Host {
                    override val valueSourceProviderFactory: ValueSourceProviderFactory
                        get() = host.service()
                })
            }
        }

    private
    fun buildPath(): Path =
        host.service<GradleInternal>().identityPath

    private
    fun registerWatchableBuildDirectories(buildDirs: Iterable<File>) {
        buildDirs.forEach(virtualFileSystem::registerWatchableHierarchy)
    }

    private
    fun rollbackProperties(systemPropertiesSnapshot: Properties) {
        gradlePropertiesController.unloadAll()
        System.setProperties(systemPropertiesSnapshot)
    }

    private
    fun logBootstrapSummary(message: StructuredMessage) {
        logger.log(configurationCacheLogLevel, message.render())
    }

    private
    val configurationCacheLogLevel: LogLevel
        get() = startParameter.configurationCacheLogLevel
}
