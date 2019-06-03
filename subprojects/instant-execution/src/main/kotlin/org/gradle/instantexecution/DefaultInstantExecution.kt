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
import org.gradle.api.logging.Logging
import org.gradle.initialization.InstantExecution
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.beans.BeanPropertyReader
import org.gradle.instantexecution.serialization.codecs.Codecs
import org.gradle.instantexecution.serialization.codecs.TaskGraphCodec
import org.gradle.instantexecution.serialization.readClassPath
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.writeClassPath
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashUtil
import org.gradle.internal.operations.BuildOperationExecutor
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
            KryoBackedEncoder(stateFileOutputStream()).use { encoder ->
                writeContextFor(encoder).run {

                    val build = host.currentBuild
                    writeString(build.rootProject.name)
                    val scheduledTasks = build.scheduledTasks
                    writeRelevantProjectsFor(scheduledTasks)

                    val tasksClassPath = classPathFor(scheduledTasks)
                    writeClassPath(tasksClassPath)

                    TaskGraphCodec().run {
                        writeTaskGraphOf(build, scheduledTasks)
                    }
                }
            }
        }
    }

    override fun loadTaskGraph() {

        require(isInstantExecutionEnabled)

        buildOperationExecutor.withLoadOperation {
            KryoBackedDecoder(stateFileInputStream()).use { decoder ->
                readContextFor(decoder).run {

                    val rootProjectName = readString()
                    val build = host.createBuild(rootProjectName)
                    readRelevantProjects(build)

                    build.autoApplyPlugins()
                    build.registerProjects()

                    val tasksClassPath = readClassPath()
                    val taskClassLoader = classLoaderFor(tasksClassPath)
                    initialize(build::getProject, taskClassLoader)

                    val scheduledTasks = TaskGraphCodec().run {
                        readTaskGraph()
                    }
                    build.scheduleTasks(scheduledTasks)
                }
            }
        }
    }

    private
    fun writeContextFor(encoder: KryoBackedEncoder) = DefaultWriteContext(
        codecs(),
        encoder,
        logger
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
    fun classPathFor(tasks: List<Task>) = DefaultClassPath.of(
        linkedSetOf<File>().also { classPathFiles ->
            for (task in tasks) {
                ClasspathUtil.collectClasspathOf(
                    task.javaClass.classLoader,
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

    // Skip instant execution for buildSrc for now. Should instead collect up the inputs of its tasks and treat as task graph cache inputs
    private
    val isInstantExecutionEnabled: Boolean
        get() = host.getSystemProperty("org.gradle.unsafe.instant-execution") != null && !host.currentBuild.buildSrc


    private
    val instantExecutionStateFile by lazy {
        val currentGradleVersion = GradleVersion.current().version
        val cacheDir = File(host.rootDir, ".instant-execution-state/$currentGradleVersion").absoluteFile
        val baseName = HashUtil.createCompactMD5(host.requestedTaskNames.joinToString("/"))
        val cacheFileName = "$baseName.bin"
        File(cacheDir, cacheFileName)
    }
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
