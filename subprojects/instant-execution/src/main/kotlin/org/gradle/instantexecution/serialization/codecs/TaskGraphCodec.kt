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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.tasks.properties.InputFilePropertyType
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType
import org.gradle.api.internal.tasks.properties.PropertyValue
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import org.gradle.api.tasks.FileNormalizer
import org.gradle.instantexecution.ClassicModeBuild
import org.gradle.instantexecution.serialization.IsolateContext
import org.gradle.instantexecution.serialization.MutableIsolateContext
import org.gradle.instantexecution.serialization.beans.writeNextProperty
import org.gradle.instantexecution.serialization.beans.writingProperties
import org.gradle.instantexecution.serialization.MutableReadContext
import org.gradle.instantexecution.serialization.MutableWriteContext
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.beans.BeanPropertyWriter
import org.gradle.instantexecution.serialization.beans.readEachProperty
import org.gradle.instantexecution.serialization.readClass
import org.gradle.instantexecution.serialization.readCollectionInto
import org.gradle.instantexecution.serialization.readEnum
import org.gradle.instantexecution.serialization.readStrings
import org.gradle.instantexecution.serialization.withIsolate
import org.gradle.instantexecution.serialization.withPropertyTrace
import org.gradle.instantexecution.serialization.writeClass
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.instantexecution.serialization.writeEnum
import org.gradle.instantexecution.serialization.writeStrings


internal
class TaskGraphCodec {

    fun MutableWriteContext.writeTaskGraphOf(build: ClassicModeBuild, tasks: List<Task>) {
        writeCollection(tasks) { task ->
            try {
                writeTask(task, build.dependenciesOf(task))
            } catch (e: Throwable) {
                throw GradleException("Could not save state of $task.", e)
            }
        }
    }

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
    fun MutableWriteContext.writeTask(task: Task, dependencies: Set<Task>) {
        val taskType = GeneratedSubclasses.unpack(task.javaClass)
        writeClass(taskType)
        writeString(task.project.path)
        writeString(task.name)
        writeStrings(dependencies.map { it.path })

        withTaskOf(taskType, task) {
            beanPropertyWriterFor(taskType).run {
                writeFieldsOf(task)
                writeRegisteredPropertiesOf(task, this)
            }
        }
    }

    private
    fun MutableReadContext.readTask(): Pair<Task, List<String>> {
        val taskType = readClass().asSubclass(Task::class.java)
        val projectPath = readString()
        val taskName = readString()
        val taskDependencies = readStrings()

        val task = createTask(projectPath, taskName, taskType)

        withTaskOf(taskType, task) {
            beanPropertyReaderFor(taskType).run {
                readFieldsOf(task)
                readRegisteredPropertiesOf(task)
            }
        }

        return task to taskDependencies
    }
}


private
inline fun <T> T.withTaskOf(
    taskType: Class<*>,
    task: Task,
    action: () -> Unit
) where T : IsolateContext, T : MutableIsolateContext {
    withIsolate(task) {
        withPropertyTrace(PropertyTrace.Task(taskType, task.path)) {
            action()
        }
    }
}


private
fun WriteContext.writeRegisteredPropertiesOf(
    task: Task,
    propertyWriter: BeanPropertyWriter
) = propertyWriter.run {

    fun writeProperty(propertyName: String, propertyValue: PropertyValue, kind: PropertyKind): Boolean {
        val value = unpack(propertyValue.call()) ?: return false
        return writeNextProperty(propertyName, value, kind)
    }

    fun writeInputProperty(propertyName: String, propertyValue: PropertyValue): Boolean =
        writeProperty(propertyName, propertyValue, PropertyKind.InputProperty)

    fun writeOutputProperty(propertyName: String, propertyValue: PropertyValue): Boolean =
        writeProperty(propertyName, propertyValue, PropertyKind.OutputProperty)

    writingProperties {
        (task.inputs as TaskInputsInternal).visitRegisteredProperties(object : PropertyVisitor.Adapter() {

            override fun visitInputFileProperty(
                propertyName: String,
                optional: Boolean,
                skipWhenEmpty: Boolean,
                incremental: Boolean,
                fileNormalizer: Class<out FileNormalizer>?,
                propertyValue: PropertyValue,
                filePropertyType: InputFilePropertyType
            ) {
                if (!writeInputProperty(propertyName, propertyValue)) {
                    return
                }
                writeBoolean(optional)
                writeBoolean(true)
                writeEnum(filePropertyType)
                writeBoolean(skipWhenEmpty)
                writeClass(fileNormalizer!!)
            }

            override fun visitInputProperty(
                propertyName: String,
                propertyValue: PropertyValue,
                optional: Boolean
            ) {
                if (!writeInputProperty(propertyName, propertyValue)) {
                    return
                }
                writeBoolean(optional)
                writeBoolean(false)
            }
        })
    }

    writingProperties {
        (task.outputs as TaskOutputsInternal).visitRegisteredProperties(object : PropertyVisitor.Adapter() {

            override fun visitOutputFileProperty(
                propertyName: String,
                optional: Boolean,
                value: PropertyValue,
                filePropertyType: OutputFilePropertyType
            ) {
                if (!writeOutputProperty(propertyName, value)) {
                    return
                }
                writeBoolean(optional)
                writeEnum(filePropertyType)
            }
        })
    }
}


private
fun ReadContext.readRegisteredPropertiesOf(task: Task) {
    readInputPropertiesOf(task)
    readOutputPropertiesOf(task)
}


private
fun ReadContext.readInputPropertiesOf(task: Task) =
    readEachProperty(PropertyKind.InputProperty) { propertyName, propertyValue ->
        val optional = readBoolean()
        val isFileInputProperty = readBoolean()
        require(propertyValue != null)
        when {
            isFileInputProperty -> {
                val filePropertyType = readEnum<InputFilePropertyType>()
                val skipWhenEmpty = readBoolean()
                val normalizer = readClass()
                task.inputs.run {
                    when (filePropertyType) {
                        InputFilePropertyType.FILE -> file(propertyValue)
                        InputFilePropertyType.DIRECTORY -> dir(propertyValue)
                        InputFilePropertyType.FILES -> files(propertyValue)
                    }
                }.run {
                    withPropertyName(propertyName)
                    optional(optional)
                    skipWhenEmpty(skipWhenEmpty)
                    @Suppress("unchecked_cast")
                    withNormalizer(normalizer as Class<out FileNormalizer>)
                }
            }
            else -> {
                task.inputs
                    .property(propertyName, propertyValue)
                    .optional(optional)
            }
        }
    }


private
fun ReadContext.readOutputPropertiesOf(task: Task) =
    readEachProperty(PropertyKind.OutputProperty) { propertyName, propertyValue ->
        val optional = readBoolean()
        val filePropertyType = readEnum<OutputFilePropertyType>()
        require(propertyValue != null)
        task.outputs.run {
            when (filePropertyType) {
                OutputFilePropertyType.DIRECTORY -> dir(propertyValue)
                OutputFilePropertyType.DIRECTORIES -> dirs(propertyValue)
                OutputFilePropertyType.FILE -> file(propertyValue)
                OutputFilePropertyType.FILES -> files(propertyValue)
            }
        }.run {
            withPropertyName(propertyName)
            optional(optional)
        }
    }


private
fun ReadContext.createTask(projectPath: String, taskName: String, taskClass: Class<out Task>) =
    getProject(projectPath).tasks.createWithoutConstructor(taskName, taskClass)
