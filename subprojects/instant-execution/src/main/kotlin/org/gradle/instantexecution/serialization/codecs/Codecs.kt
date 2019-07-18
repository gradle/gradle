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

import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.util.internal.PatternSpecFactory
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.DecodingProvider
import org.gradle.instantexecution.serialization.Encoding
import org.gradle.instantexecution.serialization.EncodingProvider
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.SerializerCodec
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.ownerService
import org.gradle.instantexecution.serialization.reentrant
import org.gradle.instantexecution.serialization.unsupported
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory.BOOLEAN_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BYTE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.CHAR_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.DOUBLE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FLOAT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.INTEGER_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.LONG_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.SHORT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SetSerializer
import org.gradle.process.internal.ExecActionFactory
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import kotlin.reflect.KClass


class Codecs(
    directoryFileTreeFactory: DirectoryFileTreeFactory,
    fileCollectionFactory: FileCollectionFactory,
    fileResolver: FileResolver,
    instantiator: Instantiator,
    listenerManager: ListenerManager
) : EncodingProvider, DecodingProvider {

    private
    val fileSetSerializer = SetSerializer(FILE_SERIALIZER)

    private
    val bindings = bindings {

        bind(unsupported<Project>())
        bind(unsupported<Gradle>())
        bind(unsupported<Settings>())
        bind(unsupported<TaskContainer>())
        bind(unsupported<ConfigurationContainer>())

        bind(STRING_SERIALIZER)
        bind(BOOLEAN_SERIALIZER)
        bind(INTEGER_SERIALIZER)
        bind(CHAR_SERIALIZER)
        bind(SHORT_SERIALIZER)
        bind(LONG_SERIALIZER)
        bind(BYTE_SERIALIZER)
        bind(FLOAT_SERIALIZER)
        bind(DOUBLE_SERIALIZER)
        bind(FileTreeCodec(fileSetSerializer, directoryFileTreeFactory))
        bind(FILE_SERIALIZER)
        bind(ClassCodec)
        bind(MethodCodec)

        bind(listCodec)

        // Only serialize certain Set implementations for now, as some custom types extend Set (eg DomainObjectContainer)
        bind(linkedHashSetCodec)
        bind(hashSetCodec)
        bind(treeSetCodec)

        // Only serialize certain Map implementations for now, as some custom types extend Map (eg DefaultManifest)
        bind(linkedHashMapCodec)
        bind(hashMapCodec)
        bind(treeMapCodec)

        bind(arrayCodec)

        bind(ListenerBroadcastCodec(listenerManager))
        bind(LoggerCodec)

        bind(ConfigurableFileCollectionCodec(fileSetSerializer, fileCollectionFactory))
        bind(FileCollectionCodec(fileSetSerializer, fileCollectionFactory, directoryFileTreeFactory))
        bind(ArtifactCollectionCodec)

        bind(DefaultCopySpecCodec(fileResolver, instantiator))
        bind(DestinationRootCopySpecCodec(fileResolver))

        bind(TaskReferenceCodec)

        bind(ownerService<ObjectFactory>())
        bind(ownerService<PatternSpecFactory>())
        bind(ownerService<FileResolver>())
        bind(ownerService<Instantiator>())
        bind(ownerService<FileCollectionFactory>())
        bind(ownerService<FileOperations>())
        bind(ownerService<BuildOperationExecutor>())
        bind(ownerService<ToolingModelBuilderRegistry>())
        bind(ownerService<ExecActionFactory>())
        bind(ownerService<BuildOperationListenerManager>())
        bind(ownerService<BuildRequestMetaData>())

        // This protects the BeanCodec against StackOverflowErrors but
        // we can still get them for the other codecs, for instance,
        // with deeply nested Lists, deeply nested Maps, etc.
        bind(reentrant(BeanCodec()))
    }

    private
    val nullEncoding = encoding {
        writeByte(NULL_VALUE)
    }

    private
    val encodings = HashMap<Class<*>, Encoding?>()

    override fun WriteContext.encodingFor(candidate: Any?): Encoding? = when (candidate) {
        null -> nullEncoding
        else -> encodings.computeIfAbsent(candidate.javaClass, ::computeEncoding)
    }

    override suspend fun ReadContext.decode(): Any? = when (val tag = readByte()) {
        NULL_VALUE -> null
        else -> bindings[tag.toInt()].codec.run { decode() }
    }

    private
    fun computeEncoding(type: Class<*>): Encoding? =
        bindings.find { it.type.isAssignableFrom(type) }?.run {
            encoding { value ->
                require(value != null)
                writeByte(tag)
                codec.run { encode(value) }
            }
        }

    private
    fun encoding(e: Encoding) = e

    internal
    companion object {
        const val NULL_VALUE: Byte = -1
    }
}


private
inline fun bindings(block: BindingsBuilder.() -> Unit): List<Binding> =
    BindingsBuilder().apply(block).build()


private
data class Binding(
    val tag: Byte,
    val type: Class<*>,
    val codec: Codec<Any>
)


private
class BindingsBuilder {

    private
    val bindings = mutableListOf<Binding>()

    fun build(): List<Binding> = bindings.toList()

    fun bind(type: Class<*>, codec: Codec<*>) {
        require(bindings.none { it.type === type })
        val tag = bindings.size
        require(tag < Byte.MAX_VALUE)
        bindings.add(
            Binding(tag.toByte(), type, codec.uncheckedCast())
        )
    }

    inline fun <reified T> bind(codec: Codec<T>) =
        bind(T::class.java, codec)

    inline fun <reified T> bind(serializer: Serializer<T>) =
        bind(T::class.java, serializer)

    fun bind(type: KClass<*>, codec: Codec<*>) =
        bind(type.java, codec)

    fun bind(type: KClass<*>, serializer: Serializer<*>) =
        bind(type.java, serializer)

    fun bind(type: Class<*>, serializer: Serializer<*>) =
        bind(type, SerializerCodec(serializer))
}
