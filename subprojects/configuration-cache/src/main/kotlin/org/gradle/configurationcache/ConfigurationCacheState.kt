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

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.initialization.IncludedBuild
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.GradleInternal
import org.gradle.api.provider.Provider
import org.gradle.caching.configuration.BuildCache
import org.gradle.configurationcache.problems.DocumentationSection.NotYetImplementedSourceDependencies
import org.gradle.configurationcache.serialization.DefaultReadContext
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.codecs.Codecs
import org.gradle.configurationcache.serialization.codecs.WorkNodeCodec
import org.gradle.configurationcache.serialization.logNotImplemented
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.readFile
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.withDebugFrame
import org.gradle.configurationcache.serialization.withGradleIsolate
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.configurationcache.serialization.writeFile
import org.gradle.execution.plan.Node
import org.gradle.internal.Actions
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.build.PublicBuildPath
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.vcs.internal.VcsMappingsStore
import java.util.ArrayList


internal
class ConfigurationCacheState(
    private val codecs: Codecs,
    private val host: DefaultConfigurationCache.Host,
    private val relevantProjectsRegistry: RelevantProjectsRegistry
) {

    suspend fun DefaultWriteContext.writeState() {
        encodeScheduledWork()
        writeInt(0x1ecac8e)
    }

    suspend fun DefaultReadContext.readState() {
        decodeScheduledWork()
        require(readInt() == 0x1ecac8e) {
            "corrupt state file"
        }
    }

    private
    suspend fun DefaultWriteContext.encodeScheduledWork() {
        writeBuild(host.currentBuild)
    }

    private
    suspend fun DefaultReadContext.decodeScheduledWork() {
        readBuild(host::createBuild)
    }

    private
    suspend fun DefaultWriteContext.writeBuild(build: VintageGradleBuild) {
        val gradle = build.gradle
        withDebugFrame({ "Gradle" }) {
            writeBuildTreeState(gradle)
        }
        withDebugFrame({ "Gradle" }) {
            writeString(gradle.rootProject.name)
            writeGradleState(gradle)
        }
        withDebugFrame({ "Work Graph" }) {
            val scheduledNodes = build.scheduledWork
            writeRelevantProjectsFor(scheduledNodes, relevantProjectsRegistry)
            WorkNodeCodec(gradle, internalTypesCodec).run {
                writeWork(scheduledNodes)
            }
        }
    }

    private
    suspend fun DefaultReadContext.readBuild(createBuild: (String) -> ConfigurationCacheBuild) {
        val rootProjectName = readString()
        val build = createBuild(rootProjectName)

        readBuildTreeState(build.gradle)
        readGradleState(build.gradle)

        readRelevantProjects(build)

        build.registerProjects()

        initProjectProvider(build::getProject)

        val scheduledNodes = WorkNodeCodec(build.gradle, internalTypesCodec).run {
            readWork()
        }
        build.scheduleNodes(scheduledNodes)
    }

    private
    suspend fun DefaultWriteContext.writeGradleState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            // per build
            withDebugFrame({ "included builds" }) {
                writeChildBuilds(gradle)
            }
            withDebugFrame({ "build cache" }) {
                writeBuildCacheConfiguration(gradle)
            }
            withDebugFrame({ "cleanup registrations" }) {
                writeBuildOutputCleanupRegistrations(gradle)
            }
        }
    }

    private
    suspend fun DefaultWriteContext.writeBuildTreeState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            // per build tree
            withDebugFrame({ "listener subscriptions" }) {
                writeBuildEventListenerSubscriptions(gradle)
            }
            writeGradleEnterprisePluginManager(gradle)
        }
    }

    private
    suspend fun DefaultReadContext.readGradleState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {

            // per build tree
            readBuildEventListenerSubscriptions(gradle)
            readGradleEnterprisePluginManager(gradle)

            // per build
            readChildBuilds(gradle)
            readBuildCacheConfiguration(gradle)
            readBuildOutputCleanupRegistrations(gradle)
        }
    }

    private
    suspend fun DefaultReadContext.readBuildTreeState(gradle: GradleInternal) {
        withGradleIsolate(gradle, userTypesCodec) {
            // per build tree
            readBuildEventListenerSubscriptions(gradle)
            readGradleEnterprisePluginManager(gradle)
        }
    }

    private
    val internalTypesCodec
        get() = codecs.internalTypesCodec

    private
    val userTypesCodec
        get() = codecs.userTypesCodec

    private
    suspend fun DefaultWriteContext.writeChildBuilds(gradle: GradleInternal) {
        writeCollection(gradle.includedBuilds) {
            writeIncludedBuildState(it)
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
    suspend fun DefaultReadContext.readChildBuilds(gradle: GradleInternal) {
        val includedBuilds = readList {
            readIncludedBuildState(gradle)
        }
        gradle.includedBuilds = includedBuilds.map { it.model }

        if (readBoolean()) {
            logNotImplemented(
                feature = "source dependencies",
                documentationSection = NotYetImplementedSourceDependencies
            )
        }
    }

    private
    suspend fun DefaultReadContext.readIncludedBuildState(gradle: GradleInternal): IncludedBuildState {
        val includedBuildName = readString()
        val includedBuildRootDir = readFile()
        val fromBuild = readNonNull<PublicBuildPath>()

        val buildDefinition = BuildDefinition.fromStartParameterForBuild(
            gradle.startParameter,
            includedBuildName,
            includedBuildRootDir,
            PluginRequests.EMPTY,
            Actions.doNothing(),
            fromBuild
        )

        return gradle.services.get(BuildStateRegistry::class.java)
            .addIncludedBuild(buildDefinition)
    }

    private
    suspend fun DefaultWriteContext.writeIncludedBuildState(includedBuild: IncludedBuild) {
        val buildState = includedBuild as IncludedBuildState
        val buildDefinition = buildState.configuredBuild.services.get(BuildDefinition::class.java)
        buildDefinition.run {
            writeString(name!!)
            writeFile(buildRootDir)
            write(fromBuild)
        }
    }

    private
    suspend fun DefaultWriteContext.writeBuildCacheConfiguration(gradle: GradleInternal) {
        gradle.settings.buildCache.let { buildCache ->
            write(buildCache.local)
            write(buildCache.remote)
        }
    }

    private
    suspend fun DefaultReadContext.readBuildCacheConfiguration(gradle: GradleInternal) {
        gradle.settings.buildCache.let { buildCache ->
            buildCache.local = readNonNull()
            buildCache.remote = read() as BuildCache?
        }
    }

    private
    suspend fun DefaultWriteContext.writeBuildEventListenerSubscriptions(gradle: GradleInternal) {
        val eventListenerRegistry = gradle.serviceOf<BuildEventListenerRegistryInternal>()
        writeCollection(eventListenerRegistry.subscriptions)
    }

    private
    suspend fun DefaultReadContext.readBuildEventListenerSubscriptions(gradle: GradleInternal) {
        val eventListenerRegistry = gradle.serviceOf<BuildEventListenerRegistryInternal>()
        readCollection {
            val provider = readNonNull<Provider<OperationCompletionListener>>()
            eventListenerRegistry.subscribe(provider)
        }
    }

    private
    suspend fun DefaultWriteContext.writeBuildOutputCleanupRegistrations(gradle: GradleInternal) {
        val buildOutputCleanupRegistry = gradle.serviceOf<BuildOutputCleanupRegistry>()
        writeCollection(buildOutputCleanupRegistry.registeredOutputs)
    }

    private
    suspend fun DefaultReadContext.readBuildOutputCleanupRegistrations(gradle: GradleInternal) {
        val buildOutputCleanupRegistry = gradle.serviceOf<BuildOutputCleanupRegistry>()
        readCollection {
            val files = readNonNull<FileCollection>()
            buildOutputCleanupRegistry.registerOutputs(files)
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
            adapter.onLoadFromConfigurationCache()
            val manager = gradle.serviceOf<GradleEnterprisePluginManager>()
            manager.registerAdapter(adapter)
        }
    }

    private
    fun Encoder.writeRelevantProjectsFor(nodes: List<Node>, relevantProjectsRegistry: RelevantProjectsRegistry) {
        val relevantProjects = fillTheGapsOf(relevantProjectsRegistry.relevantProjects(nodes))
        writeCollection(relevantProjects) { project ->
            writeString(project.path)
            writeFile(project.projectDir)
            writeFile(project.buildDir)
        }
    }

    private
    fun Decoder.readRelevantProjects(build: ConfigurationCacheBuild) {
        readCollection {
            val projectPath = readString()
            val projectDir = readFile()
            val buildDir = readFile()
            build.createProject(projectPath, projectDir, buildDir)
        }
    }
}


internal
fun fillTheGapsOf(projects: Collection<Project>): List<Project> {
    val projectsWithoutGaps = ArrayList<Project>(projects.size)
    var index = 0
    projects.forEach { project ->
        var parent = project.parent
        var added = 0
        while (parent !== null && parent !in projectsWithoutGaps) {
            projectsWithoutGaps.add(index, parent)
            added += 1
            parent = parent.parent
        }
        if (project !in projectsWithoutGaps) {
            projectsWithoutGaps.add(project)
            added += 1
        }
        index += added
    }
    return projectsWithoutGaps
}
