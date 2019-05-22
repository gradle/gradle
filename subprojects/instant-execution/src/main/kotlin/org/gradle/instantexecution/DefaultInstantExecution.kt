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

import groovy.lang.GroovyObject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.logging.Logging
import org.gradle.initialization.InstantExecution
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashUtil
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.util.GradleVersion
import org.gradle.util.Path

import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.util.SortedSet


inline fun <reified T> DefaultInstantExecution.Host.service(): T = getService(T::class.java)


class DefaultInstantExecution(
    private val host: Host
) : InstantExecution {

    interface Host {

        val isSkipLoadingState: Boolean

        val currentBuild: ClassicModeBuild

        fun createBuild(rootProjectName: String): InstantExecutionBuild

        fun newStateSerializer(): StateSerializer

        fun deserializerFor(beanClassLoader: ClassLoader): StateDeserializer

        fun <T> getService(serviceType: Class<T>): T

        fun getSystemProperty(propertyName: String): String?

        val rootDir: File

        val requestedTaskNames: List<String>

        fun classLoaderFor(classPath: ClassPath): ClassLoader
    }

    private
    val stateSerializer by lazy(LazyThreadSafetyMode.NONE) {
        host.newStateSerializer()
    }

    private
    val buildOperationExecutor: BuildOperationExecutor =
        host.service()

    override fun canExecuteInstantaneously(): Boolean = when {
        !isInstantExecutionEnabled -> false
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
        if (isInstantExecutionEnabled) {
            buildOperationExecutor.withStoreOperation {
                saveTasks(instantExecutionStateFile)
            }
        }
    }

    override fun loadTaskGraph() {
        buildOperationExecutor.withLoadOperation {
            loadTasks(instantExecutionStateFile)
        }
    }

    private
    fun saveTasks(stateFile: File) {
        val build = host.currentBuild
        Files.createDirectories(stateFile.parentFile.toPath())
        KryoBackedEncoder(stateFile.outputStream()).use { encoder ->
            encoder.writeString(build.rootProject.name)
            val scheduledTasks = build.scheduledTasks
            saveRelevantProjectsFor(scheduledTasks, encoder)
            val relevantClassPath = classPathFor(scheduledTasks)
            encoder.serializeClassPath(relevantClassPath)
            encoder.serializeCollection(scheduledTasks) { task ->
                try {
                    encoder.saveStateOf(task, build)
                } catch (e: Throwable) {
                    throw GradleException("Could not save state of $task.", e)
                }
            }
        }
    }

    private
    fun saveRelevantProjectsFor(tasks: List<Task>, encoder: Encoder) {
        encoder.serializeCollection(fillTheGapsOf(relevantProjectsFor(tasks))) {
            encoder.writeString(it.path)
        }
    }

    private
    fun relevantProjectsFor(tasks: List<Task>) =
        tasks.mapNotNull { task ->
            task.project.takeIf { it.parent != null }?.path?.let(Path::path)
        }.toSortedSet()

    private
    fun loadTasks(stateFile: File) {
        KryoBackedDecoder(stateFile.inputStream()).use { decoder ->
            val rootProjectName = decoder.readString()
            val build = host.createBuild(rootProjectName)
            loadRelevantProjects(decoder, build)
            build.autoApplyPlugins()
            build.registerProjects()
            build.scheduleTasks(loadTasksFor(decoder, build))
        }
    }

    private
    fun loadRelevantProjects(decoder: Decoder, build: InstantExecutionBuild) {
        decoder.deserializeCollection {
            build.createProject(decoder.readString())
        }
    }

    private
    fun loadTasksFor(decoder: Decoder, build: InstantExecutionBuild): List<Task> {

        val tasksWithDependencies = loadTasksWithDependenciesFor(decoder, build)

        val tasksByPath = tasksWithDependencies.associate { (task, _) ->
            task.path to task
        }

        val tasks = ArrayList<Task>(tasksWithDependencies.size)
        tasksWithDependencies.forEach { (task, dependencies) ->
            task.dependsOn(dependencies.map(tasksByPath::getValue))
            tasks.add(task)
        }
        return tasks
    }

    private
    fun loadTasksWithDependenciesFor(decoder: Decoder, build: InstantExecutionBuild): List<Pair<Task, List<String>>> {
        val classPath = decoder.deserializeClassPath()
        val taskClassLoader = classLoaderFor(classPath)
        return decoder.deserializeCollectionInto({ size -> ArrayList(size) }) { container ->
            val task = loadTaskFor(build, decoder, taskClassLoader)
            container.add(task)
        }
    }

    private
    val filePropertyFactory = host.service<FilePropertyFactory>()

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
    fun Encoder.saveStateOf(task: Task, build: ClassicModeBuild) {
        val taskType = GeneratedSubclasses.unpack(task.javaClass)
        writeString(task.project.path)
        writeString(task.name)
        writeString(taskType.name)
        serializeCollection(build.dependenciesOf(task)) {
            writeString(it.path)
        }

        BeanFieldSerializer(task, taskType, stateSerializer).invoke(
            this,
            SerializationContext(task, logger)
        )
    }

    private
    fun loadTaskFor(build: InstantExecutionBuild, decoder: Decoder, taskClassLoader: ClassLoader): Pair<Task, List<String>> {
        val projectPath = decoder.readString()
        val taskName = decoder.readString()
        val typeName = decoder.readString()
        val taskDependencies = decoder.deserializeStrings()
        val taskClass = taskClassLoader.loadClass(typeName).asSubclass(Task::class.java)
        val task = build.createTask(projectPath, taskName, taskClass)
        val deserializer = host.deserializerFor(taskClassLoader)
        BeanFieldDeserializer(task, taskClass, deserializer, filePropertyFactory).deserialize(
            decoder,
            DeserializationContext(task, logger)
        )
        return task to taskDependencies
    }

    private
    fun InstantExecutionBuild.createTask(projectPath: String, taskName: String, taskClass: Class<out Task>) =
        getProject(projectPath).tasks.createWithoutConstructor(taskName, taskClass)

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


internal
fun relevantStateOf(taskType: Class<*>): Sequence<Field> =
    relevantTypeHierarchyOf(taskType).flatMap { type ->
        type.declaredFields.asSequence().filterNot { field ->
            Modifier.isStatic(field.modifiers) || Modifier.isTransient(field.modifiers)
        }
    }


private
fun relevantTypeHierarchyOf(taskType: Class<*>): Sequence<Class<*>> = sequence {
    var current = taskType
    while (isRelevantDeclaringClass(current)) {
        yield(current)
        current = current.superclass
    }
}


private
fun isRelevantDeclaringClass(declaringClass: Class<*>): Boolean =
    declaringClass !in irrelevantDeclaringClasses


private
val irrelevantDeclaringClasses = setOf(
    Object::class.java,
    GroovyObject::class.java,
    Task::class.java,
    TaskInternal::class.java,
    DefaultTask::class.java,
    AbstractTask::class.java,
    ConventionTask::class.java
)


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
fun Encoder.serializeClassPath(classPath: ClassPath) {
    serializeCollection(classPath.asFiles) {
        writeFile(it)
    }
}


private
fun Decoder.deserializeClassPath(): ClassPath =
    DefaultClassPath.of(
        deserializeCollectionInto({ size -> LinkedHashSet<File>(size) }) { container ->
            container.add(readFile())
        }
    )


private
fun Encoder.writeFile(file: File?) {
    BaseSerializerFactory.FILE_SERIALIZER.write(this, file)
}


private
fun Decoder.readFile(): File =
    BaseSerializerFactory.FILE_SERIALIZER.read(this)


private
fun Decoder.deserializeStrings(): List<String> =
    deserializeCollectionInto({ size -> ArrayList(size) }) { container ->
        container.add(readString())
    }


private
fun <T> Encoder.serializeCollection(elements: Collection<T>, serializeElement: (T) -> Unit) {
    writeSmallInt(elements.size)
    for (element in elements) {
        serializeElement(element)
    }
}


private
fun Decoder.deserializeCollection(deserializeElement: () -> Unit) {
    val size = readSmallInt()
    for (i in 0 until size) {
        deserializeElement()
    }
}


private
inline fun <T> Decoder.deserializeCollectionInto(containerSupplier: (Int) -> T, deserializeElement: (T) -> Unit): T {
    val size = readSmallInt()
    val container = containerSupplier(size)
    for (i in 0 until size) {
        deserializeElement(container)
    }
    return container
}


private
val logger = Logging.getLogger(DefaultInstantExecution::class.java)
