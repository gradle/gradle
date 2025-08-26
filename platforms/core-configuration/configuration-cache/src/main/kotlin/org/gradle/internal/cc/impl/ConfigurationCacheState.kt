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

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.cache.Cleanup
import org.gradle.api.cache.MarkingStrategy
import org.gradle.api.file.FileCollection
import org.gradle.api.flow.FlowScope
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal.BUILD_SRC
import org.gradle.api.internal.cache.CacheConfigurationsInternal
import org.gradle.api.internal.cache.CacheResourceConfigurationInternal.EntryRetention
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.internal.BuildServiceProvider
import org.gradle.api.services.internal.RegisteredBuildServiceProvider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.caching.configuration.BuildCache
import org.gradle.execution.plan.Node
import org.gradle.execution.plan.ScheduledWork
import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.BuildStructureOperationProject
import org.gradle.initialization.ProjectsIdentifiedProgressDetails
import org.gradle.initialization.RootBuildCacheControllerSettingsProcessor
import org.gradle.internal.Actions
import org.gradle.internal.build.BuildProjectRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.build.NestedBuildState
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.build.StandAloneNestedBuild
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal.Subscription
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.buildtree.BuildTreeWorkGraph
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.base.serialize.service
import org.gradle.internal.cc.base.serialize.withGradleIsolate
import org.gradle.internal.cc.base.services.ProjectRefResolver
import org.gradle.internal.cc.impl.serialize.ConfigurationCacheCodecs
import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.DocumentationSection.NotYetImplementedSourceDependencies
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.file.FileSystemDefaultExcludesProvider
import org.gradle.internal.flow.services.BuildFlowScope
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.serialize.codecs.core.IsolateContextSource
import org.gradle.internal.serialize.graph.MutableReadContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.logNotImplemented
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readEnum
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.readStrings
import org.gradle.internal.serialize.graph.readStringsSet
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.graph.withDebugFrame
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeEnum
import org.gradle.internal.serialize.graph.writeStrings
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.util.Path
import org.gradle.vcs.internal.VcsMappingsStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream


typealias BuildTreeWorkGraphBuilder = BuildTreeWorkGraph.Builder.(BuildState) -> Unit


internal
enum class StateType(val encryptable: Boolean = false) {
    /**
     * Contains the state for the entire build.
     */
    Work(true),

    /**
     * Contains work-related state meant to be shared for the entire build.
     */
    WorkShared(true),

    /**
     * Contains the side effects observed during the creation of the [Model].
     */
    ModelSideEffects(true),

    /**
     * Contains the model objects sent back to the IDE in response to a TAPI request.
     */
    Model(true),

    /**
     * Contains the model objects queried by the IDE-provided build action in order to calculate the model to send back.
     */
    IntermediateModels(true),

    /**
     * Contains the dependency resolution metadata for each project.
     */
    ProjectMetadata(false),
    BuildFingerprint(true),
    ProjectFingerprint(true),

    /**
     * Contains the [ClassLoaderScope specifications][org.gradle.internal.cc.impl.serialize.ClassLoaderScopeSpec]
     * required to restore the specific configuration cache entry it is associated with.
     */
    ClassLoaderScopes(false),

    /**
     * The index file that points to all of these things
     */
    Entry(false),

    /**
     * The per cache-key file that lists all known configuration cache entries
     * for that key.
     */
    Candidates(false)
}


internal
interface ConfigurationCacheStateFile {
    val exists: Boolean
    val stateType: StateType
    val stateFile: ConfigurationCacheStateStore.StateFile
    val name: String get() = "${stateFile.name} ($stateType)"
    fun outputStream(): OutputStream
    fun inputStream(): InputStream
    fun delete()

    // Replace the contents of this state file by moving the given file to the location of this state file
    fun moveFrom(file: File)
    fun stateFileForIncludedBuild(build: BuildDefinition): ConfigurationCacheStateFile
    fun relatedStateFile(path: Path): ConfigurationCacheStateFile
    fun stateFileForSharedObjects(): ConfigurationCacheStateFile
}


@Suppress("LargeClass")
internal
class ConfigurationCacheState(
    private val codecs: ConfigurationCacheCodecs,
    private val stateFile: ConfigurationCacheStateFile,
    private val contextSource: IsolateContextSource,
    private val eventEmitter: BuildOperationProgressEventEmitter,
    private val host: ConfigurationCacheHost
) {

    suspend fun WriteContext.writeRootBuildState(rootBuild: BuildState) {
        writeBuildInvocationId()
        writeRootBuild(rootBuild).also {
            writeInt(0x1ecac8e)
        }
    }

    suspend fun MutableReadContext.readRootBuildState(
        graph: BuildTreeWorkGraph,
        graphBuilder: BuildTreeWorkGraphBuilder?,
        loadAfterStore: Boolean
    ): Pair<String, BuildTreeWorkGraph.FinalizedGraph> {

        val originBuildInvocationId = readBuildInvocationId()
        val builds = readRootBuild()
        require(readInt() == 0x1ecac8e) {
            "corrupt state file"
        }
        if (!loadAfterStore) {
            for (build in builds) {
                identifyBuild(build)
            }
        }
        return originBuildInvocationId to calculateRootTaskGraph(builds, graph, graphBuilder)
    }

    private
    fun WriteContext.writeBuildInvocationId() {
        writeString(host.service<BuildInvocationScopeId>().id.asString())
    }

    private
    fun ReadContext.readBuildInvocationId(): String =
        readString()

    private
    fun identifyBuild(state: CachedBuildState) {
        val identityPath = state.identityPath.toString()

        eventEmitter.emitNowForCurrent(BuildIdentifiedProgressDetails { identityPath })

        if (state is BuildWithProjects) {
            val projects = convertProjects(state.projects, state.rootProjectName)
            eventEmitter.emitNowForCurrent(object : ProjectsIdentifiedProgressDetails {
                override fun getBuildPath() = identityPath
                override fun getRootProject() = projects
            })
        }
    }

    private
    fun convertProjects(projects: List<CachedProjectState>, rootProjectName: String): ProjectsIdentifiedProgressDetails.Project {
        val children = projects.groupBy { it.path.parent }
        val converted = mutableMapOf<Path, BuildStructureOperationProject>()
        for (project in projects) {
            convertProject(converted, project, rootProjectName, children)
        }
        return converted.getValue(Path.ROOT)
    }

    private
    fun convertProject(
        converted: MutableMap<Path, BuildStructureOperationProject>,
        project: CachedProjectState,
        rootProjectName: String,
        children: Map<Path?, List<CachedProjectState>>
    ): BuildStructureOperationProject {
        val childProjects = children.getOrDefault(project.path, emptyList()).map { convertProject(converted, it, rootProjectName, children) }.toSet()
        return converted.computeIfAbsent(project.path) {
            // Root project name is serialized separately, could perhaps move it to this cached project state object
            val projectName = project.path.name ?: rootProjectName
            BuildStructureOperationProject(projectName, project.path.asString(), project.path.asString(), project.projectDir.absolutePath, project.buildFile.absolutePath, childProjects)
        }
    }

    private
    fun calculateRootTaskGraph(builds: List<CachedBuildState>, graph: BuildTreeWorkGraph, graphBuilder: BuildTreeWorkGraphBuilder?): BuildTreeWorkGraph.FinalizedGraph {
        return graph.scheduleWork { builder ->

            graphBuilder?.invoke(builder, rootBuildState())

            for (build in builds) {
                if (build is BuildWithWork) {
                    builder.withWorkGraph(build.build.state) {
                        it.setScheduledWork(build.workGraph)
                    }
                }
            }
        }
    }

    private
    fun rootBuildState() =
        host.service<BuildState>()

    private
    suspend fun WriteContext.writeRootBuild(rootBuild: BuildState) {
        require(rootBuild.mutableModel.isRootBuild)
        val gradle = rootBuild.mutableModel
        withDebugFrame({ "Gradle" }) {
            write(gradle.settings.settingsScript.resource.location.file)
            writeBuildTreeScopedState(gradle)
        }
        val buildEventListeners = buildEventListenersOf(gradle)
        writeBuildsInTree(buildEventListeners)
    }

    private
    suspend fun MutableReadContext.readRootBuild(): List<CachedBuildState> {
        val settingsFile = read() as File?
        val rootBuild = host.createBuild(settingsFile)
        val gradle = rootBuild.gradle
        readBuildTreeScopedState(gradle)
        return readBuildsInTree(rootBuild)
    }

    private
    suspend fun WriteContext.writeBuildsInTree(buildEventListeners: List<RegisteredBuildServiceProvider<*, *>>) {
        val requiredBuildServicesPerBuild = buildEventListeners.groupBy { it.buildIdentifier }
        val builds = mutableMapOf<BuildState, BuildToStore>()
        host.visitBuilds { state ->
            val gradle = state.mutableModel
            val hasScheduledWork = gradle.taskGraph.hasScheduledWork()
            builds[state] = BuildToStore(state, hasScheduledWork, hasChildren = gradle.isRootBuild)
            if (hasScheduledWork && state is StandAloneNestedBuild) {
                // Also require the owner of a buildSrc build
                builds[state.owner] = builds.getValue(state.owner).hasChildren()
            }
        }
        writeCollection(builds.values) { build ->
            writeBuildState(
                build,
                StoredBuildTreeState(requiredBuildServicesPerBuild)
            )
        }
    }

    private
    suspend fun MutableReadContext.readBuildsInTree(rootBuild: ConfigurationCacheBuild): List<CachedBuildState> {
        return readList {
            readBuildState(rootBuild)
        }
    }

    private
    suspend fun WriteContext.writeBuildState(build: BuildToStore, buildTreeState: StoredBuildTreeState) {
        val state = build.build
        when {
            !build.hasWork && !build.hasChildren -> {
                writeEnum(BuildType.BuildWithNoWork)
                writeBuildWithNoWork(state)
            }

            state is RootBuildState -> {
                writeEnum(BuildType.RootBuild)
                writeBuildContent(state, buildTreeState)
            }

            state is IncludedBuildState -> {
                writeEnum(BuildType.IncludedBuild)
                writeIncludedBuild(state, buildTreeState)
            }

            state is StandAloneNestedBuild -> {
                writeEnum(BuildType.BuildSrcBuild)
                writeBuildSrcBuild(state, buildTreeState)
            }

            else -> {
                error("Unexpected build state ${state.javaClass.name}")
            }
        }
    }

    private
    suspend fun MutableReadContext.readBuildState(rootBuild: ConfigurationCacheBuild): CachedBuildState {
        return when (readEnum<BuildType>()) {
            BuildType.BuildWithNoWork -> readBuildWithNoWork(rootBuild)
            BuildType.RootBuild -> readBuildContent(rootBuild)
            BuildType.IncludedBuild -> readIncludedBuild(rootBuild)
            BuildType.BuildSrcBuild -> readBuildSrcBuild(rootBuild)
        }
    }

    private
    suspend fun WriteContext.writeIncludedBuild(state: IncludedBuildState, buildTreeState: StoredBuildTreeState) {
        val gradle = state.mutableModel
        withGradleIsolate(gradle, userTypesCodec) {
            write(gradle.settings.settingsScript.resource.file)
            writeBuildDefinition(state.buildDefinition)
            write(state.identityPath)
        }
        // Encode the build state using the contextualized IO service for the nested build
        gradle.serviceOf<ConfigurationCacheIncludedBuildIO>().run {
            writeIncludedBuildStateTo(
                stateFileFor(state.buildDefinition),
                buildTreeState
            )
        }
    }

    private
    suspend fun ReadContext.readIncludedBuild(rootBuild: ConfigurationCacheBuild): CachedBuildState {
        val build = withGradleIsolate(rootBuild.gradle, userTypesCodec) {
            val settingsFile = read() as File?
            val definition = readIncludedBuildDefinition(rootBuild)
            val buildPath = read() as Path
            rootBuild.addIncludedBuild(definition, settingsFile, buildPath)
        }
        return readNestedBuildState(build)
    }

    private
    suspend fun WriteContext.writeBuildSrcBuild(state: StandAloneNestedBuild, buildTreeState: StoredBuildTreeState) {
        val gradle = state.mutableModel
        withGradleIsolate(gradle, userTypesCodec) {
            write(state.owner.buildIdentifier)
        }
        // Encode the build state using the contextualized IO service for the nested build
        gradle.serviceOf<ConfigurationCacheIncludedBuildIO>().run {
            writeIncludedBuildStateTo(
                stateFileFor(state.buildDefinition),
                buildTreeState
            )
        }
    }

    private
    suspend fun ReadContext.readBuildSrcBuild(rootBuild: ConfigurationCacheBuild): CachedBuildState {
        val build = withGradleIsolate(rootBuild.gradle, userTypesCodec) {
            val ownerIdentifier = readNonNull<BuildIdentifier>()
            rootBuild.getBuildSrcOf(ownerIdentifier)
        }
        return readNestedBuildState(build)
    }

    private
    fun ReadContext.readNestedBuildState(build: ConfigurationCacheBuild): CachedBuildState {
        // Decode the build state using the contextualized IO service for the build
        return build.gradle.serviceOf<ConfigurationCacheIncludedBuildIO>().run {
            readIncludedBuildStateFrom(
                stateFileFor((build.state as NestedBuildState).buildDefinition),
                build
            )
        }
    }

    private
    suspend fun WriteContext.writeBuildWithNoWork(buildState: BuildState) {
        withGradleIsolate(buildState.mutableModel, userTypesCodec) {
            writeString(buildState.identityPath.asString())
            if (buildState.isProjectsCreated) {
                writeBoolean(true)
                writeString(buildState.projects.rootProject.name)
                writeCollection(buildState.projects.allProjects) { project ->
                    write(ProjectWithNoWork(project.projectPath, project.projectDir, project.mutableModel.buildFile))
                }
            } else {
                writeBoolean(false)
            }
        }
    }

    private
    suspend fun ReadContext.readBuildWithNoWork(rootBuild: ConfigurationCacheBuild) =
        withGradleIsolate(rootBuild.gradle, userTypesCodec) {
            val identityPath = Path.path(readString())
            val hasProjects = readBoolean()
            if (hasProjects) {
                val rootProjectName = readString()
                val projects: List<ProjectWithNoWork> = readList().uncheckedCast()
                BuildWithNoWork(identityPath, rootProjectName, projects)
            } else {
                BuildWithNoProjects(identityPath)
            }
        }

    internal
    suspend fun WriteContext.writeBuildContent(buildState: BuildState, buildTreeState: StoredBuildTreeState) {
        val gradle = buildState.mutableModel
        if (buildState.isProjectsCreated) {
            writeBoolean(true)
            val scheduledWork = gradle.taskGraph.collectScheduledWork()
            withDebugFrame({ "Gradle" }) {
                writeGradleState(gradle)
                val projects = collectProjects(buildState.projects, scheduledWork.scheduledNodes, gradle.serviceOf())
                writeProjects(gradle, projects)
                writeRequiredBuildServicesOf(buildState, buildTreeState)
            }
            withDebugFrame({ "Work Graph" }) {
                writeWorkGraphOf(gradle, scheduledWork)
            }
            withDebugFrame({ "Flow Scope" }) {
                writeFlowScopeOf(gradle)
            }
            withDebugFrame({ "Cleanup registrations" }) {
                writeBuildOutputCleanupRegistrations(gradle)
            }
        } else {
            writeBoolean(false)
        }
    }

    internal
    suspend fun MutableReadContext.readBuildContent(build: ConfigurationCacheBuild): CachedBuildState {
        val gradle = build.gradle
        if (readBoolean()) {
            readGradleState(build)
            val projects = readProjects(gradle, build)

            build.createProjects()
            gradle.serviceOf<ProjectRefResolver>().projectsReady()

            applyProjectStates(projects, gradle)
            readRequiredBuildServicesOf(gradle)

            val workGraph = readWorkGraph(gradle)
            readFlowScopeOf(gradle)
            readBuildOutputCleanupRegistrations(gradle)
            return BuildWithWork(build.state.identityPath, build, gradle.rootProject.name, projects, workGraph)
        } else {
            return BuildWithNoProjects(build.state.identityPath)
        }
    }

    private
    fun WriteContext.writeWorkGraphOf(gradle: GradleInternal, scheduledWork: ScheduledWork) {
        workNodeCodec(gradle).run {
            writeWork(scheduledWork)
        }
    }

    private
    fun ReadContext.readWorkGraph(gradle: GradleInternal) =
        workNodeCodec(gradle).run {
            readWork()
        }

    private
    suspend fun WriteContext.writeFlowScopeOf(gradle: GradleInternal) {
        suspend fun WriteContext.doWriteFlowScopeOf(gradle: GradleInternal) {
            val flowScopeState = buildFlowScopeOf(gradle).store()
            write(flowScopeState)
        }

        withIsolate(IsolateOwners.OwnerFlowScope(gradle), userTypesCodec) {
            val buildState = gradle.owner
            if (buildState.isProjectsLoaded) {
                // Grab the allprojects lock to serialize the flow actions.
                // This is a workaround for parameters that may require dependency resolution under the hood.
                buildState.projects.withMutableStateOfAllProjects {
                    runWriteOperation {
                        doWriteFlowScopeOf(gradle)
                    }
                }
            } else {
                // Projects are not registered yet, but actions may be already scheduled in the settings context.
                // Let's run them without locks.
                doWriteFlowScopeOf(gradle)
            }
        }
    }

    private
    suspend fun ReadContext.readFlowScopeOf(gradle: GradleInternal) {
        withIsolate(IsolateOwners.OwnerFlowScope(gradle), userTypesCodec) {
            buildFlowScopeOf(gradle).load(readNonNull())
        }
    }

    private
    fun buildFlowScopeOf(gradle: GradleInternal) =
        gradle.serviceOf<FlowScope>().uncheckedCast<BuildFlowScope>()

    private
    fun workNodeCodec(gradle: GradleInternal) =
        codecs.workNodeCodecFor(gradle, contextSource)

    private
    suspend fun WriteContext.writeRequiredBuildServicesOf(build: BuildState, buildTreeState: StoredBuildTreeState) {
        withGradleIsolate(build.mutableModel, userTypesCodec) {
            val providers = buildTreeState.requiredBuildServicesPerBuild[build.buildIdentifier] ?: emptyList()
            writeCollection(providers) { listener ->
                writeBuildEventListenerSubscription(listener)
            }
        }
    }

    private
    suspend fun ReadContext.readRequiredBuildServicesOf(gradle: GradleInternal) {
        val eventListenerRegistry by lazy { gradle.serviceOf<BuildEventListenerRegistryInternal>() }
        withGradleIsolate(gradle, userTypesCodec) {
            readCollection {
                readBuildEventListenerSubscription(eventListenerRegistry)
            }
        }
    }

    private
    suspend fun WriteContext.writeBuildEventListenerSubscription(listener: Provider<*>) {
        write(listener)
    }

    private
    suspend fun ReadContext.readBuildEventListenerSubscription(eventListenerRegistry: BuildEventListenerRegistryInternal) {
        val listener = readNonNull<Provider<*>>()
        eventListenerRegistry.subscribe(listener)
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
    suspend fun WriteContext.writeBuildTreeScopedState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            withDebugFrame({ "environment state" }) {
                writePreviewFlags(gradle)
                writeFileSystemDefaultExcludes(gradle)
            }
            withDebugFrame({ "gradle enterprise" }) {
                writeGradleEnterprisePluginManager(gradle)
            }
            withDebugFrame({ "build cache" }) {
                writeBuildCacheConfiguration(gradle)
            }
            withDebugFrame({ "cache configurations" }) {
                writeCacheConfigurations(gradle)
            }
        }
    }

    private
    suspend fun ReadContext.readBuildTreeScopedState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            readPreviewFlags(gradle)
            readFileSystemDefaultExcludes(gradle)
            // It is important that the Develocity plugin be read before
            // build cache configuration, as it may contribute build cache configuration.
            readGradleEnterprisePluginManager(gradle)
            readBuildCacheConfiguration(gradle)
            readCacheConfigurations(gradle)
        }
    }

    private
    fun WriteContext.writeGradleState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            // per build
            writeStartParameterOf(gradle)
            writeChildBuilds(gradle)
        }
    }

    private
    fun ReadContext.readGradleState(
        build: ConfigurationCacheBuild
    ) {
        val gradle = build.gradle
        return withGradleIsolate(gradle, userTypesCodec) {
            // per build
            readStartParameterOf(gradle)
            readChildBuilds()
        }
    }

    private
    fun WriteContext.writeStartParameterOf(gradle: GradleInternal) {
        val startParameterTaskNames = gradle.startParameter.taskNames
        writeStrings(startParameterTaskNames)
    }

    private
    fun ReadContext.readStartParameterOf(gradle: GradleInternal) {
        // Restore startParameter.taskNames to enable `gradle.startParameter.setTaskNames(...)` idiom in included build scripts
        // See org/gradle/caching/configuration/internal/BuildCacheCompositeConfigurationIntegrationTest.groovy:134
        val startParameterTaskNames = readStrings()
        gradle.startParameter.setTaskNames(startParameterTaskNames)
    }

    private
    fun WriteContext.writeChildBuilds(gradle: GradleInternal) {
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
    fun ReadContext.readChildBuilds() {
        if (readBoolean()) {
            logNotImplemented(
                feature = "source dependencies",
                documentationSection = NotYetImplementedSourceDependencies
            )
        }
    }

    private
    suspend fun WriteContext.writeBuildDefinition(buildDefinition: BuildDefinition) {
        buildDefinition.run {
            writeString(name!!)
            write(buildRootDir)
            write(fromBuild)
            writeBoolean(isPluginBuild)
        }
    }

    private
    suspend fun ReadContext.readIncludedBuildDefinition(parentBuild: ConfigurationCacheBuild): BuildDefinition {
        val includedBuildName = readString()
        val includedBuildRootDir: File? = read()?.uncheckedCast()
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
    fun WriteContext.writeFileSystemDefaultExcludes(gradle: GradleInternal) {
        val fileSystemDefaultExcludesProvider = gradle.serviceOf<FileSystemDefaultExcludesProvider>()
        val currentDefaultExcludes = fileSystemDefaultExcludesProvider.currentDefaultExcludes
        writeStrings(currentDefaultExcludes.toList())
    }

    private
    fun ReadContext.readFileSystemDefaultExcludes(gradle: GradleInternal) {
        val defaultExcludes = readStringsSet()
        gradle.serviceOf<FileSystemDefaultExcludesProvider>().updateCurrentDefaultExcludes(defaultExcludes)
    }

    private
    suspend fun WriteContext.writeBuildCacheConfiguration(gradle: GradleInternal) {
        gradle.settings.buildCache.let { buildCache ->
            write(buildCache.local)
            write(buildCache.remote)
            write(buildCache.registrations)
        }
    }

    private
    suspend fun ReadContext.readBuildCacheConfiguration(gradle: GradleInternal) {
        gradle.settings.buildCache.let { buildCache ->
            buildCache.local = readNonNull()
            buildCache.remote = read() as BuildCache?
            buildCache.registrations = readNonNull()
        }
        RootBuildCacheControllerSettingsProcessor.process(gradle)
    }

    private
    suspend fun WriteContext.writeCacheConfigurations(gradle: GradleInternal) {
        gradle.settings.caches.let { cacheConfigurations ->
            write(cacheConfigurations.releasedWrappers.entryRetention)
            write(cacheConfigurations.snapshotWrappers.entryRetention)
            write(cacheConfigurations.downloadedResources.entryRetention)
            write(cacheConfigurations.createdResources.entryRetention)
            write(cacheConfigurations.buildCache.entryRetention)
            write(cacheConfigurations.cleanup)
            write(cacheConfigurations.markingStrategy)
        }
    }

    private
    suspend fun ReadContext.readCacheConfigurations(gradle: GradleInternal) {
        gradle.settings.caches.let { cacheConfigurations ->
            cacheConfigurations.releasedWrappers.entryRetention.value(readNonNull<Provider<EntryRetention>>())
            cacheConfigurations.snapshotWrappers.entryRetention.value(readNonNull<Provider<EntryRetention>>())
            cacheConfigurations.downloadedResources.entryRetention.value(readNonNull<Provider<EntryRetention>>())
            cacheConfigurations.createdResources.entryRetention.value(readNonNull<Provider<EntryRetention>>())
            cacheConfigurations.buildCache.entryRetention.value(readNonNull<Provider<EntryRetention>>())
            cacheConfigurations.cleanup.value(readNonNull<Provider<Cleanup>>())
            cacheConfigurations.markingStrategy.value(readNonNull<Provider<MarkingStrategy>>())
        }
        if (gradle.isRootBuild) {
            gradle.serviceOf<CacheConfigurationsInternal>().setCleanupHasBeenConfigured(true)
        }
    }

    private
    suspend fun WriteContext.writeBuildOutputCleanupRegistrations(gradle: GradleInternal) {
        val buildOutputCleanupRegistry = gradle.serviceOf<BuildOutputCleanupRegistry>()
        withGradleIsolate(gradle, userTypesCodec) {
            writeCollection(buildOutputCleanupRegistry.registeredOutputs)
        }
    }

    private
    suspend fun ReadContext.readBuildOutputCleanupRegistrations(gradle: GradleInternal) {
        val buildOutputCleanupRegistry = gradle.serviceOf<BuildOutputCleanupRegistry>()
        withGradleIsolate(gradle, userTypesCodec) {
            readCollection {
                val files = readNonNull<FileCollection>()
                buildOutputCleanupRegistry.registerOutputs(files)
            }
        }
    }

    private
    suspend fun WriteContext.writePreviewFlags(gradle: GradleInternal) {
        val featureFlags = gradle.serviceOf<FeatureFlags>()
        val enabledFeatures = FeaturePreviews.Feature.entries.filter { featureFlags.isEnabledWithApi(it) }
        writeCollection(enabledFeatures)
    }

    private
    suspend fun ReadContext.readPreviewFlags(gradle: GradleInternal) {
        val featureFlags = gradle.serviceOf<FeatureFlags>()
        readCollection {
            val enabledFeature = read() as FeaturePreviews.Feature
            featureFlags.enable(enabledFeature)
        }
    }

    private
    suspend fun WriteContext.writeGradleEnterprisePluginManager(gradle: GradleInternal) {
        val manager = gradle.serviceOf<GradleEnterprisePluginManager>()
        val adapter = manager.adapter
        val writtenAdapter = adapter?.takeIf {
            it.shouldSaveToConfigurationCache()
        }
        write(writtenAdapter)
    }

    private
    suspend fun ReadContext.readGradleEnterprisePluginManager(gradle: GradleInternal) {
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
            if (relevantProjects.contains(project)) {
                mutableModel.layout.buildDirectory.finalizeValue()
                ProjectWithWork(project.projectPath, mutableModel.projectDir, mutableModel.buildFile, mutableModel.layout.buildDirectory.asFile.get(), mutableModel.normalization.computeCachedState())
            } else {
                ProjectWithNoWork(project.projectPath, mutableModel.projectDir, mutableModel.buildFile)
            }
        }
    }

    private
    suspend fun WriteContext.writeProjects(gradle: GradleInternal, projects: List<CachedProjectState>) {
        writeString(gradle.rootProject.name)
        withGradleIsolate(gradle, userTypesCodec) {
            writeCollection(projects)
        }
    }

    private
    suspend fun ReadContext.readProjects(gradle: GradleInternal, build: ConfigurationCacheBuild): List<CachedProjectState> {
        withGradleIsolate(gradle, userTypesCodec) {
            val rootProjectName = readString()
            return readList {
                val project = readNonNull<CachedProjectState>()
                if (project is ProjectWithWork) {
                    if (project.path == Path.ROOT) {
                        build.registerRootProject(rootProjectName, project.projectDir, project.buildDir)
                    } else {
                        build.registerProject(project.path, project.projectDir, project.buildDir)
                    }
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
    fun WriteContext.buildEventListenersOf(gradle: GradleInternal): List<RegisteredBuildServiceProvider<*, *>> {
        val subscriptions = gradle.serviceOf<BuildEventListenerRegistryInternal>().subscriptions

        val validProviders = mutableListOf<RegisteredBuildServiceProvider<*, *>>()
        val invalidProviders = mutableListOf<Subscription>()

        subscriptions.forEach { subscription ->
            val listener = subscription.listener
            if (listener is RegisteredBuildServiceProvider<*, *>) {
                if (isRelevantBuildEventListener(listener)) {
                    validProviders.add(listener)
                }
            } else {
                invalidProviders.add(subscription)
            }
        }
        if (!gradle.startParameter.isConfigurationCacheIgnoreUnsupportedBuildEventsListeners) {
            invalidProviders.forEach { subscription ->
                onProblem(
                    PropertyProblem(
                        subscription.location(),
                        StructuredMessage.build {
                            text("Unsupported provider is registered as a task completion listener in ")
                            reference(BuildEventsListenerRegistry::class)
                            text(". Configuration Cache only supports providers returned from ")
                            reference(BuildServiceRegistry::class)
                            text(" as task completion listeners.")
                        },
                        documentationSection = DocumentationSection.NotYetImplementedBuildEventListeners
                    )
                )
            }
        }
        return validProviders
    }

    private
    fun Subscription.location(): PropertyTrace = registrationPoint?.let { PropertyTrace.BuildLogic(it) } ?: PropertyTrace.Unknown

    private
    fun isRelevantBuildEventListener(provider: RegisteredBuildServiceProvider<*, *>) =
        Path.path(provider.buildIdentifier.buildPath).name != BUILD_SRC
}


internal
class StoredBuildTreeState(
    val requiredBuildServicesPerBuild: Map<BuildIdentifier, List<BuildServiceProvider<*, *>>>
)


internal
enum class BuildType {
    BuildWithNoWork, RootBuild, IncludedBuild, BuildSrcBuild
}
