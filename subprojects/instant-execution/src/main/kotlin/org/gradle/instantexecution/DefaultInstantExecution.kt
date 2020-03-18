/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.Project
import org.gradle.api.execution.internal.TaskInputsListeners
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory
import org.gradle.api.internal.provider.ValueSourceProviderFactory
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.execution.plan.Node
import org.gradle.initialization.GradlePropertiesController
import org.gradle.initialization.InstantExecution
import org.gradle.instantexecution.coroutines.runToCompletion
import org.gradle.instantexecution.extensions.unsafeLazy
import org.gradle.instantexecution.fingerprint.InstantExecutionCacheInputs
import org.gradle.instantexecution.fingerprint.InstantExecutionFingerprintChecker
import org.gradle.instantexecution.fingerprint.InvalidationReason
import org.gradle.instantexecution.fingerprint.ObtainedValue
import org.gradle.instantexecution.fingerprint.hashCodeForFile
import org.gradle.instantexecution.initialization.InstantExecutionStartParameter
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.MutableIsolateContext
import org.gradle.instantexecution.serialization.beans.BeanConstructors
import org.gradle.instantexecution.serialization.codecs.Codecs
import org.gradle.instantexecution.serialization.codecs.WorkNodeCodec
import org.gradle.instantexecution.serialization.logNotImplemented
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.readFile
import org.gradle.instantexecution.serialization.readNonNull
import org.gradle.instantexecution.serialization.runWriteOperation
import org.gradle.instantexecution.serialization.withIsolate
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.instantexecution.serialization.writeFile
import org.gradle.internal.Factory
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.fingerprint.FileCollectionSnapshotter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.internal.vfs.VirtualFileSystem
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.util.GFileUtils.relativePathOf
import org.gradle.util.GradleVersion
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.ArrayList


class DefaultInstantExecution internal constructor(
    private val host: Host,
    private val startParameter: InstantExecutionStartParameter,
    private val report: InstantExecutionReport,
    private val scopeRegistryListener: InstantExecutionClassLoaderScopeRegistryListener,
    private val beanConstructors: BeanConstructors,
    private val valueSourceProviderFactory: ValueSourceProviderFactory,
    private val virtualFileSystem: VirtualFileSystem,
    private val gradlePropertiesController: GradlePropertiesController,
    private val taskInputsListeners: TaskInputsListeners,
    private val fileCollectionSnapshotter: FileCollectionSnapshotter,
    private val fileCollectionFactory: FileCollectionFactory
) : InstantExecution {

    interface Host {

        val currentBuild: ClassicModeBuild

        fun createBuild(rootProjectName: String): InstantExecutionBuild

        fun <T> getService(serviceType: Class<T>): T

        fun <T> factory(serviceType: Class<T>): Factory<T>
    }

    override fun canExecuteInstantaneously(): Boolean = when {
        !isInstantExecutionEnabled -> {
            false
        }
        startParameter.recreateCache -> {
            log("Recreating instant execution cache")
            false
        }
        startParameter.isRefreshDependencies -> {
            log(
                "Calculating task graph as instant execution cache cannot be reused due to {}",
                "--refresh-dependencies"
            )
            false
        }
        !instantExecutionFingerprintFile.isFile -> {
            log(
                "Calculating task graph as no instant execution cache is available for tasks: {}",
                startParameter.requestedTaskNames.joinToString(" ")
            )
            false
        }
        else -> {
            val fingerprintChangedReason = checkFingerprint()
            when {
                fingerprintChangedReason != null -> {
                    log(
                        "Calculating task graph as instant execution cache cannot be reused because {}.",
                        fingerprintChangedReason
                    )
                    false
                }
                else -> {
                    log(
                        "Reusing instant execution cache. This is not guaranteed to work in any way."
                    )
                    true
                }
            }
        }
    }

    override fun prepareForBuildLogicExecution() {

        if (!isInstantExecutionEnabled) return

        attachBuildLogicInputsCollector()
    }

    override fun saveScheduledWork() {

        if (!isInstantExecutionEnabled) {
            // No need to hold onto the `ClassLoaderScope` tree
            // if we are not writing it.
            scopeRegistryListener.dispose()
            return
        }

        detachBuildLogicInputsCollector()

        buildOperationExecutor.withStoreOperation {

            // Discard the state file on serialization errors
            report.withExceptionHandling(::discardInstantExecutionState) {

                instantExecutionStateFile.createParentDirectories()

                service<ProjectStateRegistry>().withLenientState {
                    withWriteContextFor(instantExecutionStateFile, report) {
                        encodeScheduledWork()
                    }
                }
                withWriteContextFor(instantExecutionFingerprintFile, report) {
                    encodeFingerprint()
                }
            }
        }
    }

    override fun loadScheduledWork() {

        require(isInstantExecutionEnabled)

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        buildOperationExecutor.withLoadOperation {
            withReadContextFor(instantExecutionStateFile) {
                decodeScheduledWork()
            }
        }
    }

    private
    suspend fun DefaultWriteContext.encodeScheduledWork() {
        val build = host.currentBuild
        writeString(build.rootProject.name)

        writeGradleState(build.gradle)

        val scheduledNodes = build.scheduledWork
        writeRelevantProjectsFor(scheduledNodes)

        WorkNodeCodec(build.gradle, codecs.internalTypesCodec).run {
            writeWork(scheduledNodes)
        }
    }

    private
    suspend fun DefaultReadContext.decodeScheduledWork() {
        val rootProjectName = readString()
        val build = host.createBuild(rootProjectName)

        readGradleState(build.gradle)

        readRelevantProjects(build)

        build.registerProjects()

        initProjectProvider(build::getProject)

        val scheduledNodes = WorkNodeCodec(build.gradle, codecs.internalTypesCodec).run {
            readWork()
        }
        build.scheduleNodes(scheduledNodes)
    }

    private
    suspend fun DefaultWriteContext.encodeFingerprint() {
        withHostIsolate {
            InstantExecutionFingerprintChecker.FingerprintEncoder.run {
                encode(instantExecutionInputs!!.fingerprint)
            }
        }
    }

    private
    fun checkFingerprint(): InvalidationReason? {
        loadGradleProperties()
        return checkInstantExecutionFingerprintFile()
    }

    private
    fun loadGradleProperties() {
        gradlePropertiesController.loadGradlePropertiesFrom(
            startParameter.settingsDirectory
        )
    }

    private
    fun checkInstantExecutionFingerprintFile(): InvalidationReason? =
        withReadContextFor(instantExecutionFingerprintFile) {
            withHostIsolate {
                instantExecutionFingerprintChecker().run {
                    checkFingerprint()
                }
            }
        }

    private
    fun attachBuildLogicInputsCollector() {
        InstantExecutionCacheInputs(virtualFileSystem).also {
            instantExecutionInputs = it
            valueSourceProviderFactory.addListener(it)
            taskInputsListeners.addListener(it)
        }
    }

    private
    fun detachBuildLogicInputsCollector() {
        instantExecutionInputs.let {
            require(it != null)
            valueSourceProviderFactory.removeListener(it)
            taskInputsListeners.removeListener(it)
        }
    }

    private
    var instantExecutionInputs: InstantExecutionCacheInputs? = null

    private
    fun discardInstantExecutionState() {
        instantExecutionFingerprintFile.delete()
    }

    private
    fun withWriteContextFor(
        file: File,
        report: InstantExecutionReport,
        writeOperation: suspend DefaultWriteContext.() -> Unit
    ) {
        writerContextFor(file.outputStream(), report).useToRun {
            runWriteOperation(writeOperation)
        }
    }

    private
    fun writerContextFor(outputStream: OutputStream, report: InstantExecutionReport) =
        writeContextFor(
            KryoBackedEncoder(outputStream),
            report
        )

    private
    fun <R> withReadContextFor(file: File, readOperation: suspend DefaultReadContext.() -> R): R =
        KryoBackedDecoder(file.inputStream()).use { decoder ->
            readContextFor(decoder).run {
                initClassLoader(javaClass.classLoader)
                runToCompletion {
                    readOperation()
                }
            }
        }

    private
    fun writeContextFor(
        encoder: Encoder,
        report: InstantExecutionReport
    ) = DefaultWriteContext(
        codecs.userTypesCodec,
        encoder,
        scopeRegistryListener,
        logger,
        report::add
    )

    private
    fun readContextFor(decoder: KryoBackedDecoder) = DefaultReadContext(
        codecs.userTypesCodec,
        decoder,
        service(),
        beanConstructors,
        logger
    )

    private
    val codecs: Codecs by unsafeLazy {
        Codecs(
            directoryFileTreeFactory = service(),
            fileCollectionFactory = service(),
            fileLookup = service(),
            propertyFactory = service(),
            filePropertyFactory = service(),
            fileResolver = service(),
            instantiator = service(),
            listenerManager = service(),
            projectStateRegistry = service(),
            taskNodeFactory = service(),
            fingerprinterRegistry = service(),
            projectFinder = service(),
            buildOperationExecutor = service(),
            classLoaderHierarchyHasher = service(),
            isolatableFactory = service(),
            valueSnapshotter = service(),
            buildServiceRegistry = service(),
            managedFactoryRegistry = service(),
            parameterScheme = service(),
            actionScheme = service(),
            attributesFactory = service(),
            transformListener = service(),
            valueSourceProviderFactory = service(),
            patternSetFactory = factory(),
            fileSystem = service(),
            fileFactory = service()
        )
    }

    private
    suspend fun DefaultWriteContext.writeGradleState(gradle: Gradle) {
        withGradleIsolate(gradle) {
            if (gradle.includedBuilds.isNotEmpty()) {
                logNotImplemented("included builds")
            }
            val eventListenerRegistry = service<BuildEventListenerRegistryInternal>()
            writeCollection(eventListenerRegistry.subscriptions)
        }
    }

    private
    suspend fun DefaultReadContext.readGradleState(gradle: Gradle) {
        withGradleIsolate(gradle) {
            val eventListenerRegistry = service<BuildEventListenerRegistryInternal>()
            readCollection {
                val provider = readNonNull<Provider<OperationCompletionListener>>()
                eventListenerRegistry.subscribe(provider)
            }
        }
    }

    private
    inline fun <T : MutableIsolateContext, R> T.withGradleIsolate(gradle: Gradle, block: T.() -> R): R =
        withIsolate(IsolateOwner.OwnerGradle(gradle), codecs.userTypesCodec) {
            block()
        }

    private
    inline fun <T : MutableIsolateContext, R> T.withHostIsolate(block: T.() -> R): R =
        withIsolate(IsolateOwner.OwnerHost(host), codecs.userTypesCodec) {
            block()
        }

    private
    fun Encoder.writeRelevantProjectsFor(nodes: List<Node>) {
        writeCollection(fillTheGapsOf(relevantProjectPathsFor(nodes))) { project ->
            writeString(project.path)
            writeFile(project.projectDir)
        }
    }

    private
    fun Decoder.readRelevantProjects(build: InstantExecutionBuild) {
        readCollection {
            val projectPath = readString()
            val projectDir = readFile()
            build.createProject(projectPath, projectDir)
        }
    }

    private
    fun relevantProjectPathsFor(nodes: List<Node>): List<Project> =
        nodes.mapNotNullTo(mutableListOf()) { node ->
            node.owningProject
                ?.takeIf { it.parent != null }
        }

    private
    fun log(message: String, vararg args: Any?) {
        logger.log(instantExecutionLogLevel, message, *args)
    }

    private
    val buildOperationExecutor: BuildOperationExecutor
        get() = service()

    private
    inline fun <reified T> service() =
        host.service<T>()

    private
    inline fun <reified T> factory() =
        host.factory(T::class.java)

    private
    fun File.createParentDirectories() {
        Files.createDirectories(parentFile.toPath())
    }

    private
    val instantExecutionFingerprintFile by unsafeLazy {
        instantExecutionStateFile.run {
            resolveSibling("$name.fingerprint")
        }
    }

    private
    val instantExecutionStateFile by unsafeLazy {
        val cacheDir = absoluteFile(".instant-execution-state/${currentGradleVersion()}")
        val baseName = startParameter.instantExecutionCacheKey
        val cacheFileName = "$baseName.bin"
        File(cacheDir, cacheFileName)
    }

    private
    fun currentGradleVersion(): String =
        GradleVersion.current().version

    private
    fun absoluteFile(path: String) =
        File(rootDirectory, path).absoluteFile

    // Skip instant execution for buildSrc for now. Should instead collect up the inputs of its tasks and treat as task graph cache inputs
    private
    val isInstantExecutionEnabled: Boolean by unsafeLazy {
        startParameter.isEnabled && !host.currentBuild.buildSrc
    }

    private
    val instantExecutionLogLevel: LogLevel
        get() = when (startParameter.isQuiet) {
            true -> LogLevel.INFO
            else -> LogLevel.LIFECYCLE
        }

    private
    fun instantExecutionFingerprintChecker() =
        InstantExecutionFingerprintChecker(InstantExecutionFingerprintCheckerHost())

    private
    inner class InstantExecutionFingerprintCheckerHost : InstantExecutionFingerprintChecker.Host {

        override fun hashCodeForFile(file: File) =
            virtualFileSystem.hashCodeForFile(file)

        override fun hashCodeForDirectory(directory: File): HashCode? {
            val hashes = mutableListOf<HashCode>()
            fileCollectionSnapshotter
                .snapshot(fileCollectionFactory.fixed(directory))
                .singleOrNull()
                ?.accept(
                    object : FileSystemSnapshotVisitor {
                        override fun preVisitDirectory(directorySnapshot: CompleteDirectorySnapshot): Boolean {
                            hashes.add(directorySnapshot.hash)
                            return false
                        }

                        override fun visitFile(fileSnapshot: CompleteFileSystemLocationSnapshot) {
                            // Directory became a file?
                            // Let hashes disagree
                            hashes.add(fileSnapshot.hash)
                        }

                        override fun postVisitDirectory(directorySnapshot: CompleteDirectorySnapshot) = Unit
                    }
                )
            return hashes.singleOrNull()
        }

        override fun displayNameOf(fileOrDirectory: File): String =
            relativePathOf(fileOrDirectory, rootDirectory)

        override fun instantiateValueSourceOf(obtainedValue: ObtainedValue) =
            (valueSourceProviderFactory as DefaultValueSourceProviderFactory).instantiateValueSource(
                obtainedValue.valueSourceType,
                obtainedValue.valueSourceParametersType,
                obtainedValue.valueSourceParameters
            )
    }

    private
    val rootDirectory
        get() = startParameter.rootDirectory
}


inline fun <reified T> DefaultInstantExecution.Host.service(): T =
    getService(T::class.java)


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
        projectsWithoutGaps.add(project)
        added += 1
        index += added
    }
    return projectsWithoutGaps
}


private
val logger = Logging.getLogger(DefaultInstantExecution::class.java)
