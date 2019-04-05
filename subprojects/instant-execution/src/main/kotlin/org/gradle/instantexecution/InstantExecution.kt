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
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.logging.Logging
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.reflect.ClassInspector
import org.gradle.internal.reflect.PropertyDetails
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.util.Path

import java.io.File
import java.lang.reflect.Method
import javax.inject.Inject


class InstantExecution(private val host: Host) {

    interface Host {

        val scheduledTasks: List<Task>

        fun scheduleTask(task: Task)

        fun <T> getService(serviceType: Class<T>): T

        fun getSystemProperty(propertyName: String): String?

        fun createProject(path: String): Project

        fun classLoaderFor(classPath: ClassPath): ClassLoader
    }

    fun canExecuteInstantaneously() =
        isInstantExecutionEnabled && instantExecutionStateFile.isFile

    fun saveInstantExecutionState() {
        if (!isInstantExecutionEnabled) {
            return
        }

        KryoBackedEncoder(instantExecutionStateFile.outputStream()).use { encoder ->
            val scheduledTasks = host.scheduledTasks
            val relevantProjectPaths = scheduledTasks.map { Path.path(it.project.path) }.toSortedSet()
            val relevantClassPath = classPathFor(scheduledTasks)
            encoder.serializeClassPath(relevantClassPath)
            encoder.serializeCollection(relevantProjectPaths) {
                encoder.writeString(it.path)
            }
            encoder.serializeCollection(scheduledTasks) { task ->
                saveStateOf(task, encoder)
            }
        }
    }

    fun loadInstantExecutionStateInto() {
        KryoBackedDecoder(instantExecutionStateFile.inputStream()).use { decoder ->
            val classPath = decoder.deserializeClassPath()
            val taskClassLoader = classLoaderFor(classPath)
            val relevantProjects = mutableListOf<Project>()
            decoder.deserializeCollection {
                relevantProjects.add(host.createProject(decoder.readString()))
            }
            decoder.deserializeCollection {
                val task = loadTask(relevantProjects, decoder, taskClassLoader)
                host.scheduleTask(task)
            }
        }
    }

    private
    fun classLoaderFor(classPath: ClassPath): ClassLoader =
        host.classLoaderFor(classPath)

    private
    fun classPathFor(tasks: List<Task>): ClassPath =
        tasks.map(::taskClassPath).fold(ClassPath.EMPTY, ClassPath::plus)

    private
    fun taskClassPath(task: Task): ClassPath =
        task.javaClass.classLoader.let(ClasspathUtil::getClasspath)

    private
    fun saveStateOf(task: Task, encoder: KryoBackedEncoder) {
        val taskType = GeneratedSubclasses.unpack(task.javaClass)
        encoder.writeString(task.project.path)
        encoder.writeString(task.name)
        encoder.writeString(taskType.name)
        for (property in relevantPropertiesOf(taskType)) {
            if (property.setters.isEmpty()) {
                logProperty(taskType, property, "there are no setters")
                continue
            }
            if (property.getters.isEmpty()) {
                logProperty(taskType, property, "there are no getters")
                continue
            }
            val getter = property.getters[0]
            if (!propertyValueSerializer.canWrite(getter.returnType)) {
                logProperty(taskType, property, "there's no serializer for type ${getter.returnType}")
                continue
            }
            getter.isAccessible = true

            val finalValue = getter(task)
            encoder.writeString(property.name)
            propertyValueSerializer.write(encoder, finalValue)
        }
        encoder.writeString("")
    }

    private
    fun loadTask(projects: List<Project>, decoder: KryoBackedDecoder, taskClassLoader: ClassLoader): Task {
        val projectPath = decoder.readString()
        val taskName = decoder.readString()
        val typeName = decoder.readString()
        val taskClass = taskClassLoader.loadClass(typeName).asSubclass(Task::class.java)
        val details = ClassInspector.inspect(taskClass)
        val task = projects.single { it.path == projectPath }.tasks.create(taskName, taskClass)
        while (true) {
            val propertyName = decoder.readString()
            if (propertyName.isEmpty()) {
                break
            }
            val value = propertyValueSerializer.read(decoder) ?: continue
            val property = details.getProperty(propertyName)
            for (setter in property.setters) {
                if (setter.parameterTypes[0].isAssignableFrom(value.javaClass)) {
                    setter.isAccessible = true
                    setter(task, value)
                    break
                }
            }
        }
        return task
    }


    private
    fun relevantPropertiesOf(taskType: Class<*>) =
        ClassInspector.inspect(taskType).properties.filter { property ->
            property.run {
                getters.any { relevant(it) && !injected(it) } || setters.any(::relevant)
            }
        }

    private
    fun injected(it: Method): Boolean =
        it.isAnnotationPresent(Inject::class.java)

    private
    fun relevant(it: Method): Boolean =
        it.declaringClass !in setOf(
            Object::class.java,
            GroovyObject::class.java,
            Task::class.java
        ) && it.declaringClass.name !in setOf(
            "org.gradle.api.internal.TaskInternal",
            "org.gradle.api.DefaultTask",
            "org.gradle.api.internal.AbstractTask",
            "org.gradle.api.internal.ConventionTask"
        )

    private
    fun logProperty(taskType: Class<*>, property: PropertyDetails, reason: String) {
        logger.info("Property `${property.name}` from $taskType cannot be serialized because $reason.")
    }

    private
    val isInstantExecutionEnabled: Boolean
        get() = host.getSystemProperty("instantExecution") != null

    private
    val propertyValueSerializer by lazy {
        PropertyValueSerializer(
            host.getService(DirectoryFileTreeFactory::class.java),
            host.getService(FileCollectionFactory::class.java)
        )
    }

    private
    val instantExecutionStateFile
        get() = File(".instant-execution-state")
}


private
fun KryoBackedEncoder.serializeClassPath(classPath: ClassPath) {
    serializeCollection(classPath.asFiles) {
        writeFile(it)
    }
}


private
fun KryoBackedDecoder.deserializeClassPath(): ClassPath {
    val classPathFiles = LinkedHashSet<File>()
    deserializeCollection {
        classPathFiles.add(readFile())
    }
    return DefaultClassPath.of(classPathFiles)
}


private
fun KryoBackedEncoder.writeFile(file: File?) {
    BaseSerializerFactory.FILE_SERIALIZER.write(this, file)
}


private
fun KryoBackedDecoder.readFile(): File =
    BaseSerializerFactory.FILE_SERIALIZER.read(this)


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
val logger = Logging.getLogger(InstantExecution::class.java)
