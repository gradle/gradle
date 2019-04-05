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
import org.gradle.internal.reflect.ClassInspector
import org.gradle.internal.reflect.PropertyDetails
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder

import java.io.File
import java.lang.reflect.Method
import javax.inject.Inject


class InstantExecution(private val host: Host) {

    interface Host {

        val scheduledTasks: List<Task>

        fun loadTaskClass(typeName: String): Class<out Task>

        fun scheduleTask(task: Task)

        fun <T> getService(serviceType: Class<T>): T

        fun getSystemProperty(propertyName: String): String?
    }

    fun canExecuteInstantaneously() =
        isInstantExecutionEnabled && instantExecutionStateFile().isFile

    fun saveInstantExecutionState() {
        if (!isInstantExecutionEnabled) {
            return
        }

        val propertyValueSerializer = propertyValueSerializer()
        KryoBackedEncoder(instantExecutionStateFile().outputStream()).use { encoder ->
            val scheduledTasks = host.scheduledTasks
            encoder.writeSmallInt(scheduledTasks.size)
            for (task in scheduledTasks) {
                saveStateOf(task, encoder, propertyValueSerializer)
            }
        }
    }

    fun loadInstantExecutionStateInto(rootProject: Project) {
        val propertyValueSerializer = propertyValueSerializer()
        KryoBackedDecoder(instantExecutionStateFile().inputStream()).use { decoder ->
            val count = decoder.readSmallInt()
            for (i in 0 until count) {
                val task = loadTask(rootProject, decoder, propertyValueSerializer)
                host.scheduleTask(task)
            }
        }
    }

    private
    fun saveStateOf(task: Task, encoder: KryoBackedEncoder, propertyValueSerializer: PropertyValueSerializer) {
        if (task.project.parent != null) {
            throw UnsupportedOperationException("Tasks must be in the root project")
        }
        val taskType = GeneratedSubclasses.unpack(task.javaClass)
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
        println("Property `${property.name}` from $taskType cannot be serialized because $reason.")
    }

    private
    fun loadTask(project: Project, decoder: KryoBackedDecoder, propertyValueSerializer: PropertyValueSerializer): Task {
        val taskName = decoder.readString()
        val typeName = decoder.readString()
        val taskClass = host.loadTaskClass(typeName)
        val details = ClassInspector.inspect(taskClass)
        val task = project.tasks.create(taskName, taskClass)
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
    val isInstantExecutionEnabled: Boolean
        get() = host.getSystemProperty("instantExecution") != null

    private
    fun propertyValueSerializer() =
        PropertyValueSerializer(
            host.getService(DirectoryFileTreeFactory::class.java),
            host.getService(FileCollectionFactory::class.java)
        )

    private
    fun instantExecutionStateFile() = File(".instant-execution-state")
}
