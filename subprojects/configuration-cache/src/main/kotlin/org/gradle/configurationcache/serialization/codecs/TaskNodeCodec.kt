/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.TaskDestroyablesInternal
import org.gradle.api.internal.tasks.TaskLocalStateInternal
import org.gradle.api.internal.tasks.properties.InputFilePropertyType
import org.gradle.api.internal.tasks.properties.InputParameterUtils
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType
import org.gradle.api.internal.tasks.properties.PropertyValue
import org.gradle.api.internal.tasks.properties.PropertyVisitor
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.FileNormalizer
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.problems.PropertyKind
import org.gradle.configurationcache.problems.PropertyTrace
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.IsolateContext
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.MutableIsolateContext
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.beans.BeanPropertyWriter
import org.gradle.configurationcache.serialization.beans.readPropertyValue
import org.gradle.configurationcache.serialization.beans.writeNextProperty
import org.gradle.configurationcache.serialization.readClassOf
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.readCollectionInto
import org.gradle.configurationcache.serialization.readEnum
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.withDebugFrame
import org.gradle.configurationcache.serialization.withIsolate
import org.gradle.configurationcache.serialization.withPropertyTrace
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.configurationcache.serialization.writeEnum
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.util.internal.DeferredUtil


class TaskNodeCodec(
    private val userTypesCodec: Codec<Any?>,
    private val taskNodeFactory: TaskNodeFactory
) : Codec<LocalTaskNode> {

    override suspend fun WriteContext.encode(value: LocalTaskNode) {
        val task = value.task
        writeTask(task)
    }

    override suspend fun ReadContext.decode(): LocalTaskNode {
        val task = readTask()
        val node = taskNodeFactory.getOrCreateNode(task) as LocalTaskNode
        node.isolated()
        return node
    }

    private
    suspend fun WriteContext.writeTask(task: TaskInternal) {
        withDebugFrame({ task.path }) {
            val taskType = GeneratedSubclasses.unpackType(task)
            val projectPath = task.project.path
            val taskName = task.name
            writeClass(taskType)
            writeString(projectPath)
            writeString(taskName)
            writeNullableString(task.reasonTaskIsIncompatibleWithConfigurationCache.orElse(null))

            withDebugFrame({ taskType.name }) {
                withTaskOf(taskType, task, userTypesCodec) {
                    writeUpToDateSpec(task)
                    writeCollection(task.outputs.cacheIfSpecs)
                    writeCollection(task.outputs.doNotCacheIfSpecs)
                    writeReasonNotToTrackState(task)
                    beanStateWriterFor(task.javaClass).run {
                        writeStateOf(task)
                        writeRegisteredPropertiesOf(
                            task,
                            this as BeanPropertyWriter
                        )
                    }
                    writeDestroyablesOf(task)
                    writeLocalStateOf(task)
                    writeRegisteredServicesOf(task)
                }
            }
        }
    }

    private
    suspend fun ReadContext.readTask(): Task {
        val taskType = readClassOf<Task>()
        val projectPath = readString()
        val taskName = readString()
        val incompatibleReason = readNullableString()

        val task = createTask(projectPath, taskName, taskType, incompatibleReason)

        withTaskOf(taskType, task, userTypesCodec) {
            readUpToDateSpec(task)
            readCollectionInto { task.outputs.cacheIfSpecs.uncheckedCast() }
            readCollectionInto { task.outputs.doNotCacheIfSpecs.uncheckedCast() }
            readReasonNotToTrackState(task)
            beanStateReaderFor(task.javaClass).run {
                readStateOf(task)
            }
            readRegisteredPropertiesOf(task)
            readDestroyablesOf(task)
            readLocalStateOf(task)
            readRegisteredServicesOf(task)
        }

        return task
    }

    private
    suspend fun WriteContext.writeUpToDateSpec(task: TaskInternal) {
        // TODO - should just write this as a bean field of the outputs object, and also do this for the registered properties above
        if (task.outputs.upToDateSpec.isEmpty) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            write(task.outputs.upToDateSpec)
        }
    }

    private
    suspend fun ReadContext.readUpToDateSpec(task: TaskInternal) {
        if (readBoolean()) {
            task.outputs.upToDateWhen(readNonNull<Spec<Task>>())
        }
    }

    private
    fun WriteContext.writeReasonNotToTrackState(task: TaskInternal) {
        writeNullableString(task.reasonNotToTrackState.orElse(null))
    }

    private
    fun ReadContext.readReasonNotToTrackState(task: TaskInternal) {
        val reasonNotToTrackState = readNullableString()
        if (reasonNotToTrackState != null) {
            task.doNotTrackState(reasonNotToTrackState)
        }
    }

    private
    suspend fun WriteContext.writeRegisteredServicesOf(task: TaskInternal) {
        writeCollection(task.requiredServices)
    }

    private
    suspend fun ReadContext.readRegisteredServicesOf(task: TaskInternal) {
        readCollection {
            task.usesService(readNonNull())
        }
    }

    private
    suspend fun WriteContext.writeDestroyablesOf(task: TaskInternal) {
        val destroyables = (task.destroyables as TaskDestroyablesInternal).registeredFiles
        if (destroyables.isEmpty) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            write(destroyables)
        }
    }

    private
    suspend fun ReadContext.readDestroyablesOf(task: TaskInternal) {
        if (readBoolean()) {
            task.destroyables.register(readNonNull<FileCollection>())
        }
    }

    private
    suspend fun WriteContext.writeLocalStateOf(task: TaskInternal) {
        val localState = (task.localState as TaskLocalStateInternal).registeredFiles
        if (localState.isEmpty) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            write(localState)
        }
    }

    private
    suspend fun ReadContext.readLocalStateOf(task: TaskInternal) {
        if (readBoolean()) {
            task.localState.register(readNonNull<FileCollection>())
        }
    }
}


private
suspend fun <T> T.withTaskOf(
    taskType: Class<*>,
    task: TaskInternal,
    codec: Codec<Any?>,
    action: suspend () -> Unit
) where T : IsolateContext, T : MutableIsolateContext {
    withIsolate(IsolateOwner.OwnerTask(task), codec) {
        withPropertyTrace(PropertyTrace.Task(taskType, task.path)) {
            if (task.isCompatibleWithConfigurationCache) {
                action()
            } else {
                forIncompatibleType(action)
            }
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
        val fileNormalizer: Class<out FileNormalizer>?,
        val directorySensitivity: DirectorySensitivity,
        val lineEndingSensitivity: LineEndingSensitivity
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

    suspend fun writeProperty(propertyName: String, propertyValue: Any?, kind: PropertyKind) {
        writeString(propertyName)
        writeNextProperty(propertyName, propertyValue, kind)
    }

    suspend fun writeInputProperty(propertyName: String, propertyValue: Any?) =
        writeProperty(propertyName, propertyValue, PropertyKind.InputProperty)

    suspend fun writeOutputProperty(propertyName: String, propertyValue: Any?) =
        writeProperty(propertyName, propertyValue, PropertyKind.OutputProperty)

    val inputProperties = collectRegisteredInputsOf(task)
    writeCollection(inputProperties) { property ->
        property.run {
            when (this) {
                is RegisteredProperty.InputFile -> {
                    val finalValue = DeferredUtil.unpackOrNull(propertyValue)
                    writeInputProperty(propertyName, finalValue)
                    writeBoolean(optional)
                    writeBoolean(true)
                    writeEnum(filePropertyType)
                    writeBoolean(skipWhenEmpty)
                    writeClass(fileNormalizer!!)
                    writeEnum(directorySensitivity)
                    writeEnum(lineEndingSensitivity)
                }
                is RegisteredProperty.Input -> {
                    val finalValue = InputParameterUtils.prepareInputParameterValue(propertyValue)
                    writeInputProperty(propertyName, finalValue)
                    writeBoolean(optional)
                    writeBoolean(false)
                }
                else -> throw IllegalStateException()
            }
        }
    }

    val outputProperties = collectRegisteredOutputsOf(task)
    writeCollection(outputProperties) { property ->
        property.run {
            val finalValue = DeferredUtil.unpackOrNull(propertyValue)
            writeOutputProperty(propertyName, finalValue)
            writeBoolean(optional)
            writeEnum(filePropertyType)
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
            directorySensitivity: DirectorySensitivity,
            lineEndingSensitivity: LineEndingSensitivity,
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
                    fileNormalizer,
                    directorySensitivity,
                    lineEndingSensitivity
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
                    val directorySensitivity = readEnum<DirectorySensitivity>()
                    val lineEndingNormalization = readEnum<LineEndingSensitivity>()
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
                        ignoreEmptyDirectories(directorySensitivity == DirectorySensitivity.IGNORE_DIRECTORIES)
                        normalizeLineEndings(lineEndingNormalization == LineEndingSensitivity.NORMALIZE_LINE_ENDINGS)
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
fun ReadContext.createTask(projectPath: String, taskName: String, taskClass: Class<out Task>, incompatibleReason: String?): TaskInternal {
    val task = getProject(projectPath).tasks.createWithoutConstructor(taskName, taskClass) as TaskInternal
    if (incompatibleReason != null) {
        task.notCompatibleWithConfigurationCache(incompatibleReason)
    }
    return task
}
