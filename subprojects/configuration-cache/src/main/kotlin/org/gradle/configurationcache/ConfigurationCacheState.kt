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

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal.BUILD_SRC
import org.gradle.api.provider.Provider
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.api.services.internal.BuildServiceRegistryInternal
import org.gradle.api.services.internal.RegisteredBuildServiceProvider
import org.gradle.caching.configuration.BuildCache
import org.gradle.caching.configuration.internal.BuildCacheServiceRegistration
import org.gradle.configurationcache.extensions.serviceOf
import org.gradle.configurationcache.extensions.unsafeLazy
import org.gradle.configurationcache.problems.DocumentationSection.NotYetImplementedSourceDependencies
import org.gradle.configurationcache.serialization.DefaultReadContext
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.codecs.Codecs
import org.gradle.configurationcache.serialization.logNotImplemented
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.readFile
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.readStrings
import org.gradle.configurationcache.serialization.withDebugFrame
import org.gradle.configurationcache.serialization.withGradleIsolate
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.configurationcache.serialization.writeFile
import org.gradle.configurationcache.serialization.writeStrings
import org.gradle.configurationcache.services.EnvironmentChangeTracker
import org.gradle.execution.plan.Node
import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.BuildStructureOperationProject
import org.gradle.initialization.ProjectsIdentifiedProgressDetails
import org.gradle.initialization.RootBuildCacheControllerSettingsProcessor
import org.gradle.internal.Actions
import org.gradle.internal.build.BuildProjectRegistry
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.CompositeBuildParticipantBuildState
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.composite.IncludedBuildInternal
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.util.Path
import org.gradle.vcs.internal.VcsMappingsStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream


internal
enum class StateType {
    Work, Model, Entry, BuildFingerprint, ProjectFingerprint, IntermediateModels, ProjectMetadata
}


internal
interface ConfigurationCacheStateFile {
    val exists: Boolean
    fun outputStream(): OutputStream
    fun inputStream(): InputStream
    fun delete()

    // Replace the contents of this state file, by moving the given file to the location of this state file
    fun moveFrom(file: File)
    fun stateFileForIncludedBuild(build: BuildDefinition): ConfigurationCacheStateFile
}


internal
class ConfigurationCacheState(
    private val codecs: Codecs,
    private val stateFile: ConfigurationCacheStateFile,
    private val eventEmitter: BuildOperationProgressEventEmitter
) {
    /**
     * Writes the state for the whole build starting from the given root [build] and returns the set
     * of stored included build directories.
     */
    suspend fun DefaultWriteContext.writeRootBuildState(build: VintageGradleBuild) =
        writeRootBuild(build).also {
            writeInt(0x1ecac8e)
        }

    suspend fun DefaultReadContext.readRootBuildState(graph: BuildTreeWorkGraph, loadAfterStore: Boolean, createBuild: (File?, String) -> ConfigurationCacheBuild): BuildTreeWorkGraph.FinalizedGraph {
        val buildState = readRootBuild(createBuild)
        require(readInt() == 0x1ecac8e) {
            "corrupt state file"
        }
        if (!loadAfterStore) {
            identifyBuild(buildState)
        }

        return calculateRootTaskGraph(buildState, graph)
    }

    private
    fun identifyBuild(state: CachedBuildState) {
        val gradle = state.build.gradle
        val identityPath = gradle.identityPath.toString()

        eventEmitter.emitNowForCurrent(BuildIdentifiedProgressDetails { identityPath })

        val projects = convertProjects(state.projects, gradle.rootProject.name)
        eventEmitter.emitNowForCurrent(object : ProjectsIdentifiedProgressDetails {
            override fun getBuildPath() = identityPath
            override fun getRootProject() = projects
        })
        state.children.forEach(::identifyBuild)
    }

    private fun convertProjects(projects: List<CachedProjectState>, rootProjectName: String): ProjectsIdentifiedProgressDetails.Project {
        val children = projects.groupBy { it.path.parent }
        val converted = mutableMapOf<Path, BuildStructureOperationProject>()
        for (project in projects) {
            convertProject(converted, project, rootProjectName, children)
        }
        return converted.getValue(Path.ROOT)
    }

    private fun convertProject(converted: MutableMap<Path, BuildStructureOperationProject>,
                               project: CachedProjectState,
                               rootProjectName: String,
                               children: Map<Path?, List<CachedProjectState>>): BuildStructureOperationProject {
        val childProjects = children.getOrDefault(project.path, emptyList()).map { convertProject(converted, it, rootProjectName, children) }.toSet()
        return converted.computeIfAbsent(project.path) {
            // Root project name is serialized separately, could perhaps move it to the cached project state
            val projectName = project.path.name ?: rootProjectName
            BuildStructureOperationProject(projectName, project.path.path, project.path.path, project.projectDir.absolutePath, project.buildDir.absolutePath, childProjects)
        }
    }

    private
    fun calculateRootTaskGraph(state: CachedBuildState, graph: BuildTreeWorkGraph): BuildTreeWorkGraph.FinalizedGraph {
        return graph.scheduleWork { builder ->
            builder.withWorkGraph(state.build.state) {
                it.setScheduledNodes(state.workGraph)
            }
            for (child in state.children) {
                addNodesForChildBuilds(child, builder)
            }
        }
    }

    private
    fun addNodesForChildBuilds(state: CachedBuildState, builder: BuildTreeWorkGraph.Builder) {
        builder.withWorkGraph(state.build.state) {
            it.setScheduledNodes(state.workGraph)
        }
        for (child in state.children) {
            addNodesForChildBuilds(child, builder)
        }
    }

    private
    suspend fun DefaultWriteContext.writeRootBuild(build: VintageGradleBuild) {
        require(build.gradle.owner is RootBuildState)
        val gradle = build.gradle
        withDebugFrame({ "Gradle" }) {
            write(gradle.settings.settingsScript.resource.file)
            writeString(gradle.rootProject.name)
            writeBuildTreeState(gradle)
        }
        val buildEventListeners = buildEventListenersOf(gradle)
        writeBuildState(
            build,
            StoredBuildTreeState(
                storedBuilds = storedBuilds(),
                requiredBuildServicesPerBuild = buildEventListeners
                    .groupBy { it.buildIdentifier }
            )
        )
        writeRootEventListenerSubscriptions(gradle, buildEventListeners)
    }

    private
    suspend fun DefaultReadContext.readRootBuild(
        createBuild: (File?, String) -> ConfigurationCacheBuild
    ): CachedBuildState {
        val settingsFile = read() as File?
        val rootProjectName = readString()
        val build = createBuild(settingsFile, rootProjectName)
        val gradle = build.gradle
        readBuildTreeState(gradle)
        val rootBuildState = readBuildState(build)
        readRootEventListenerSubscriptions(gradle)
        return rootBuildState
    }

    internal
    suspend fun DefaultWriteContext.writeBuildState(build: VintageGradleBuild, buildTreeState: StoredBuildTreeState) {
        val gradle = build.gradle
        withDebugFrame({ "Gradle" }) {
            writeGradleState(gradle, buildTreeState)
        }
        withDebugFrame({ "Work Graph" }) {
            val scheduledNodes = build.scheduledWork
            val projects = collectProjects(gradle.owner.projects, scheduledNodes, gradle.serviceOf())
            writeProjects(gradle, projects)
            writeRequiredBuildServicesOf(gradle, buildTreeState)
            writeWorkGraphOf(gradle, scheduledNodes)
        }
        withDebugFrame({ "cleanup registrations" }) {
            writeBuildOutputCleanupRegistrations(gradle)
        }
    }

    internal
    suspend fun DefaultReadContext.readBuildState(build: ConfigurationCacheBuild): CachedBuildState {

        val gradle = build.gradle

        val children = readGradleState(build)

        val projects = readProjects(gradle, build)

        build.registerProjects()

        initProjectProvider(build::getProject)

        applyProjectStates(projects, gradle)
        readRequiredBuildServicesOf(gradle)

        val workGraph = readWorkGraph(gradle)
        readBuildOutputCleanupRegistrations(gradle)
        return CachedBuildState(build, projects, workGraph, children)
    }

    private
    suspend fun DefaultWriteContext.writeWorkGraphOf(gradle: GradleInternal, scheduledNodes: List<Node>) {
        workNodeCodec(gradle).run {
            writeWork(scheduledNodes)
        }
    }

    private
    suspend fun DefaultReadContext.readWorkGraph(gradle: GradleInternal) =
        workNodeCodec(gradle).run {
            readWork()
        }

    private
    fun workNodeCodec(gradle: GradleInternal) =
        codecs.workNodeCodecFor(gradle)

    private
    suspend fun DefaultWriteContext.writeRequiredBuildServicesOf(gradle: GradleInternal, buildTreeState: StoredBuildTreeState) {
        withGradleIsolate(gradle, userTypesCodec) {
            write(buildTreeState.requiredBuildServicesPerBuild[buildIdentifierOf(gradle)])
        }
    }

    private
    suspend fun DefaultReadContext.readRequiredBuildServicesOf(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            read()
        }
    }

    private
    fun applyProjectStates(projects: List<CachedProjectState>, gradle: GradleInternal) {
        for (project in projects) {
            if (project is ProjectWithWork && project.normalizationState != null) {
                val projectState = gradle.owner.projects.getProject(project.path)
                projectState.mutableModel.normalization.configureFromCachedState(project.normalizationState)
            }
        }
    }

    private
    suspend fun DefaultWriteContext.writeBuildTreeState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            withDebugFrame({ "environment state" }) {
                writeCachedEnvironmentState(gradle)
                writePreviewFlags(gradle)
            }
            withDebugFrame({ "gradle enterprise" }) {
                writeGradleEnterprisePluginManager(gradle)
            }
            withDebugFrame({ "build cache" }) {
                writeBuildCacheConfiguration(gradle)
            }
        }
    }

    private
    suspend fun DefaultReadContext.readBuildTreeState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            readCachedEnvironmentState(gradle)
            readPreviewFlags(gradle)
            // It is important that the Gradle Enterprise plugin be read before
            // build cache configuration, as it may contribute build cache configuration.
            readGradleEnterprisePluginManager(gradle)
            readBuildCacheConfiguration(gradle)
        }
    }

    private
    suspend fun DefaultWriteContext.writeRootEventListenerSubscriptions(gradle: GradleInternal, listeners: List<Provider<*>>) {
        withGradleIsolate(gradle, userTypesCodec) {
            withDebugFrame({ "listener subscriptions" }) {
                writeBuildEventListenerSubscriptions(listeners)
            }
        }
    }

    private
    suspend fun DefaultReadContext.readRootEventListenerSubscriptions(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            readBuildEventListenerSubscriptions(gradle)
        }
    }

    private
    suspend fun DefaultWriteContext.writeGradleState(gradle: GradleInternal, buildTreeState: StoredBuildTreeState) {
        withGradleIsolate(gradle, userTypesCodec) {
            // per build
            writeStartParameterOf(gradle)
            withDebugFrame({ "included builds" }) {
                writeChildBuilds(gradle, buildTreeState)
            }
        }
    }

    private
    suspend fun DefaultReadContext.readGradleState(
        build: ConfigurationCacheBuild
    ): List<CachedBuildState> {
        val gradle = build.gradle
        withGradleIsolate(gradle, userTypesCodec) {
            // per build
            readStartParameterOf(gradle)
            return readChildBuildsOf(build)
        }
    }

    private
    fun DefaultWriteContext.writeStartParameterOf(gradle: GradleInternal) {
        val startParameterTaskNames = gradle.startParameter.taskNames
        writeStrings(startParameterTaskNames)
    }

    private
    fun DefaultReadContext.readStartParameterOf(gradle: GradleInternal) {
        // Restore startParameter.taskNames to enable `gradle.startParameter.setTaskNames(...)` idiom in included build scripts
        // See org/gradle/caching/configuration/internal/BuildCacheCompositeConfigurationIntegrationTest.groovy:134
        val startParameterTaskNames = readStrings()
        gradle.startParameter.setTaskNames(startParameterTaskNames)
    }

    private
    suspend fun DefaultWriteContext.writeChildBuilds(gradle: GradleInternal, buildTreeState: StoredBuildTreeState) {
        writeCollection(gradle.includedBuilds()) {
            writeIncludedBuildState(it, buildTreeState)
        }
        if (gradle.serviceOf<VcsMappingsStore>().asResolver().hasRules()) {
            logNotImplemented(
                feature = "source dependencies",
                documentationSection = NotYetImplementedSourceDependencies
            )
            writeBoolean(true)
        } else {
            writeBoolean(false)
        }
    }

    private
    suspend fun DefaultReadContext.readChildBuildsOf(
        parentBuild: ConfigurationCacheBuild
    ): List<CachedBuildState> {
        val includedBuilds = readList {
            readIncludedBuildState(parentBuild)
        }
        if (readBoolean()) {
            logNotImplemented(
                feature = "source dependencies",
                documentationSection = NotYetImplementedSourceDependencies
            )
        }
        parentBuild.gradle.setIncludedBuilds(includedBuilds.map { it.first.model })
        return includedBuilds.mapNotNull { it.second }
    }

    private
    suspend fun DefaultWriteContext.writeIncludedBuildState(
        reference: IncludedBuildInternal,
        buildTreeState: StoredBuildTreeState
    ) {
        when (val target = reference.target) {
            is IncludedBuildState -> {
                writeBoolean(true)
                val includedGradle = target.mutableModel
                val buildDefinition = includedGradle.serviceOf<BuildDefinition>()
                writeBuildDefinition(buildDefinition)
                when {
                    buildTreeState.storedBuilds.store(buildDefinition) -> {
                        writeBoolean(true)
                        target.projects.withMutableStateOfAllProjects {
                            includedGradle.serviceOf<ConfigurationCacheIO>().writeIncludedBuildStateTo(
                                stateFileFor(buildDefinition),
                                buildTreeState
                            )
                        }
                    }

                    else -> {
                        writeBoolean(false)
                    }
                }
            }

            is RootBuildState -> {
                writeBoolean(false)
            }

            else -> {
                TODO("Unsupported included build type '${target.javaClass}'")
            }
        }
    }

    private
    suspend fun DefaultReadContext.readIncludedBuildState(
        parentBuild: ConfigurationCacheBuild
    ): Pair<CompositeBuildParticipantBuildState, CachedBuildState?> =
        when {
            readBoolean() -> {
                val buildDefinition = readIncludedBuildDefinition(parentBuild)
                val includedBuild = parentBuild.addIncludedBuild(buildDefinition)
                val stored = readBoolean()
                val cachedBuildState =
                    if (stored) {
                        val confCacheBuild = includedBuild.withState { includedGradle ->
                            includedGradle.serviceOf<ConfigurationCacheHost>().createBuild(null, includedBuild.name)
                        }
                        confCacheBuild.gradle.serviceOf<ConfigurationCacheIO>().readIncludedBuildStateFrom(
                            stateFileFor(buildDefinition),
                            confCacheBuild
                        )
                    } else null
                includedBuild to cachedBuildState
            }

            else -> {
                rootBuildStateOf(parentBuild) to null
            }
        }

    private
    fun rootBuildStateOf(parentBuild: ConfigurationCacheBuild): RootBuildState =
        parentBuild.gradle.serviceOf<BuildStateRegistry>().rootBuild

    private
    suspend fun DefaultWriteContext.writeBuildDefinition(buildDefinition: BuildDefinition) {
        buildDefinition.run {
            writeString(name!!)
            writeFile(buildRootDir)
            write(fromBuild)
            writeBoolean(isPluginBuild)
        }
    }

    private
    suspend fun DefaultReadContext.readIncludedBuildDefinition(parentBuild: ConfigurationCacheBuild): BuildDefinition {
        val includedBuildName = readString()
        val includedBuildRootDir = readFile()
        val fromBuild = readNonNull<PublicBuildPath>()
        val pluginBuild = readBoolean()
        return BuildDefinition.fromStartParameterForBuild(
            parentBuild.gradle.startParameter,
            includedBuildName,
            includedBuildRootDir,
            PluginRequests.EMPTY,
            Actions.doNothing(),
            fromBuild,
            pluginBuild
        )
    }

    private
    suspend fun DefaultWriteContext.writeBuildCacheConfiguration(gradle: GradleInternal) {
        gradle.settings.buildCache.let { buildCache ->
            write(buildCache.local)
            write(buildCache.remote)
            write(buildCache.registrations)
        }
    }

    private
    suspend fun DefaultReadContext.readBuildCacheConfiguration(gradle: GradleInternal) {
        gradle.settings.buildCache.let { buildCache ->
            buildCache.local = readNonNull()
            buildCache.remote = read() as BuildCache?
            buildCache.registrations = readNonNull<MutableSet<BuildCacheServiceRegistration>>()
        }
        RootBuildCacheControllerSettingsProcessor.process(gradle)
    }

    private
    suspend fun DefaultWriteContext.writeBuildEventListenerSubscriptions(listeners: List<Provider<*>>) {
        writeCollection(listeners) { listener ->
            when (listener) {
                is RegisteredBuildServiceProvider<*, *> -> {
                    writeBoolean(true)
                    write(listener.buildIdentifier)
                    writeString(listener.name)
                }

                else -> {
                    writeBoolean(false)
                    write(listener)
                }
            }
        }
    }

    private
    suspend fun DefaultReadContext.readBuildEventListenerSubscriptions(gradle: GradleInternal) {
        val eventListenerRegistry by unsafeLazy {
            gradle.serviceOf<BuildEventListenerRegistryInternal>()
        }
        val buildStateRegistry by unsafeLazy {
            gradle.serviceOf<BuildStateRegistry>()
        }
        readCollection {
            when (readBoolean()) {
                true -> {
                    val buildIdentifier = readNonNull<BuildIdentifier>()
                    val serviceName = readString()
                    val provider = buildStateRegistry.buildServiceRegistrationOf(buildIdentifier).getByName(serviceName)
                    eventListenerRegistry.subscribe(provider.service)
                }

                else -> {
                    val provider = readNonNull<Provider<*>>()
                    eventListenerRegistry.subscribe(provider)
                }
            }
        }
    }

    private
    suspend fun DefaultWriteContext.writeBuildOutputCleanupRegistrations(gradle: GradleInternal) {
        val buildOutputCleanupRegistry = gradle.serviceOf<BuildOutputCleanupRegistry>()
        withGradleIsolate(gradle, userTypesCodec) {
            writeCollection(buildOutputCleanupRegistry.registeredOutputs)
        }
    }

    private
    suspend fun DefaultReadContext.readBuildOutputCleanupRegistrations(gradle: GradleInternal) {
        val buildOutputCleanupRegistry = gradle.serviceOf<BuildOutputCleanupRegistry>()
        withGradleIsolate(gradle, userTypesCodec) {
            readCollection {
                val files = readNonNull<FileCollection>()
                buildOutputCleanupRegistry.registerOutputs(files)
            }
        }
    }

    private
    suspend fun DefaultWriteContext.writeCachedEnvironmentState(gradle: GradleInternal) {
        val environmentChangeTracker = gradle.serviceOf<EnvironmentChangeTracker>()
        write(environmentChangeTracker.getCachedState())
    }

    private
    suspend fun DefaultReadContext.readCachedEnvironmentState(gradle: GradleInternal) {
        val environmentChangeTracker = gradle.serviceOf<EnvironmentChangeTracker>()
        val storedState = read() as EnvironmentChangeTracker.CachedEnvironmentState
        environmentChangeTracker.loadFrom(storedState)
    }

    private
    suspend fun DefaultWriteContext.writePreviewFlags(gradle: GradleInternal) {
        val featureFlags = gradle.serviceOf<FeatureFlags>()
        val enabledFeatures = FeaturePreviews.Feature.values().filter { featureFlags.isEnabledWithApi(it) }
        writeCollection(enabledFeatures)
    }

    private
    suspend fun DefaultReadContext.readPreviewFlags(gradle: GradleInternal) {
        val featureFlags = gradle.serviceOf<FeatureFlags>()
        readCollection {
            val enabledFeature = read() as FeaturePreviews.Feature
            featureFlags.enable(enabledFeature)
        }
    }

    private
    suspend fun DefaultWriteContext.writeGradleEnterprisePluginManager(gradle: GradleInternal) {
        val manager = gradle.serviceOf<GradleEnterprisePluginManager>()
        val adapter = manager.adapter
        val writtenAdapter = adapter?.takeIf {
            it.shouldSaveToConfigurationCache()
        }
        write(writtenAdapter)
    }

    private
    suspend fun DefaultReadContext.readGradleEnterprisePluginManager(gradle: GradleInternal) {
        val adapter = read() as GradleEnterprisePluginAdapter?
        if (adapter != null) {
            val manager = gradle.serviceOf<GradleEnterprisePluginManager>()
            if (manager.adapter == null) {
                // Don't replace the existing adapter. The adapter will be present if the current Gradle invocation wrote this entry.
                adapter.onLoadFromConfigurationCache()
                manager.registerAdapter(adapter)
            }
        }
    }

    private
    fun collectProjects(projects: BuildProjectRegistry, nodes: List<Node>, relevantProjectsRegistry: RelevantProjectsRegistry): List<CachedProjectState> {
        val relevantProjects = relevantProjectsRegistry.relevantProjects(nodes)
        return projects.allProjects.map { project ->
            val mutableModel = project.mutableModel
            mutableModel.layout.buildDirectory.finalizeValue()
            if (relevantProjects.contains(project)) {
                ProjectWithWork(project.projectPath, mutableModel.projectDir, mutableModel.buildDir, mutableModel.normalization.computeCachedState())
            } else {
                ProjectWithNoWork(project.projectPath, mutableModel.projectDir, mutableModel.buildDir)
            }
        }
    }

    private
    suspend fun WriteContext.writeProjects(gradle: GradleInternal, projects: List<CachedProjectState>) {
        withGradleIsolate(gradle, userTypesCodec) {
            writeCollection(projects)
        }
    }

    private
    suspend fun ReadContext.readProjects(gradle: GradleInternal, build: ConfigurationCacheBuild): List<CachedProjectState> {
        withGradleIsolate(gradle, userTypesCodec) {
            return readList {
                val project = readNonNull<CachedProjectState>()
                if (project is ProjectWithWork) {
                    build.createProject(project.path, project.projectDir, project.buildDir)
                }
                project
            }
        }
    }

    private
    fun stateFileFor(buildDefinition: BuildDefinition) =
        stateFile.stateFileForIncludedBuild(buildDefinition)

    private
    val userTypesCodec
        get() = codecs.userTypesCodec()

    private
    fun storedBuilds() = object : StoredBuilds {
        val buildRootDirs = hashSetOf<File>()
        override fun store(build: BuildDefinition): Boolean =
            buildRootDirs.add(build.buildRootDir!!)
    }

    private
    fun buildIdentifierOf(gradle: GradleInternal) =
        gradle.owner.buildIdentifier

    private
    fun buildEventListenersOf(gradle: GradleInternal) =
        gradle.serviceOf<BuildEventListenerRegistryInternal>()
            .subscriptions
            .filterIsInstance<RegisteredBuildServiceProvider<*, *>>()
            .filter(::isRelevantBuildEventListener)

    private
    fun isRelevantBuildEventListener(provider: RegisteredBuildServiceProvider<*, *>) =
        provider.buildIdentifier.name != BUILD_SRC

    private
    fun BuildStateRegistry.buildServiceRegistrationOf(buildId: BuildIdentifier) =
        getBuild(buildId).mutableModel.serviceOf<BuildServiceRegistryInternal>().registrations
}


internal
class StoredBuildTreeState(
    val storedBuilds: StoredBuilds,
    val requiredBuildServicesPerBuild: Map<BuildIdentifier, List<BuildServiceProvider<*, *>>>
)


internal
interface StoredBuilds {
    /**
     * Returns true if this is the first time the given [build] is seen and its state should be stored to the cache.
     * Returns false if the build has already been stored to the cache.
     */
    fun store(build: BuildDefinition): Boolean
}
