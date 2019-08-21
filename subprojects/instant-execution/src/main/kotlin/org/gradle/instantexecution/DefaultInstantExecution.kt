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

import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import org.gradle.execution.plan.Node
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.InstantExecution
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.codecs.BuildOperationListenersCodec
import org.gradle.instantexecution.serialization.codecs.Codecs
import org.gradle.instantexecution.serialization.codecs.WorkNodeCodec
import org.gradle.instantexecution.serialization.readClassPath
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.readList
import org.gradle.instantexecution.serialization.withIsolate
import org.gradle.instantexecution.serialization.writeClassPath
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashUtil
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.util.GradleVersion
import org.gradle.util.Path
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.SortedSet
import java.util.TreeSet
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine


class DefaultInstantExecution internal constructor(
    private val host: Host,
    private val scopeRegistryListener: InstantExecutionClassLoaderScopeRegistryListener
) : InstantExecution {

    interface Host {

        val skipLoadingStateReason: String?

        val currentBuild: ClassicModeBuild

        fun createBuild(rootProjectName: String): InstantExecutionBuild

        fun <T> getService(serviceType: Class<T>): T

        fun getSystemProperty(propertyName: String): String?

        val rootDir: File

        val requestedTaskNames: List<String>

        fun classLoaderFor(classPath: ClassPath): ClassLoader
    }

    override fun canExecuteInstantaneously(): Boolean = when {
        !isInstantExecutionEnabled -> {
            false
        }
        host.skipLoadingStateReason != null -> {
            logger.lifecycle("Calculating task graph as instant execution cache cannot be reused due to ${host.skipLoadingStateReason}")
            false
        }
        !instantExecutionStateFile.isFile -> {
            logger.lifecycle("Calculating task graph as no instant execution cache is available for tasks: ${host.requestedTaskNames.joinToString(" ")}")
            false
        }
        else -> {
            logger.lifecycle("Reusing instant execution cache. This is not guaranteed to work in any way.")
            true
        }
    }

    override fun saveScheduledWork() {

        if (!isInstantExecutionEnabled) {
            // No need to hold onto the `ClassLoaderScope` tree
            // if we are not writing it.
            scopeRegistryListener.dispose()
            return
        }

        buildOperationExecutor.withStoreOperation {

            val report = instantExecutionReport()
            val instantExecutionException = report.withExceptionHandling {
                KryoBackedEncoder(stateFileOutputStream()).use { encoder ->
                    writeContextFor(encoder, report).run {
                        runToCompletion {
                            encodeScheduledWork()
                        }
                    }
                }
            }

            // Discard the state file on errors
            if (instantExecutionException != null) {
                discardInstantExecutionState()
                throw instantExecutionException
            }
        }
    }

    override fun loadScheduledWork() {

        require(isInstantExecutionEnabled)

        // No need to record the `ClassLoaderScope` tree
        // when loading the task graph.
        scopeRegistryListener.dispose()

        buildOperationExecutor.withLoadOperation {
            KryoBackedDecoder(stateFileInputStream()).use { decoder ->
                readContextFor(decoder).run {
                    runToCompletion {
                        decodeScheduledWork()
                    }
                }
            }
        }
    }

    private
    suspend fun DefaultWriteContext.encodeScheduledWork() {
        val build = host.currentBuild
        writeString(build.rootProject.name)

        writeClassLoaderScopeSpecs(collectClassLoaderScopeSpecs())

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

        val loader = classLoaderFor(readClassLoaderScopeSpecs())
        initClassLoader(loader)

        readGradleState(build.gradle)

        readRelevantProjects(build)

        build.autoApplyPlugins()
        build.registerProjects()

        initProjectProvider(build::getProject)

        val scheduledNodes = WorkNodeCodec(build.gradle, codecs.internalTypesCodec).run {
            readWork()
        }
        build.scheduleNodes(scheduledNodes)
    }

    private
    fun instantExecutionReport() = InstantExecutionReport(
        reportOutputDir,
        logger,
        maxProblems()
    )

    private
    fun discardInstantExecutionState() {
        instantExecutionStateFile.delete()
    }

    private
    fun writeContextFor(
        encoder: Encoder,
        report: InstantExecutionReport
    ) = DefaultWriteContext(
        codecs.userTypesCodec,
        encoder,
        logger,
        report::add
    )

    private
    fun readContextFor(decoder: KryoBackedDecoder) = DefaultReadContext(
        codecs.userTypesCodec,
        decoder,
        logger
    )

    private
    val codecs: Codecs by lazy {
        Codecs(
            directoryFileTreeFactory = service(),
            fileCollectionFactory = service(),
            filePropertyFactory = service(),
            fileResolver = service(),
            instantiator = service(),
            listenerManager = service(),
            projectStateRegistry = service(),
            taskNodeFactory = service(),
            fingerprinterRegistry = service(),
            projectFinder = service(),
            buildOperationExecutor = service(),
            isolatableFactory = service(),
            valueSnapshotter = service(),
            fileCollectionFingerprinterRegistry = service(),
            isolatableSerializerRegistry = service(),
            actionScheme = service(),
            parameterScheme = service(),
            classLoaderHierarchyHasher = service(),
            transformListener = service()
        )
    }

    private
    suspend fun DefaultWriteContext.writeGradleState(gradle: Gradle) {
        withIsolate(IsolateOwner.OwnerGradle(gradle), codecs.userTypesCodec) {
            BuildOperationListenersCodec().run {
                writeBuildOperationListeners(service())
            }
        }
    }

    private
    suspend fun DefaultReadContext.readGradleState(gradle: Gradle) {
        withIsolate(IsolateOwner.OwnerGradle(gradle), codecs.userTypesCodec) {
            val listeners = BuildOperationListenersCodec().run {
                readBuildOperationListeners()
            }
            service<BuildOperationListenerManager>().let { manager ->
                listeners.forEach { manager.addListener(it) }
            }
        }
    }

    private
    fun DefaultWriteContext.writeClassLoaderScopeSpecs(classLoaderScopeSpecs: List<ClassLoaderScopeSpec>) {
        writeCollection(classLoaderScopeSpecs) { spec ->
            writeString(spec.name)
            writeClassPath(spec.localClassPath.toClassPath())
            writeClassPath(spec.exportClassPath.toClassPath())
            writeClassLoaderScopeSpecs(spec.children)
        }
    }

    private
    fun DefaultReadContext.readClassLoaderScopeSpecs(): List<ClassLoaderScopeSpec> =
        readList {
            ClassLoaderScopeSpec(readString()).apply {
                localClassPath.add(readClassPath())
                exportClassPath.add(readClassPath())
                children.addAll(readClassLoaderScopeSpecs())
            }
        }

    private
    fun Encoder.writeRelevantProjectsFor(nodes: List<Node>) {
        writeCollection(fillTheGapsOf(relevantProjectPathsFor(nodes))) { projectPath ->
            writeString(projectPath.path)
        }
    }

    private
    fun Decoder.readRelevantProjects(build: InstantExecutionBuild) {
        readCollection {
            val projectPath = readString()
            build.createProject(projectPath)
        }
    }

    private
    fun relevantProjectPathsFor(nodes: List<Node>): SortedSet<Path> =
        nodes.mapNotNullTo(TreeSet()) { node ->
            node.owningProject
                ?.takeIf { it.parent != null }
                ?.path
                ?.let(Path::path)
        }

    private
    val buildOperationExecutor: BuildOperationExecutor
        get() = service()

    private
    inline fun <reified T> service() =
        host.service<T>()

    private
    fun classLoaderFor(classLoaderScopeSpecs: List<ClassLoaderScopeSpec>): ClassLoader =
        CachingClassLoader(
            MultiParentClassLoader(
                classLoadersFrom(classLoaderScopeSpecs)
            )
        )

    private
    fun classLoadersFrom(specs: List<ClassLoaderScopeSpec>) = mutableListOf<ClassLoader>().apply {

        val coreAndPluginsScope = service<ClassLoaderScopeRegistry>().coreAndPluginsScope

        val stack = specs.mapTo(ArrayDeque()) { spec -> spec to coreAndPluginsScope }
        while (stack.isNotEmpty()) {

            val (spec, parent) = stack.pop()
            val scope = parent
                .createChild(spec.name)
                .local(spec.localClassPath.toClassPath())
                .export(spec.exportClassPath.toClassPath())
                .lock()

            add(scope.localClassLoader)
            add(scope.exportClassLoader)

            stack.addAll(
                spec.children.map { child -> child to scope }
            )
        }
    }

    private
    fun collectClassLoaderScopeSpecs(): List<ClassLoaderScopeSpec> =
        scopeRegistryListener.coreAndPluginsSpec!!.children

    private
    fun stateFileOutputStream(): FileOutputStream = instantExecutionStateFile.run {
        createParentDirectories()
        outputStream()
    }

    private
    fun stateFileInputStream() = instantExecutionStateFile.inputStream()

    private
    fun File.createParentDirectories() {
        Files.createDirectories(parentFile.toPath())
    }

    private
    val instantExecutionStateFile by lazy {
        val currentGradleVersion = GradleVersion.current().version
        val cacheDir = File(host.rootDir, ".instant-execution-state/$currentGradleVersion").absoluteFile
        val baseName = compactMD5For(host.requestedTaskNames)
        val cacheFileName = "$baseName.bin"
        File(cacheDir, cacheFileName)
    }

    private
    val reportOutputDir by lazy {
        instantExecutionStateFile.run {
            resolveSibling(nameWithoutExtension)
        }
    }

    // Skip instant execution for buildSrc for now. Should instead collect up the inputs of its tasks and treat as task graph cache inputs
    private
    val isInstantExecutionEnabled: Boolean
        get() = systemProperty(SystemProperties.isEnabled) != null && !host.currentBuild.buildSrc

    private
    fun maxProblems(): Int =
        systemProperty(SystemProperties.maxProblems)
            ?.let(Integer::valueOf)
            ?: 512

    private
    fun systemProperty(propertyName: String) =
        host.getSystemProperty(propertyName)

    private
    fun compactMD5For(taskNames: List<String>) =
        HashUtil.createCompactMD5(taskNames.joinToString("/"))
}


inline fun <reified T> DefaultInstantExecution.Host.service(): T =
    getService(T::class.java)


internal
fun fillTheGapsOf(paths: SortedSet<Path>): List<Path> {
    val pathsWithoutGaps = ArrayList<Path>(paths.size)
    var index = 0
    paths.forEach { path ->
        var parent = path.parent
        var added = 0
        while (parent !== null && parent !in pathsWithoutGaps) {
            pathsWithoutGaps.add(index, parent)
            added += 1
            parent = parent.parent
        }
        pathsWithoutGaps.add(path)
        added += 1
        index += added
    }
    return pathsWithoutGaps
}


private
val logger = Logging.getLogger(DefaultInstantExecution::class.java)


/**
 * [Starts][startCoroutine] the suspending [block], asserts it runs
 * to completion and returns its result.
 */
internal
fun <R> runToCompletion(block: suspend () -> R): R {
    var completion: Result<R>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) {
        completion = it
    })
    return completion.let {
        require(it != null) {
            "Coroutine didn't run to completion."
        }
        it.getOrThrow()
    }
}


private
fun List<ClassPath>.toClassPath() = when (size) {
    0 -> ClassPath.EMPTY
    1 -> this[0]
    else -> DefaultClassPath.of(
        mutableSetOf<File>().also { files ->
            forEach { classPath ->
                files.addAll(classPath.asFiles)
            }
        }
    )
}
