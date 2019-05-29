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

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.logging.Logging
import org.gradle.initialization.InstantExecution
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.MutableReadContext
import org.gradle.instantexecution.serialization.MutableWriteContext
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.codecs.Codecs
import org.gradle.instantexecution.serialization.beans.BeanFieldDeserializer
import org.gradle.instantexecution.serialization.beans.BeanFieldSerializer
import org.gradle.instantexecution.serialization.readClass
import org.gradle.instantexecution.serialization.readClassPath
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.readCollectionInto
import org.gradle.instantexecution.serialization.readStrings
import org.gradle.instantexecution.serialization.withIsolate
import org.gradle.instantexecution.serialization.writeClass
import org.gradle.instantexecution.serialization.writeClassPath
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.instantexecution.serialization.writeStrings
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
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

        val isSkipLoadingState: Boolean

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
        host.isSkipLoadingState -> {
            logger.lifecycle("Calculating task graph as skipping instant execution cache was requested")
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
                DefaultWriteContext(codecs, encoder, logger).run {

                    val build = host.currentBuild
                    writeString(build.rootProject.name)
                    val scheduledTasks = build.scheduledTasks
                    writeRelevantProjectsFor(scheduledTasks)

                    val tasksClassPath = classPathFor(scheduledTasks)
                    writeClassPath(tasksClassPath)

                    writeTaskGraphOf(build, scheduledTasks)
                }
            }
        }
    }

    override fun loadTaskGraph() {

        require(isInstantExecutionEnabled)

        buildOperationExecutor.withLoadOperation {
            KryoBackedDecoder(stateFileInputStream()).use { decoder ->
                DefaultReadContext(codecs, decoder, logger).run {

                    val rootProjectName = readString()
                    val build = host.createBuild(rootProjectName)
                    readRelevantProjects(build)

                    build.autoApplyPlugins()
                    build.registerProjects()

                    val tasksClassPath = readClassPath()
                    val taskClassLoader = classLoaderFor(tasksClassPath)
                    initialize(build::getProject, taskClassLoader)

                    val scheduledTasks = readTaskGraph()
                    build.scheduleTasks(scheduledTasks)
                }
            }
        }
    }

    private
    val codecs by lazy {
        Codecs(
            directoryFileTreeFactory = service(),
            fileCollectionFactory = service(),
            fileResolver = service(),
            instantiator = service(),
            objectFactory = service(),
            patternSpecFactory = service(),
            filePropertyFactory = service()
        )
    }

    private
    fun MutableWriteContext.writeTaskGraphOf(build: ClassicModeBuild, tasks: List<Task>) {
        writeCollection(tasks) { task ->
            try {
                writeTask(task, build.dependenciesOf(task))
            } catch (e: Throwable) {
                throw GradleException("Could not save state of $task.", e)
            }
        }
    }

    private
    fun MutableReadContext.readTaskGraph(): List<Task> {
        val tasksWithDependencies = readTasksWithDependencies()
        wireTaskDependencies(tasksWithDependencies)
        return tasksWithDependencies.map { (task, _) -> task }
    }

    private
    fun MutableReadContext.readTasksWithDependencies(): List<Pair<Task, List<String>>> =
        readCollectionInto({ size -> ArrayList(size) }) {
            readTask()
        }

    private
    fun wireTaskDependencies(tasksWithDependencies: List<Pair<Task, List<String>>>) {
        val tasksByPath = tasksWithDependencies.associate { (task, _) ->
            task.path to task
        }
        tasksWithDependencies.forEach { (task, dependencies) ->
            task.dependsOn(dependencies.map(tasksByPath::getValue))
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
    val filePropertyFactory: FilePropertyFactory
        get() = service()

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
    fun classPathFor(tasks: List<Task>) =
        tasks.map(::taskClassPath).fold(ClassPath.EMPTY, ClassPath::plus)

    private
    fun taskClassPath(task: Task) =
        task.javaClass.classLoader.let(ClasspathUtil::getClasspath)

    private
    fun MutableWriteContext.writeTask(task: Task, dependencies: Set<Task>) {
        val taskType = GeneratedSubclasses.unpack(task.javaClass)
        writeString(task.project.path)
        writeString(task.name)
        writeClass(taskType)
        writeStrings(dependencies.map { it.path })

        withIsolate(task) {
            BeanFieldSerializer(taskType).run {
                serialize(task)
            }
        }
    }

    private
    fun MutableReadContext.readTask(): Pair<Task, List<String>> {
        val projectPath = readString()
        val taskName = readString()
        val taskType = readClass().asSubclass(Task::class.java)
        val taskDependencies = readStrings()

        val task = createTask(projectPath, taskName, taskType)

        withIsolate(task) {
            BeanFieldDeserializer(taskType, filePropertyFactory).run {
                deserialize(task)
            }
        }

        return task to taskDependencies
    }

    private
    fun ReadContext.createTask(projectPath: String, taskName: String, taskClass: Class<out Task>) =
        getProject(projectPath).tasks.createWithoutConstructor(taskName, taskClass)

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
    val isInstantExecutionEnabled: Boolean
        get() = host.getSystemProperty("org.gradle.unsafe.instant-execution") != null

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
