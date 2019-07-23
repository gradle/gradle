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

import org.gradle.api.Task
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCacheInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logging
import org.gradle.initialization.InstantExecution
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.IsolateContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.MutableIsolateContext
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.beans.BeanPropertyReader
import org.gradle.instantexecution.serialization.codecs.BuildOperationListenersCodec
import org.gradle.instantexecution.serialization.codecs.Codecs
import org.gradle.instantexecution.serialization.codecs.TaskGraphCodec
import org.gradle.instantexecution.serialization.readClassPath
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.withIsolate
import org.gradle.instantexecution.serialization.withPropertyTrace
import org.gradle.instantexecution.serialization.writeClassPath
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.classloader.ClasspathUtil
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

import java.util.ArrayList
import java.util.SortedSet

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine


class DefaultInstantExecution(
    private val host: Host
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

    override fun saveTaskGraph() {

        if (!isInstantExecutionEnabled) {
            return
        }

        buildOperationExecutor.withStoreOperation {

            val report = instantExecutionReport()
            val instantExecutionException = report.withExceptionHandling {
                KryoBackedEncoder(stateFileOutputStream()).use { encoder ->
                    writeContextFor(encoder, report).run {
                        runToCompletion {
                            encodeTaskGraph()
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

    override fun loadTaskGraph() {

        require(isInstantExecutionEnabled)

        buildOperationExecutor.withLoadOperation {
            KryoBackedDecoder(stateFileInputStream()).use { decoder ->
                readContextFor(decoder).run {
                    runToCompletion {
                        decodeTaskGraph()
                    }
                }
            }
        }
    }

    private
    suspend fun DefaultWriteContext.encodeTaskGraph() {
        val build = host.currentBuild
        writeString(build.rootProject.name)

        writeClassPath(collectClassPath())

        writeGradleState(build.rootProject.gradle)

        val scheduledTasks = build.scheduledTasks
        writeRelevantProjectsFor(scheduledTasks)

        TaskGraphCodec(service()).run {
            writeTaskGraphOf(build, scheduledTasks)
        }
    }

    private
    suspend fun DefaultReadContext.decodeTaskGraph() {
        val rootProjectName = readString()
        val build = host.createBuild(rootProjectName)

        val classPath = readClassPath()
        val classLoader = classLoaderFor(classPath)
        initClassLoader(classLoader)

        readGradleState(build.gradle)

        readRelevantProjects(build)

        build.autoApplyPlugins()
        build.registerProjects()

        initProjectProvider(build::getProject)

        val scheduledTasks = TaskGraphCodec(service()).run {
            readTaskGraph()
        }
        build.scheduleTasks(scheduledTasks)
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
        encoder: KryoBackedEncoder,
        report: InstantExecutionReport
    ) = DefaultWriteContext(
        codecs(),
        encoder,
        logger,
        report::add
    )

    private
    fun readContextFor(decoder: KryoBackedDecoder) = DefaultReadContext(
        codecs(),
        decoder,
        logger,
        BeanPropertyReader.factoryFor(service())
    )

    private
    fun codecs() = Codecs(
        directoryFileTreeFactory = service(),
        fileCollectionFactory = service(),
        fileResolver = service(),
        instantiator = service(),
        listenerManager = service()
    )

    private
    suspend fun DefaultWriteContext.writeGradleState(gradle: Gradle) {
        withGradle(gradle) {
            BuildOperationListenersCodec().run {
                writeBuildOperationListeners(service())
            }
        }
    }

    private
    suspend fun DefaultReadContext.readGradleState(gradle: Gradle) {
        withGradle(gradle) {
            val listeners = BuildOperationListenersCodec().run {
                readBuildOperationListeners()
            }
            service<BuildOperationListenerManager>().let { manager ->
                listeners.forEach { manager.addListener(it) }
            }
        }
    }

    private
    fun Encoder.writeRelevantProjectsFor(tasks: List<Task>) {
        writeCollection(fillTheGapsOf(relevantProjectPathsFor(tasks))) { projectPath ->
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
    fun relevantProjectPathsFor(tasks: List<Task>) =
        tasks.mapNotNull { task ->
            task.project.takeIf { it.parent != null }?.path?.let(Path::path)
        }.toSortedSet()

    private
    val buildOperationExecutor: BuildOperationExecutor
        get() = service()

    private
    inline fun <reified T> service() =
        host.service<T>()

    private
    fun classLoaderFor(classPath: ClassPath) =
        host.classLoaderFor(classPath)

    private
    fun collectClassPath() = DefaultClassPath.of(
        linkedSetOf<File>().also { classPathFiles ->
            (service<ClassLoaderCache>() as ClassLoaderCacheInternal).visitClassLoadersUsedInThisBuild { loader ->
                ClasspathUtil.collectClasspathOf(
                    loader,
                    classPathFiles
                )
            }
        }
    )

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
        val baseName = HashUtil.createCompactMD5(host.requestedTaskNames.joinToString("/"))
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


private
inline fun <T> T.withGradle(
    gradle: Gradle,
    action: () -> Unit
) where T : IsolateContext, T : MutableIsolateContext {
    withIsolate(IsolateOwner.OwnerGradle(gradle)) {
        withPropertyTrace(PropertyTrace.Gradle) {
            action()
        }
    }
}


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
