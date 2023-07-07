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

package org.gradle.configurationcache

import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.provider.DefaultConfigurationTimeBarrier
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.configurationcache.cacheentry.EntryDetails
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.metadata.ProjectMetadataController
import org.gradle.configurationcache.models.IntermediateModelController
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.withIsolate
import org.gradle.initialization.GradlePropertiesController
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.classpath.Instrumented
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem
import org.gradle.util.Path
import java.io.File
import java.io.OutputStream


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
    private val buildOperationExecutor: BuildOperationExecutor,
    private val cacheFingerprintController: ConfigurationCacheFingerprintController,
    private val encryptionService: EncryptionService,
    private val resolveStateFactory: LocalComponentGraphResolveStateFactory,
    /**
     * Force the [FileSystemAccess] service to be initialized as it initializes important static state.
     */
    @Suppress("unused")
    private val fileSystemAccess: FileSystemAccess
) : BuildTreeConfigurationCache, Stoppable {

    interface Host {

        val currentBuild: VintageGradleBuild

        fun createBuild(settingsFile: File?): ConfigurationCacheBuild

        fun visitBuilds(visitor: (VintageGradleBuild) -> Unit)

        fun <T> service(serviceType: Class<T>): T

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
    val store by lazy { cacheRepository.forKey(cacheKey.string) }

    private
    val intermediateModels = lazy { IntermediateModelController(host, cacheIO, store, cacheFingerprintController) }

    private
    val projectMetadata = lazy { ProjectMetadataController(host, cacheIO, resolveStateFactory, store) }

    private
    val cacheIO by lazy { host.service<ConfigurationCacheIO>() }

    private
    val gradlePropertiesController: GradlePropertiesController
        get() = host.service()

    override val isLoaded: Boolean
        get() = cacheAction == ConfigurationCacheAction.LOAD

    override fun initializeCacheEntry() {
        cacheAction = determineCacheAction()
        problems.action(cacheAction)
    }

    override fun attachRootBuild(host: Host) {
        this.host = host
    }

    override fun loadOrScheduleRequestedTasks(graph: BuildTreeWorkGraph, graphBuilder: BuildTreeWorkGraphBuilder?, scheduler: (BuildTreeWorkGraph) -> BuildTreeWorkGraph.FinalizedGraph): BuildTreeConfigurationCache.WorkGraphResult {
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
            return loadModel().uncheckedCast()
        }
        return runWorkThatContributesToCacheEntry {
            val model = creator()
            saveModel(model)
            model
        }
    }

    override fun <T> loadOrCreateIntermediateModel(identityPath: Path?, modelName: String, creator: () -> T?): T? {
        return intermediateModels.value.loadOrCreateIntermediateModel(identityPath, modelName, creator)
    }

    override fun loadOrCreateProjectMetadata(identityPath: Path, creator: () -> LocalComponentGraphResolveState): LocalComponentGraphResolveState {
        return projectMetadata.value.loadOrCreateValue(identityPath, creator)
    }

    override fun finalizeCacheEntry() {
        if (problems.shouldDiscardEntry) {
            store.useForStore { layout ->
                layout.fileFor(StateType.Entry).delete()
            }
            cacheEntryRequiresCommit = false
        } else if (cacheEntryRequiresCommit) {
            val reusedProjects = mutableSetOf<Path>()
            val updatedProjects = mutableSetOf<Path>()
            intermediateModels.value.visitProjects(reusedProjects::add, updatedProjects::add)
            projectMetadata.value.visitProjects(reusedProjects::add) { }
            store.useForStore { layout ->
                writeConfigurationCacheFingerprint(layout, reusedProjects)
                cacheIO.writeCacheEntryDetailsTo(buildStateRegistry, intermediateModels.value.values, projectMetadata.value.values, layout.fileFor(StateType.Entry))
            }
            problems.projectStateStats(reusedProjects.size, updatedProjects.size)
            cacheEntryRequiresCommit = false
            // Can reuse the cache entry for the rest of this build invocation
            cacheAction = ConfigurationCacheAction.LOAD
        }
    }

    private
    fun determineCacheAction(): ConfigurationCacheAction = when {
        startParameter.recreateCache -> {
            logBootstrapSummary("Recreating configuration cache")
            ConfigurationCacheAction.STORE
        }

        startParameter.isRefreshDependencies -> {
            logBootstrapSummary(
                "{} as configuration cache cannot be reused due to {}",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--refresh-dependencies"
            )
            ConfigurationCacheAction.STORE
        }

        startParameter.isWriteDependencyLocks -> {
            logBootstrapSummary(
                "{} as configuration cache cannot be reused due to {}",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--write-locks"
            )
            ConfigurationCacheAction.STORE
        }

        startParameter.isUpdateDependencyLocks -> {
            logBootstrapSummary(
                "{} as configuration cache cannot be reused due to {}",
                buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                "--update-locks"
            )
            ConfigurationCacheAction.STORE
        }

        else -> {
            when (val checkedFingerprint = checkFingerprint()) {
                is CheckedFingerprint.NotFound -> {
                    logBootstrapSummary(
                        "{} as no configuration cache is available for {}",
                        buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                        buildActionModelRequirements.configurationCacheKeyDisplayName.displayName
                    )
                    ConfigurationCacheAction.STORE
                }

                is CheckedFingerprint.EntryInvalid -> {
                    logBootstrapSummary(
                        "{} as configuration cache cannot be reused because {}.",
                        buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                        checkedFingerprint.reason
                    )
                    ConfigurationCacheAction.STORE
                }

                is CheckedFingerprint.ProjectsInvalid -> {
                    logBootstrapSummary(
                        "{} as configuration cache cannot be reused because {}.",
                        buildActionModelRequirements.actionDisplayName.capitalizedDisplayName,
                        checkedFingerprint.reason
                    )
                    ConfigurationCacheAction.UPDATE
                }

                is CheckedFingerprint.Valid -> {
                    logBootstrapSummary("Reusing configuration cache.")
                    ConfigurationCacheAction.LOAD
                }
            }
        }
    }

    override fun stop() {
        val stoppable = CompositeStoppable.stoppable()
        if (intermediateModels.isInitialized()) {
            stoppable.add(intermediateModels.value)
        }
        if (projectMetadata.isInitialized()) {
            stoppable.add(projectMetadata.value)
        }
        stoppable.add(store)
        stoppable.stop()
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
        Instrumented.setListener(instrumentedInputAccessListener)
    }

    private
    fun doneWithWork() {
        Instrumented.discardListener()
        cacheFingerprintController.stopCollectingFingerprint()
    }

    private
    fun saveModel(model: Any) {
        saveToCache(StateType.Model) { stateFile ->
            cacheIO.writeModelTo(model, stateFile)
        }
    }

    private
    fun saveWorkGraph() {
        saveToCache(StateType.Work) { layout -> writeConfigurationCacheState(layout) }
    }

    private
    fun saveToCache(stateType: StateType, action: (ConfigurationCacheStateFile) -> Unit) {

        cacheEntryRequiresCommit = true

        buildOperationExecutor.withStoreOperation(cacheKey.string) {
            store.useForStore { layout ->
                try {
                    action(layout.fileFor(stateType))
                } catch (error: ConfigurationCacheError) {
                    // Invalidate state on serialization errors
                    problems.failingBuildDueToSerializationError()
                    throw error
                } finally {
                    scopeRegistryListener.dispose()
                }
            }
        }

        crossConfigurationTimeBarrier()
    }

    private
    fun loadModel(): Any {
        return loadFromCache(StateType.Model) { stateFile ->
            cacheIO.readModelFrom(stateFile)
        }
    }

    private
    fun loadWorkGraph(graph: BuildTreeWorkGraph, graphBuilder: BuildTreeWorkGraphBuilder?, loadAfterStore: Boolean): BuildTreeWorkGraph.FinalizedGraph {
        return loadFromCache(StateType.Work) { stateFile ->
            cacheIO.readRootBuildStateFrom(stateFile, loadAfterStore, graph, graphBuilder)
        }
    }

    private
    fun <T : Any> loadFromCache(stateType: StateType, action: (ConfigurationCacheStateFile) -> T): T {
        prepareConfigurationTimeBarrier()

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        val result = buildOperationExecutor.withLoadOperation {
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
    fun writeConfigurationCacheState(stateFile: ConfigurationCacheStateFile) =
        host.currentBuild.gradle.owner.projects.withMutableStateOfAllProjects {
            cacheIO.writeRootBuildStateTo(stateFile)
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
        cacheFingerprintController.maybeStartCollectingFingerprint(store.assignSpoolFile(StateType.BuildFingerprint), store.assignSpoolFile(StateType.ProjectFingerprint)) {
            cacheFingerprintWriterContextFor(encryptionService.outputStream(it.stateType, it.file::outputStream))
        }
    }

    private
    fun cacheFingerprintWriterContextFor(outputStream: OutputStream): DefaultWriteContext {
        val (context, codecs) = cacheIO.writerContextFor(outputStream, "fingerprint")
        return context.apply {
            push(IsolateOwner.OwnerHost(host), codecs.fingerprintTypesCodec())
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
            intermediateModels.value.restoreFromCacheEntry(entryDetails.intermediateModels, projectResult)
            projectMetadata.value.restoreFromCacheEntry(entryDetails.projectMetadata, projectResult)
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
                withIsolate(IsolateOwner.OwnerHost(host), codecs.fingerprintTypesCodec()) {
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
    fun logBootstrapSummary(message: String, vararg args: Any?) {
        log(message, *args)
    }

    private
    fun log(message: String, vararg args: Any?) {
        logger.log(configurationCacheLogLevel, message, *args)
    }

    private
    val configurationCacheLogLevel: LogLevel
        get() = startParameter.configurationCacheLogLevel
}


internal
inline fun <reified T> DefaultConfigurationCache.Host.service(): T =
    service(T::class.java)


internal
val logger = Logging.getLogger(DefaultConfigurationCache::class.java)
