/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.TaskDestroyablesInternal
import org.gradle.api.internal.tasks.TaskInputFilePropertyBuilderInternal
import org.gradle.api.internal.tasks.TaskLocalStateInternal
import org.gradle.api.specs.Spec
import org.gradle.execution.plan.LocalTaskNode
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.base.serialize.readProjectRef
import org.gradle.internal.cc.base.serialize.writeProjectRef
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.properties.InputBehavior
import org.gradle.internal.properties.InputFilePropertyType
import org.gradle.internal.properties.OutputFilePropertyType
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateContext
import org.gradle.internal.serialize.graph.MutableIsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readClassOf
import org.gradle.internal.serialize.graph.readCollection
import org.gradle.internal.serialize.graph.readCollectionInto
import org.gradle.internal.serialize.graph.readEnum
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.readPropertyValue
import org.gradle.internal.serialize.graph.withDebugFrame
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.serialize.graph.withPropertyTrace
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeEnum
import org.gradle.internal.serialize.graph.writePropertyValue
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
            val taskName = task.name
            writeClass(taskType)
            writeProjectRef(task.project)
            writeString(taskName)
            writeLong(task.taskIdentity.uniqueId)
            writeNullableString(task.reasonTaskIsIncompatibleWithConfigurationCache.orElse(null))

            withDebugFrame({ taskType.name }) {
                withTaskOf(taskType, task, userTypesCodec) {
                    writeUpToDateSpec(task)
                    writeCollection(task.outputs.cacheIfSpecs)
                    writeCollection(task.outputs.doNotCacheIfSpecs)
                    writeReasonNotToTrackState(task)
                    beanStateWriterFor(task.javaClass).run {
                        writeStateOf(task)
                        withTaskReferencesAllowed {
                            writeRegisteredPropertiesOf(
                                task
                            )
                        }
                    }
                    writeDestroyablesOf(task)
                    writeLocalStateOf(task)
                    writeRequiredServices(task)
                }
            }
        }
    }

    private
    suspend fun ReadContext.readTask(): Task {
        val taskType = readClassOf<Task>()
        val project = readProjectRef()
        val taskName = readString()
        val uniqueId = readLong()
        val incompatibleReason = readNullableString()

        val task = createTask(project, taskName, taskType, uniqueId, incompatibleReason)

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
            readRequiredServices(task)
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
    suspend fun WriteContext.writeRequiredServices(task: TaskInternal) {
        writeCollection(task.requiredServices.searchServices())
    }

    private
    suspend fun ReadContext.readRequiredServices(task: TaskInternal) {
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
    withIsolate(IsolateOwners.OwnerTask(task), codec) {
        withPropertyTrace(PropertyTrace.Task(taskType, task.identityPath.path)) {
            if (task.isCompatibleWithConfigurationCache) {
                action()
            } else {
                forIncompatibleTask(trace, task.reasonTaskIsIncompatibleWithConfigurationCache.get(), action)
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
        val behavior: InputBehavior,
        val normalizer: FileNormalizer?,
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
suspend fun WriteContext.writeRegisteredPropertiesOf(task: Task) {

    suspend fun writeProperty(propertyName: String, propertyValue: Any?, kind: PropertyKind) {
        writeString(propertyName)
        writePropertyValue(kind, propertyName, propertyValue)
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
                    val finalValue = DeferredUtil.unpackNestableDeferred(propertyValue)
                    writeInputProperty(propertyName, finalValue)
                    writeBoolean(optional)
                    writeBoolean(true)
                    writeEnum(filePropertyType)
                    writeEnum(behavior)
                    writeEnum(normalizer!! as InputNormalizer)
                    writeEnum(directorySensitivity)
                    writeEnum(lineEndingSensitivity)
                }

                is RegisteredProperty.Input -> {
                    val finalValue = DeferredUtil.unpackNestableDeferred(propertyValue)
                    writeInputProperty(propertyName, finalValue)
                    writeBoolean(optional)
                    writeBoolean(false)
                }

                else -> error("Unexpected registered property: ${this.javaClass.name}")
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

    (task.outputs as TaskOutputsInternal).visitRegisteredProperties(object : PropertyVisitor {

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

    (task.inputs as TaskInputsInternal).visitRegisteredProperties(object : PropertyVisitor {

        override fun visitInputFileProperty(
            propertyName: String,
            optional: Boolean,
            behavior: InputBehavior,
            directorySensitivity: DirectorySensitivity,
            lineEndingSensitivity: LineEndingSensitivity,
            normalizer: FileNormalizer?,
            propertyValue: PropertyValue,
            filePropertyType: InputFilePropertyType
        ) {
            properties.add(
                RegisteredProperty.InputFile(
                    propertyName,
                    propertyValue,
                    optional,
                    filePropertyType,
                    behavior,
                    normalizer,
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
                    val inputBehavior = readEnum<InputBehavior>()
                    val normalizer = readEnum<InputNormalizer>()
                    val directorySensitivity = readEnum<DirectorySensitivity>()
                    val lineEndingNormalization = readEnum<LineEndingSensitivity>()
                    ((task as TaskInternal).inputs.run {
                        when (filePropertyType) {
                            InputFilePropertyType.FILE -> file(pack(propertyValue))
                            InputFilePropertyType.DIRECTORY -> dir(pack(propertyValue))
                            InputFilePropertyType.FILES -> files(pack(propertyValue))
                        }
                    } as TaskInputFilePropertyBuilderInternal).run {
                        withPropertyName(propertyName)
                        optional(optional)
                        skipWhenEmpty(inputBehavior.shouldSkipWhenEmpty())
                        withInternalNormalizer(normalizer)
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
fun createTask(project: ProjectInternal, taskName: String, taskClass: Class<out Task>, uniqueId: Long, incompatibleReason: String?): TaskInternal {
    val task = project.tasks.createWithoutConstructor(taskName, taskClass, uniqueId) as TaskInternal
    if (incompatibleReason != null) {
        task.notCompatibleWithConfigurationCache(incompatibleReason)
    }
    return task
}


private
inline fun IsolateContext.withTaskReferencesAllowed(action: () -> Unit) {
    val ownerTask = isolate.owner as IsolateOwners.OwnerTask
    try {
        ownerTask.allowTaskReferences = true
        action()
    } finally {
        ownerTask.allowTaskReferences = false
    }
}
