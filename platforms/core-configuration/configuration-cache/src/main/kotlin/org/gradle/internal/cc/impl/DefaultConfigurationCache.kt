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

import org.gradle.api.Action
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.provider.DefaultConfigurationTimeBarrier
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.logging.LogLevel
import org.gradle.configurationcache.LoadResult
import org.gradle.configurationcache.StoreResult
import org.gradle.configurationcache.withLoadOperation
import org.gradle.configurationcache.withStoreOperation
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.NodeGroup
import org.gradle.execution.plan.ScheduledWork
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildState
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
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.configuration.inputs.InstrumentedInputs
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationInvocationException
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.MultipleBuildOperationFailures
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.serialize.graph.DefaultWriteContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier
import org.gradle.util.Path
import java.io.File
import java.io.OutputStream
import java.util.Collections
import java.util.Locale


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
    private val buildOperationExecutor: BuildOperationExecutor,
    private val cacheFingerprintController: ConfigurationCacheFingerprintController,
    private val encryptionService: EncryptionService,
    private val resolveStateFactory: LocalComponentGraphResolveStateFactory,
    /**
     * Force the [FileSystemAccess] service to be initialized as it initializes important static state.
     */
    @Suppress("unused")
    private val fileSystemAccess: FileSystemAccess,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    private val modelSideEffectExecutor: ConfigurationCacheBuildTreeModelSideEffectExecutor
) : BuildTreeConfigurationCache, Stoppable {

    interface Host : HostServiceProvider {

        val currentBuild: VintageGradleBuild

        fun createBuild(settingsFile: File?): ConfigurationCacheBuild

        fun visitBuilds(visitor: (VintageGradleBuild) -> Unit)

        fun <T> factory(serviceType: Class<T>): Factory<T>
    }

    private
    lateinit var cacheAction: ConfigurationCacheAction

    // Have one or more values been successfully written to the entry?
    private
    var cacheEntryRequiresCommit = false

    private
    lateinit var host: Host

    private
    val loadedSideEffects = mutableListOf<BuildTreeModelSideEffect>()

    private
    val storeDelegate = lazy { cacheRepository.forKey(cacheKey.string) }

    private
    val store by storeDelegate

    private
    val lazyBuildTreeModelSideEffects = lazy { BuildTreeModelSideEffectStore(host, cacheIO, store) }

    private
    val lazyIntermediateModels = lazy { IntermediateModelController(host, cacheIO, store, calculatedValueContainerFactory, cacheFingerprintController) }

    private
    val lazyProjectMetadata = lazy { ProjectMetadataController(host, cacheIO, resolveStateFactory, store, calculatedValueContainerFactory) }

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
    val cacheIO by lazy { host.service<ConfigurationCacheIO>() }

    private
    val gradlePropertiesController: GradlePropertiesController
        get() = host.service()

    override val isLoaded: Boolean
        get() = cacheAction == ConfigurationCacheAction.LOAD

    private
    fun rootBuildState(): BuildState =
        host.service()

    override fun initializeCacheEntry() {
        val (cacheAction, cacheActionDescription) = determineCacheAction()
        this.cacheAction = cacheAction
        problems.action(cacheAction, cacheActionDescription)
        // TODO:isolated find a way to avoid this late binding
        modelSideEffectExecutor.sideEffectStore = buildTreeModelSideEffects
    }

    override fun attachRootBuild(host: Host) {
        this.host = host
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

    override fun <T> loadOrCreateIntermediateModel(identityPath: Path?, modelName: String, parameter: ToolingModelParameterCarrier?, creator: () -> T?): T? {
        return intermediateModels.loadOrCreateIntermediateModel(identityPath, modelName, parameter, creator)
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
        scopeRegistryListener.dispose()
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
                        checkedFingerprint.reason.render()
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
            add(closeable.value)
        }
    }

    private
    fun checkFingerprint(): CheckedFingerprint {
        return store.useForStateLoad { layout ->
            val entryFile = layout.fileFor(StateType.Entry)
            val entryDetails = cacheIO.readCacheEntryDetailsFrom(entryFile)
            if (entryDetails == null) {
                // No entry file -> treat the entry as empty/missing/invalid
                CheckedFingerprint.NotFound
            } else {
                checkFingerprint(entryDetails, layout)
            }
        }
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
        saveToCache(
            stateType = StateType.Model
        ) { stateFile -> cacheIO.writeModelTo(model, stateFile) }
    }

    private
    fun saveWorkGraph() {
        saveToCache(
            stateType = StateType.Work,
        ) { stateFile ->
            writeRootBuildState(stateFile)
            writeRootBuildWorkGraph(stateFile)
        }
    }

    private
    fun saveToCache(stateType: StateType, action: (ConfigurationCacheStateFile) -> Unit) {

        cacheEntryRequiresCommit = true

        if (startParameter.isIgnoreInputsInTaskGraphSerialization) {
            InstrumentedInputs.discardListener()
        }

        buildOperationRunner.withStoreOperation(cacheKey.string) {
            store.useForStore { layout ->
                try {
                    val stateFile = layout.fileFor(stateType)
                    action(stateFile)
                    val storeFailure = problems.queryFailure()
                    StoreResult(stateFile.stateFile.file, storeFailure)
                } catch (error: ConfigurationCacheError) {
                    // Invalidate state on serialization errors
                    problems.failingBuildDueToSerializationError()
                    throw error
                }
            }
        }

        crossConfigurationTimeBarrier()
    }

    private
    fun loadModel(): Any {
        return loadFromCache(StateType.Model) { stateFile ->
            LoadResult(stateFile.stateFile.file) to cacheIO.readModelFrom(stateFile)
        }
    }

    private
    fun loadWorkGraph(graph: BuildTreeWorkGraph, graphBuilder: BuildTreeWorkGraphBuilder?, loadAfterStore: Boolean): BuildTreeWorkGraph.FinalizedGraph {
        return loadFromCache(StateType.Work) { stateFile ->
            val (buildInvocationId, builds) = cacheIO.readRootBuildStateFrom(stateFile, loadAfterStore)
            val partiallyLoadedBuilds = builds.filterIsInstance<BuildWithWorkPartiallyLoaded>()
            require(partiallyLoadedBuilds.size == 1)
            val build = partiallyLoadedBuilds[0].build
            val (buildInvocationId2, scheduledWork) = readRootBuildWorkGraph(build, stateFile)
            require(buildInvocationId == buildInvocationId2)
            val workGraph: BuildTreeWorkGraph.FinalizedGraph = calculateRootTaskGraph(builds, scheduledWork, graph, graphBuilder)
            LoadResult(stateFile.stateFile.file, buildInvocationId) to workGraph
        }
    }


    private
    fun calculateRootTaskGraph(builds: List<CachedBuildState>, scheduledWork: ScheduledWork?, graph: BuildTreeWorkGraph, graphBuilder: BuildTreeWorkGraphBuilder?): BuildTreeWorkGraph.FinalizedGraph {
        return graph.scheduleWork { builder ->
            for (build in builds) {
                if (build is BuildWithWork) {
                    builder.withWorkGraph(build.build.state) {
                        it.setScheduledWork(if (build is BuildWithWorkFullyLoaded) build.workGraph else scheduledWork)
                    }
                }
            }
            graphBuilder?.invoke(builder, rootBuildState())
        }
    }

    private
    fun <T : Any> loadFromCache(stateType: StateType, action: (ConfigurationCacheStateFile) -> Pair<LoadResult, T>): T {
        prepareConfigurationTimeBarrier()

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        val result = buildOperationRunner.withLoadOperation {
            store.useForStateLoad(stateType, action)
        }
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
    fun scheduledNodes() = host.currentBuild.scheduledWork.scheduledNodes

    private
    fun identifyNodesAndGroups() = with(host.currentBuild.scheduledWork) {
        identifyNodesAndGroups(scheduledNodes, entryNodes)
    }


    /**
     * Assigns ids for the given scheduled nodes, while also identifying entry nodes.
     *
     * @param scheduledNodes the list of nodes that are scheduled
     * @param entryNodes a set containing those scheduled nodes that are entry nodes
     * @return a pair **<a, b>** where
     * **a** is a map of nodes and their generated ids and
     * **b** is a set of the ids for scheduled nodes that are also entry nodes
     */
    private
    fun identifyNodesAndGroups(
        scheduledNodes: List<Node>,
        entryNodes: Set<Node>
    ): NodesAndGroups {
        val nodeGroups: MutableMap<NodeGroup, Int> = mutableMapOf()
        val scheduledNodeIds: HashMap<Node, Int> = HashMap(scheduledNodes.size)
        // Not all entry nodes are always scheduled.
        // In particular, it happens when the entry node is a task of the included plugin build that runs as part of building the plugin.
        // Such tasks do not rerun when configuration cache is re-used, even if specified on the command line.
        // Not restoring them as entry points doesn't affect the resulting execution plan.
        val scheduledEntryNodeIds: MutableList<Int> = mutableListOf()
        val nodesByOwner: MutableMap<NodeOwner, MutableList<Node>> = mutableMapOf()
        scheduledNodes.forEach { node ->
            val nodeId = scheduledNodeIds.size
            scheduledNodeIds[node] = nodeId
            val collect: (Node) -> Unit = { n ->
                val owner = NodeOwner.nodeOwnerFor(n)
                nodesByOwner
                    .computeIfAbsent(owner) { mutableListOf() }
                    .add(n)
                nodeGroups.computeIfAbsent(node.group) { nodeGroups.size }
            }
            collect(node)
            if (node in entryNodes) {
                scheduledEntryNodeIds.add(nodeId)
            }
            if (node is LocalTaskNode) {
                scheduledNodeIds[node.prepareNode] = scheduledNodeIds.size
            }
        }
        return NodesAndGroups(scheduledNodeIds, scheduledEntryNodeIds, nodesByOwner, nodeGroups)
    }

    //TODO-RC are we getting the group ids passed in or computing here?
    private
    fun writeRootBuildWorkGraph(baseStateFile: ConfigurationCacheStateFile) {
        // 1 - identify all nodes and entry nodes and split by projects
        // 2 - save all nodes in their own project-specific state file
        // 3 - save entry ids, node edges and successor references

        val (scheduledNodeIds, scheduledEntryNodeIds, nodesByProject, groupsById) = identifyNodesAndGroups()
        val scheduledNodes = scheduledNodes()
        val operations: MutableList<RunnableBuildOperation> = mutableListOf()
        cacheIO.writeProjectIndex(baseStateFile.stateFileForProjectIndex(), nodesByProject.keys.filterIsInstance<NodeOwner.Project>().map { it.project.identityPath })
        nodesByProject.forEach { (owner, nodes) ->
            operations.add(object : RunnableBuildOperation {
                override fun description(): BuildOperationDescriptor.Builder =
                    BuildOperationDescriptor
                        .displayName("Saving work nodes for $owner")
                        .progressDisplayName(
                            when (owner) {
                                NodeOwner.NoProject -> "tasks in other builds"
                                is NodeOwner.Project -> owner.project.path
                            }
                        )

                override fun run(context: BuildOperationContext) {
                    when (owner) {
                        NodeOwner.NoProject -> {
                            val projectStateFile = baseStateFile.stateFileForNodesInAnotherBuild()
                            cacheIO.writeRootBuildWorkNodesTo(projectStateFile, nodes) { scheduledNodeIds.getValue(it) }
                        }
                        is NodeOwner.Project -> {
                            val projectStateFile = baseStateFile.stateFileForProject(owner.project.identityPath)
                            owner.project.owner.applyToMutableState {
                                cacheIO.writeRootBuildWorkNodesTo(projectStateFile, nodes) { scheduledNodeIds.getValue(it) }
                            }
                        }
                    }
                }
            })

        }
        runCCOperations(BuildOperationExecutor::runAllWithAccessToProjectState, operations)
        runCCOperations(BuildOperationExecutor::runAllWithAccessToProjectState, listOf(
            object : RunnableBuildOperation {
                override fun description(): BuildOperationDescriptor.Builder =
                    BuildOperationDescriptor.displayName("Saving work edges")

                override fun run(context: BuildOperationContext) {
                    val stateFile = baseStateFile.stateFileForWorkGraph()
                    host.currentBuild.gradle.owner.projects.withMutableStateOfAllProjects {
                        cacheIO.writeRootBuildWorkEdges(stateFile, scheduledEntryNodeIds, scheduledNodes, scheduledNodeIds, groupsById)
                    }
                }
            }
        ))
    }

    /**
     * The root build work graph is read from multiple files - the basic work graph file, and multiple (per-project) node files.
     */
    private fun readRootBuildWorkGraph(
        build: ConfigurationCacheBuild,
        baseStateFile: ConfigurationCacheStateFile
    ): Pair<String, ScheduledWork> {
        //TODO-RC
        //load each project's work nodes from their private state file
        //load edges from global file

        val allScheduledNodes: MutableList<Node> = Collections.synchronizedList(mutableListOf())
        val allNodesById: MutableMap<Int, Node> = Collections.synchronizedMap(mutableMapOf())
        val allPerProjectOriginalBuildIds: MutableList<String> = Collections.synchronizedList(mutableListOf())

        val operations: MutableList<RunnableBuildOperation> = mutableListOf()

        val pathsForProjects: List<Path> = cacheIO.readProjectIndex(baseStateFile.stateFileForProjectIndex())
        for (projectPath in pathsForProjects) {
            val projectStateFile = baseStateFile.stateFileForProject(projectPath)
            if (!projectStateFile.exists) {
                continue
            }
            operations.add(object : RunnableBuildOperation {
                override fun description(): BuildOperationDescriptor.Builder {
                    val pathString = projectPath.toString()
                    return BuildOperationDescriptor
                        .displayName(pathString)
                        .progressDisplayName(pathString)
                }

                override fun run(context: BuildOperationContext) {
                    val (originalBuildId, scheduledNodes, nodesById) = cacheIO.readRootBuildWorkNodesFrom(build, projectStateFile)
                    allScheduledNodes.addAll(scheduledNodes)
                    allNodesById.putAll(nodesById)
                    allPerProjectOriginalBuildIds.add(originalBuildId)
                }
            })
        }
        val stateFileForNodesInOtherBuilds = baseStateFile.stateFileForNodesInAnotherBuild()
        if (stateFileForNodesInOtherBuilds.exists) {
            operations.add(object : RunnableBuildOperation {
                override fun description(): BuildOperationDescriptor.Builder =
                    BuildOperationDescriptor
                        .displayName("tasks in other builds")
                        .progressDisplayName("tasks in other builds")

                override fun run(context: BuildOperationContext) {
                    val (originalBuildId, scheduledNodes, nodesById) = cacheIO.readRootBuildWorkNodesFrom(build, stateFileForNodesInOtherBuilds)
                    allScheduledNodes.addAll(scheduledNodes)
                    allNodesById.putAll(nodesById)
                    allPerProjectOriginalBuildIds.add(originalBuildId)
                }
            })
        }
        runCCOperations(BuildOperationExecutor::runAllWithAccessToProjectState, operations)
        // TODO-RC the number of build ids seen may be even 0, for instance, when only requesting buildSrc build tasks to run
        // as they are not executed - see ConfigurationCacheIncludedBuildLogicIntegrationTest
        if (allPerProjectOriginalBuildIds.size > 1) {
            allPerProjectOriginalBuildIds.fold(allPerProjectOriginalBuildIds[0]) { s: String, s1: String ->
                require(s == s1) {
                    "Multiple build ids"
                }
                s
            }
        }
        var result: Pair<String, ScheduledWork>? = null
        runCCOperations(BuildOperationExecutor::runAllWithAccessToProjectState,
            object : RunnableBuildOperation {
                override fun description(): BuildOperationDescriptor.Builder =
                    BuildOperationDescriptor.displayName("Reading work edges")

                override fun run(context: BuildOperationContext) {

                    host.currentBuild.gradle.owner.projects.withMutableStateOfAllProjects {
                        result = cacheIO.readRootBuildWorkEdgesFrom(baseStateFile.stateFileForWorkGraph(), allScheduledNodes, allNodesById)
                        require(allPerProjectOriginalBuildIds.isEmpty() || result!!.first == allPerProjectOriginalBuildIds[0]) {
                            "Multiple build ids"
                        }
                    }
                }
            }
        )
        return result!!
    }


    private
    fun <O: RunnableBuildOperation> runCCOperations(operationExecution: BuildOperationExecutor.(Action<BuildOperationQueue<O>>) -> Unit, operation: O) {
        runCCOperations(operationExecution, listOf(operation))
    }

    private
    fun <O: RunnableBuildOperation> runCCOperations(operationExecution: BuildOperationExecutor.(Action<BuildOperationQueue<O>>) -> Unit, operations: List<O>) {
        try {
            if (startParameter.parallelConfigCacheStoring) {
                operationExecution.invoke(buildOperationExecutor) {
                    operations.forEach(::add)
                }
            } else {
                operations.forEach { operation ->
                    operationExecution.invoke(buildOperationExecutor) {
                        add(operation)
                    }
                }
            }
        } catch (@Suppress("SwallowedException") e: MultipleBuildOperationFailures) {
            if (e.causes[0] is BuildOperationInvocationException) {
                throw e.causes[0].cause!!
            }
            throw e.causes[0]
        }
    }

    private
    fun writeRootBuildState(stateFile: ConfigurationCacheStateFile) {
        host.currentBuild.gradle.owner.projects.withMutableStateOfAllProjects {
            cacheIO.writeRootBuildStateTo(stateFile)
        }
    }

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
            cacheFingerprintWriterContextFor(
                encryptionService.outputStream(
                    stateFile.stateType,
                    stateFile.file::outputStream
                )
            ) {
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
    fun cacheFingerprintWriterContextFor(outputStream: OutputStream, profile: () -> String): DefaultWriteContext {
        val (context, codecs) = cacheIO.writerContextFor(outputStream, profile)
        return context.apply {
            push(IsolateOwners.OwnerHost(host), codecs.fingerprintTypesCodec())
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
    fun <T> readFingerprintFile(fingerprintFile: ConfigurationCacheStateFile, action: suspend ReadContext.(ConfigurationCacheFingerprintController.Host) -> T): T =
        encryptionService.inputStream(fingerprintFile.stateType, fingerprintFile::inputStream).use { inputStream ->
            cacheIO.withReadContextFor(inputStream) { codecs ->
                withIsolate(IsolateOwners.OwnerHost(host), codecs.fingerprintTypesCodec()) {
                    action(object : ConfigurationCacheFingerprintController.Host {
                        override val valueSourceProviderFactory: ValueSourceProviderFactory
                            get() = host.service()
                        override val gradleProperties: GradleProperties
                            get() = gradlePropertiesController.gradleProperties
                    })
                }
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

data class NodesAndGroups(
    val scheduledNodeIds: Map<Node, Int>,
    val scheduledEntryNodeIds: List<Int>,
    val scheduledNodeResourceLocks: Map<NodeOwner, List<Node>>,
    val nodeGroups: Map<NodeGroup, Int>
)

sealed class NodeOwner {
    data class Project(val project: ProjectInternal): NodeOwner()
    // Tasks from other builds will have no project reference
    object NoProject : NodeOwner() {
        override fun toString(): String {
            return this::class.simpleName!!
        }
    }
    companion object {
        fun nodeOwnerFor(node: Node) =
            node.owningProject?.let(::Project) ?: NoProject
    }
}

