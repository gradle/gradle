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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.IConventionAware
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.util.Path

import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.SortedSet
import java.util.function.Supplier


class InstantExecution(private val host: Host) {

    interface Host {

        val stateSerializer: StateSerializer

        val scheduledTasks: List<Task>

        fun dependenciesOf(task: Task): Set<Task>

        fun scheduleTasks(tasks: Iterable<Task>)

        fun <T> getService(serviceType: Class<T>): T

        fun getSystemProperty(propertyName: String): String?

        fun createProject(path: String): Project

        fun classLoaderFor(classPath: ClassPath): ClassLoader

        fun getProject(projectPath: String): Project

        fun registerProjects()
    }

    fun canExecuteInstantaneously() =
        isInstantExecutionEnabled && instantExecutionStateFile.isFile

    fun saveInstantExecutionState() {
        if (isInstantExecutionEnabled) {
            saveTasks()
        }
    }

    fun loadInstantExecutionState() {
        host.scheduleTasks(loadTasks())
    }

    private
    fun saveTasks() {
        KryoBackedEncoder(instantExecutionStateFile.outputStream()).use { encoder ->
            val scheduledTasks = host.scheduledTasks
            val relevantClassPath = classPathFor(scheduledTasks)
            encoder.serializeClassPath(relevantClassPath)
            saveRelevantProjectsFor(scheduledTasks, encoder)
            encoder.serializeCollection(scheduledTasks) { task ->
                encoder.saveStateOf(task)
            }
        }
    }

    private
    fun saveRelevantProjectsFor(tasks: List<Task>, encoder: KryoBackedEncoder) {
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
    fun loadTasks(): List<Task> {

        val tasksWithDependencies = loadTasksWithDependencies()

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
    fun loadTasksWithDependencies(): List<Pair<Task, List<String>>> =
        KryoBackedDecoder(instantExecutionStateFile.inputStream()).use { decoder ->

            val classPath = decoder.deserializeClassPath()
            val taskClassLoader = classLoaderFor(classPath)
            decoder.deserializeCollection {
                host.createProject(decoder.readString())
            }

            host.registerProjects()

            decoder.deserializeCollectionInto({ count -> ArrayList(count) }) { container ->
                val task = loadTask(decoder, taskClassLoader)
                container.add(task)
            }
        }

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
    fun KryoBackedEncoder.saveStateOf(task: Task) {
        val taskType = GeneratedSubclasses.unpack(task.javaClass)
        writeString(task.project.path)
        writeString(task.name)
        writeString(taskType.name)
        serializeCollection(host.dependenciesOf(task)) {
            writeString(it.path)
        }
        for (field in relevantStateOf(taskType)) {
            val fieldValue = field.getFieldValue(task)
            val conventionalValue = fieldValue ?: conventionalValueOf(task, field.name)
            val finalValue = unpack(conventionalValue) ?: continue
            val valueSerializer = host.stateSerializer.serializerFor(finalValue)
            if (valueSerializer == null) {
                logField(taskType, field.name, "serialize", "there's no serializer for type ${finalValue.javaClass}")
                continue
            }
            writeString(field.name)
            try {
                valueSerializer(this)
            } catch (e: Exception) {
                throw GradleException("Could not save the value of field `${field.name}` of task `${task.path}`.", e)
            }
            println("SERIALIZED ${task.path} field ${field.name} value $finalValue")
        }
        writeString("")
    }

    private
    fun conventionalValueOf(task: Task, fieldName: String): Any? =
        (task as IConventionAware).conventionMapping.getConventionValue(null, fieldName, false)

    private
    fun unpack(fieldValue: Any?) = when (fieldValue) {
        is DirectoryProperty -> fieldValue.asFile.orNull
        is RegularFileProperty -> fieldValue.asFile.orNull
        is Property<*> -> fieldValue.orNull
        is Supplier<*> -> fieldValue.get()
        is Function0<*> -> (fieldValue as (() -> Any?)).invoke()
        else -> fieldValue
    }

    private
    fun Field.getFieldValue(task: Task): Any? {
        isAccessible = true
        return get(task)
    }

    private
    fun loadTask(decoder: KryoBackedDecoder, taskClassLoader: ClassLoader): Pair<Task, List<String>> {
        val projectPath = decoder.readString()
        val taskName = decoder.readString()
        val typeName = decoder.readString()
        val taskClass = taskClassLoader.loadClass(typeName).asSubclass(Task::class.java)
        val taskFieldsByName = relevantStateOf(taskClass).associateBy { it.name }
        val task = host.getProject(projectPath).tasks.create(taskName, taskClass)
        val taskDependencies = decoder.deserializeStrings()
        while (true) {
            val fieldName = decoder.readString()
            if (fieldName.isEmpty()) {
                break
            }
            try {
                val deserializedValue = host.stateSerializer.read(decoder) ?: continue
                val value = (deserializedValue as? (ClassLoader) -> Any)?.invoke(taskClassLoader) ?: deserializedValue
                val field = taskFieldsByName.getValue(fieldName)
                println("DESERIALIZED ${task.path} field $fieldName value $value")
                when (field.type) {
                    DirectoryProperty::class.java -> (field.getFieldValue(task) as? DirectoryProperty)?.set(value as File)
                    RegularFileProperty::class.java -> (field.getFieldValue(task) as? RegularFileProperty)?.set(value as File)
                    Property::class.java -> (field.getFieldValue(task) as? Property<Any>)?.set(value)
                    Supplier::class.java -> field.setValue(task, Supplier { value })
                    Function0::class.java -> field.setValue(task, { value })
                    else -> {
                        if (field.type.isAssignableFrom(value.javaClass)) {
                            field.setValue(task, value)
                        } else {
                            logField(taskClass, fieldName, "deserialize", "${field.type} != ${value.javaClass}")
                        }
                    }
                }
            } catch (e: Exception) {
                throw GradleException("Could not load value of field `$fieldName` of task ${task.path}.", e)
            }
        }
        return task to taskDependencies
    }

    private
    fun Field.setValue(task: Task, value: Any) {
        isAccessible = true
        set(task, value)
    }

    private
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
    fun isRelevantDeclaringClass(declaringClass: Class<*>): Boolean {
        return declaringClass !in setOf(
            Object::class.java,
            GroovyObject::class.java,
            Task::class.java
        ) && declaringClass.name !in setOf(
            "org.gradle.api.internal.TaskInternal",
            "org.gradle.api.DefaultTask",
            "org.gradle.api.internal.AbstractTask",
            "org.gradle.api.internal.ConventionTask"
        )
    }

    private
    fun logField(taskType: Class<*>, name: String?, actionName: String, reason: String) {
        logger.lifecycle("Field `$name` from $taskType cannot be ${actionName}d because $reason.")
    }

    private
    val isInstantExecutionEnabled: Boolean
        get() = host.getSystemProperty("instantExecution") != null

    private
    val instantExecutionStateFile
        get() = File(".instant-execution-state")
}


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
fun KryoBackedEncoder.serializeClassPath(classPath: ClassPath) {
    serializeCollection(classPath.asFiles) {
        writeFile(it)
    }
}


private
fun KryoBackedDecoder.deserializeClassPath(): ClassPath =
    DefaultClassPath.of(
        deserializeCollectionInto({ count -> LinkedHashSet<File>(count) }) { container ->
            container.add(readFile())
        }
    )


private
fun KryoBackedEncoder.writeFile(file: File?) {
    BaseSerializerFactory.FILE_SERIALIZER.write(this, file)
}


private
fun KryoBackedDecoder.readFile(): File =
    BaseSerializerFactory.FILE_SERIALIZER.read(this)


private
fun KryoBackedDecoder.deserializeStrings(): List<String> =
    deserializeCollectionInto({ count -> ArrayList(count) }) { container ->
        container.add(readString())
    }


private
fun <T> KryoBackedEncoder.serializeCollection(elements: Collection<T>, serializeElement: (T) -> Unit) {
    writeSmallInt(elements.size)
    for (element in elements) {
        serializeElement(element)
    }
}


private
fun KryoBackedDecoder.deserializeCollection(deserializeElement: () -> Unit) {
    val count = readSmallInt()
    for (i in 0 until count) {
        deserializeElement()
    }
}


private
inline fun <T> KryoBackedDecoder.deserializeCollectionInto(containerSupplier: (Int) -> T, deserializeElement: (T) -> Unit): T {
    val count = readSmallInt()
    val container = containerSupplier(count)
    for (i in 0 until count) {
        deserializeElement(container)
    }
    return container
}


private
val logger = Logging.getLogger(InstantExecution::class.java)
