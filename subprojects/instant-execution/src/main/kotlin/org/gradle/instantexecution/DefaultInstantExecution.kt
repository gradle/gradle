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
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.util.GradleVersion
import org.gradle.util.Path

import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.util.SortedSet


inline fun <reified T> DefaultInstantExecution.Host.service(): T =
    getService(T::class.java)


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

        KryoBackedEncoder(stateFileOutputStream()).use { encoder ->
            DefaultWriteContext(encoder).run {

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

    override fun loadTaskGraph() {

        require(isInstantExecutionEnabled)

        KryoBackedDecoder(stateFileInputStream()).use { decoder ->
            DefaultReadContext(decoder).run {

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

    private
    fun WriteContext.writeTaskGraphOf(build: ClassicModeBuild, tasks: List<Task>) {
        writeCollection(tasks) { task ->
            try {
                writeTask(task, build.dependenciesOf(task))
            } catch (e: Throwable) {
                throw GradleException("Could not save state of $task.", e)
            }
        }
    }

    private
    fun ReadContext.readTaskGraph(): List<Task> {
        val tasksWithDependencies = readTasksWithDependencies()
        wireTaskDependencies(tasksWithDependencies)
        return tasksWithDependencies.map { (task, _) -> task }
    }

    private
    fun ReadContext.readTasksWithDependencies(): List<Pair<Task, List<String>>> =
        readCollectionInto({ size -> ArrayList(size) }) { container ->
            val task = readTask()
            container.add(task)
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
    fun WriteContext.writeTask(task: Task, dependencies: Set<Task>) {
        val taskType = GeneratedSubclasses.unpack(task.javaClass)
        writeString(task.project.path)
        writeString(task.name)
        writeString(taskType.name)
        writeStrings(dependencies.map { it.path })

        BeanFieldSerializer(task, taskType, stateSerializer).invoke(
            this,
            SerializationContext(task, logger)
        )
    }

    private
    fun ReadContext.readTask(): Pair<Task, List<String>> {
        val projectPath = readString()
        val taskName = readString()
        val typeName = readString()
        val taskDependencies = readStrings()

        val taskType = taskClassLoader.loadClass(typeName).asSubclass(Task::class.java)
        val task = createTask(projectPath, taskName, taskType)
        val deserializer = host.deserializerFor(taskClassLoader)
        BeanFieldDeserializer(task, taskType, deserializer, filePropertyFactory).deserialize(
            this,
            DeserializationContext(task, logger)
        )

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
fun Encoder.writeClassPath(classPath: ClassPath) {
    writeCollection(classPath.asFiles) {
        writeFile(it)
    }
}


private
fun Decoder.readClassPath(): ClassPath =
    DefaultClassPath.of(
        readCollectionInto({ size -> LinkedHashSet<File>(size) }) { container ->
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
fun Encoder.writeStrings(strings: List<String>) {
    writeCollection(strings) {
        writeString(it)
    }
}


private
fun Decoder.readStrings(): List<String> =
    readCollectionInto({ size -> ArrayList(size) }) { container ->
        container.add(readString())
    }


private
fun <T> Encoder.writeCollection(collection: Collection<T>, writeElement: (T) -> Unit) {
    writeSmallInt(collection.size)
    for (element in collection) {
        writeElement(element)
    }
}


private
fun Decoder.readCollection(readElement: () -> Unit) {
    val size = readSmallInt()
    for (i in 0 until size) {
        readElement()
    }
}


private
inline fun <T> Decoder.readCollectionInto(containerForSize: (Int) -> T, readElementInto: (T) -> Unit): T {
    val size = readSmallInt()
    val container = containerForSize(size)
    for (i in 0 until size) {
        readElementInto(container)
    }
    return container
}


private
val logger = Logging.getLogger(DefaultInstantExecution::class.java)
