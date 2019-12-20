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
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.properties.InputFilePropertyType
import org.gradle.api.internal.tasks.properties.InputParameterUtils
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType
import org.gradle.api.internal.tasks.properties.PropertyValue
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.tasks.FileNormalizer
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.runToCompletion
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.IsolateContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.MutableIsolateContext
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.beans.BeanPropertyWriter
import org.gradle.instantexecution.serialization.beans.readPropertyValue
import org.gradle.instantexecution.serialization.beans.writeNextProperty
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.readEnum
import org.gradle.instantexecution.serialization.withIsolate
import org.gradle.instantexecution.serialization.withPropertyTrace
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.instantexecution.serialization.writeEnum
import org.gradle.util.DeferredUtil


class TaskNodeCodec(
    private val projectStateRegistry: ProjectStateRegistry,
    private val userTypesCodec: Codec<Any?>,
    private val taskNodeFactory: TaskNodeFactory
) : Codec<LocalTaskNode> {

    override suspend fun WriteContext.encode(value: LocalTaskNode) {
        val task = value.task
        try {
            runToCompletionWithMutableStateOf(task.project) {
                writeTask(task)
            }
        } catch (e: Exception) {
            throw GradleException("Could not save state of $task.", e)
        }
    }

    override suspend fun ReadContext.decode(): LocalTaskNode {
        val task = readTask()
        val node = taskNodeFactory.getOrCreateNode(task) as LocalTaskNode
        node.isolated()
        return node
    }

    private
    suspend fun WriteContext.writeTask(task: TaskInternal) {
        val taskType = GeneratedSubclasses.unpackType(task)
        writeClass(taskType)
        writeString(task.project.path)
        writeString(task.name)

        withTaskOf(taskType, task, userTypesCodec) {
            beanStateWriterFor(task.javaClass).run {
                writeStateOf(task)
                writeRegisteredPropertiesOf(task, this as BeanPropertyWriter)
            }
            writeRegisteredServicesOf(task)
        }
    }

    private
    suspend fun ReadContext.readTask(): Task {
        val taskType = readClass().asSubclass(Task::class.java)
        val projectPath = readString()
        val taskName = readString()

        val task = createTask(projectPath, taskName, taskType)

        withTaskOf(taskType, task, userTypesCodec) {
            beanStateReaderFor(task.javaClass).run {
                readStateOf(task)
                readRegisteredPropertiesOf(task)
            }
            readRegisteredServicesOf(task)
        }

        return task
    }

    private
    suspend fun WriteContext.writeRegisteredServicesOf(task: TaskInternal) {
        writeCollection(task.requiredServices)
    }

    private
    suspend fun ReadContext.readRegisteredServicesOf(task: TaskInternal) {
        readCollection {
            task.usesService(read() as Provider<out BuildService<*>>)
        }
    }

    /**
     * Runs the suspending [block] to completion against the [public mutable state][ProjectState.withMutableState] of [project].
     */
    private
    fun runToCompletionWithMutableStateOf(project: Project, block: suspend () -> Unit) {
        projectStateRegistry.stateFor(project).withMutableState {
            runToCompletion(block)
        }
    }
}


private
inline fun <T> T.withTaskOf(
    taskType: Class<*>,
    task: Task,
    codec: Codec<Any?>,
    action: () -> Unit
) where T : IsolateContext, T : MutableIsolateContext {
    withIsolate(IsolateOwner.OwnerTask(task), codec) {
        withPropertyTrace(PropertyTrace.Task(taskType, task.path)) {
            action()
        }
    }
}


private
sealed class RegisteredProperty {

    data class Input(
        val propertyName: String,
        val propertyValue: PropertyValue,
        val optional: Boolean
    ) : RegisteredProperty()

    data class InputFile(
        val propertyName: String,
        val propertyValue: PropertyValue,
        val optional: Boolean,
        val filePropertyType: InputFilePropertyType,
        val skipWhenEmpty: Boolean,
        val incremental: Boolean,
        val fileNormalizer: Class<out FileNormalizer>?
    ) : RegisteredProperty()

    data class OutputFile(
        val propertyName: String,
        val propertyValue: PropertyValue,
        val optional: Boolean,
        val filePropertyType: OutputFilePropertyType
    ) : RegisteredProperty()
}


private
suspend fun WriteContext.writeRegisteredPropertiesOf(
    task: Task,
    propertyWriter: BeanPropertyWriter
) = propertyWriter.run {

    suspend fun writeProperty(propertyName: String, propertyValue: Any?, kind: PropertyKind): Boolean {
        writeString(propertyName)
        return writeNextProperty(propertyName, propertyValue, kind)
    }

    suspend fun writeInputProperty(propertyName: String, propertyValue: Any?): Boolean =
        writeProperty(propertyName, propertyValue, PropertyKind.InputProperty)

    suspend fun writeOutputProperty(propertyName: String, propertyValue: Any?): Boolean =
        writeProperty(propertyName, propertyValue, PropertyKind.OutputProperty)

    val inputProperties = collectRegisteredInputsOf(task)
    writeCollection(inputProperties) { property ->
        property.run {
            when (this) {
                is RegisteredProperty.InputFile -> {
                    val finalValue = DeferredUtil.unpack(propertyValue)
                    if (writeInputProperty(propertyName, finalValue)) {
                        writeBoolean(optional)
                        writeBoolean(true)
                        writeEnum(filePropertyType)
                        writeBoolean(skipWhenEmpty)
                        writeClass(fileNormalizer!!)
                    }
                }
                is RegisteredProperty.Input -> {
                    val finalValue = InputParameterUtils.prepareInputParameterValue(propertyValue)
                    if (writeInputProperty(propertyName, finalValue)) {
                        writeBoolean(optional)
                        writeBoolean(false)
                    }
                }
            }
        }
    }

    val outputProperties = collectRegisteredOutputsOf(task)
    writeCollection(outputProperties) { property ->
        property.run {
            val finalValue = DeferredUtil.unpack(propertyValue)
            if (writeOutputProperty(propertyName, finalValue)) {
                writeBoolean(optional)
                writeEnum(filePropertyType)
            }
        }
    }
}


private
fun collectRegisteredOutputsOf(task: Task): List<RegisteredProperty.OutputFile> {

    val properties = mutableListOf<RegisteredProperty.OutputFile>()

    (task.outputs as TaskOutputsInternal).visitRegisteredProperties(object : PropertyVisitor.Adapter() {

        override fun visitOutputFileProperty(
            propertyName: String,
            optional: Boolean,
            value: PropertyValue,
            filePropertyType: OutputFilePropertyType
        ) {
            properties.add(
                RegisteredProperty.OutputFile(
                    propertyName,
                    value,
                    optional,
                    filePropertyType
                )
            )
        }
    })
    return properties
}


private
fun collectRegisteredInputsOf(task: Task): List<RegisteredProperty> {

    val properties = mutableListOf<RegisteredProperty>()

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
            properties.add(
                RegisteredProperty.InputFile(
                    propertyName,
                    propertyValue,
                    optional,
                    filePropertyType,
                    skipWhenEmpty,
                    incremental,
                    fileNormalizer
                )
            )
        }

        override fun visitInputProperty(
            propertyName: String,
            propertyValue: PropertyValue,
            optional: Boolean
        ) {
            properties.add(
                RegisteredProperty.Input(
                    propertyName,
                    propertyValue,
                    optional
                )
            )
        }
    })
    return properties
}


private
suspend fun ReadContext.readRegisteredPropertiesOf(task: Task) {
    readInputPropertiesOf(task)
    readOutputPropertiesOf(task)
}


private
suspend fun ReadContext.readInputPropertiesOf(task: Task) =
    readCollection {
        val propertyName = readString()
        readPropertyValue(PropertyKind.InputProperty, propertyName) { propertyValue ->
            val optional = readBoolean()
            val isFileInputProperty = readBoolean()
            when {
                isFileInputProperty -> {
                    val filePropertyType = readEnum<InputFilePropertyType>()
                    val skipWhenEmpty = readBoolean()
                    val normalizer = readClass()
                    task.inputs.run {
                        when (filePropertyType) {
                            InputFilePropertyType.FILE -> file(pack(propertyValue))
                            InputFilePropertyType.DIRECTORY -> dir(pack(propertyValue))
                            InputFilePropertyType.FILES -> files(pack(propertyValue))
                        }
                    }.run {
                        withPropertyName(propertyName)
                        optional(optional)
                        skipWhenEmpty(skipWhenEmpty)
                        withNormalizer(normalizer.uncheckedCast())
                    }
                }
                else -> {
                    task.inputs
                        .property(propertyName, propertyValue)
                        .optional(optional)
                }
            }
        }
    }


private
fun pack(value: Any?) = value ?: Providers.notDefined<Any>()


private
suspend fun ReadContext.readOutputPropertiesOf(task: Task) =
    readCollection {
        val propertyName = readString()
        readPropertyValue(PropertyKind.OutputProperty, propertyName) { propertyValue ->
            val optional = readBoolean()
            val filePropertyType = readEnum<OutputFilePropertyType>()
            task.outputs.run {
                when (filePropertyType) {
                    OutputFilePropertyType.DIRECTORY -> dir(pack(propertyValue))
                    OutputFilePropertyType.DIRECTORIES -> dirs(pack(propertyValue))
                    OutputFilePropertyType.FILE -> file(pack(propertyValue))
                    OutputFilePropertyType.FILES -> files(pack(propertyValue))
                }
            }.run {
                withPropertyName(propertyName)
                optional(optional)
            }
        }
    }


private
fun ReadContext.createTask(projectPath: String, taskName: String, taskClass: Class<out Task>) =
    getProject(projectPath).tasks.createWithoutConstructor(taskName, taskClass) as TaskInternal
