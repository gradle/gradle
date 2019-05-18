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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.file.DefaultCompositeFileTree
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionLeafVisitor
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.internal.file.copy.DestinationRootCopySpec
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SetSerializer
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.LinkedHashSet
import kotlin.reflect.KClass


class StateSerialization(
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val fileCollectionFactory: FileCollectionFactory,
    private val fileResolver: FileResolver,
    private val instantiator: Instantiator
) {

    private
    val fileSetSerializer = SetSerializer(BaseSerializerFactory.FILE_SERIALIZER)

    private
    val stringSerializer = BaseSerializerFactory.STRING_SERIALIZER

    private
    val fileTreeSerializer = FileTreeSerializer()

    fun newSerializer(): StateSerializer =
        DefaultStateSerializer()

    fun deserializerFor(beanClassLoader: ClassLoader): StateDeserializer =
        DefaultStateDeserializer(beanClassLoader)

    private
    inner class DefaultStateSerializer : StateSerializer {

        override fun serializerFor(value: Any?): ValueSerializer? = when (value) {
            null -> { encoder, _ ->
                encoder.writeByte(NULL_VALUE)
            }
            is String -> { encoder, _ ->
                encoder.writeWithTag(STRING_TYPE, stringSerializer, value)
            }
            is FileTreeInternal -> { encoder, _ ->
                encoder.writeWithTag(FILE_TREE_TYPE, fileTreeSerializer, value)
            }
            is File -> { encoder, _ ->
                encoder.writeWithTag(FILE_TYPE, BaseSerializerFactory.FILE_SERIALIZER, value)
            }
            is FileCollection -> { encoder, _ ->
                encoder.writeWithTag(FILE_COLLECTION_TYPE, fileSetSerializer, value.files)
            }
            is List<*> -> listSerializerFor(value)
            is ArtifactCollection -> { encoder, _ ->
                encoder.writeByte(ARTIFACT_COLLECTION_TYPE)
            }
            is DefaultCopySpec -> defaultCopySpecSerializerFor(value)
            is DestinationRootCopySpec -> destinationRootCopySpecSerializerFor(value)
            is Project -> projectStateType(Project::class)
            is Gradle -> projectStateType(Gradle::class)
            is Settings -> projectStateType(Settings::class)
            is Task -> projectStateType(Task::class)
            else -> if (isBean(value.javaClass)) beanSerializerFor(value) else null
        }

        private
        fun projectStateType(type: KClass<*>): ValueSerializer? {
            LoggerFactory.getLogger(StateSerialization::class.java).warn("instant-execution > Cannot serialize object of type ${type.java.name} as these are not supported with instant execution.")
            return null
        }

        private
        fun defaultCopySpecSerializerFor(value: DefaultCopySpec): ValueSerializer = { encoder, listener ->
            val allSourcePaths = ArrayList<File>()
            collectSourcePathsFrom(value, allSourcePaths)
            encoder.writeByte(DEFAULT_COPY_SPEC)
            write(encoder, listener, allSourcePaths)
        }

        private
        fun destinationRootCopySpecSerializerFor(value: DestinationRootCopySpec): ValueSerializer = { encoder, listener ->
            encoder.writeByte(DESTINATION_ROOT_COPY_SPEC)
            write(encoder, listener, value.destinationDir)
            write(encoder, listener, value.delegate)
        }

        private
        fun beanSerializerFor(value: Any): ValueSerializer = { encoder, _ ->
            encoder.writeByte(BEAN)
            encoder.writeString(value.javaClass.name)
        }

        private
        fun listSerializerFor(value: List<*>): ValueSerializer = { encoder, listener ->
            encoder.writeByte(LIST_TYPE)
            encoder.writeSmallInt(value.size)
            for (item in value) {
                write(encoder, listener, item)
            }
        }

        private
        fun write(encoder: Encoder, listener: SerializationListener, value: Any?) {
            serializerFor(value)!!.invoke(encoder, listener)
        }

        private
        fun isBean(type: Class<*>) =
            !Modifier.isAbstract(type.modifiers) && type.declaredConstructors.any { it.parameterCount == 0 }
    }

    private
    inner class DefaultStateDeserializer(
        private val beanClassLoader: ClassLoader
    ) : StateDeserializer {

        override fun read(decoder: Decoder): Any? = when (decoder.readByte()) {
            NULL_VALUE -> null
            STRING_TYPE -> stringSerializer.read(decoder)
            FILE_TREE_TYPE -> fileTreeSerializer.read(decoder)
            FILE_TYPE -> BaseSerializerFactory.FILE_SERIALIZER.read(decoder)
            FILE_COLLECTION_TYPE -> fileCollectionFactory.fixed(fileSetSerializer.read(decoder))
            LIST_TYPE -> deserializeList(decoder)
            ARTIFACT_COLLECTION_TYPE -> EmptyArtifactCollection(ImmutableFileCollection.of())
            DEFAULT_COPY_SPEC -> deserializeDefaultCopySpec(decoder, this)
            DESTINATION_ROOT_COPY_SPEC -> deserializeDestinationRootCopySpec(decoder, this)
            BEAN -> deserializeBean(decoder, beanClassLoader)
            else -> throw UnsupportedOperationException()
        }

        private
        fun deserializeBean(decoder: Decoder, loader: ClassLoader): Any {
            val beanTypeName = decoder.readString()
            return loader.loadClass(beanTypeName).declaredConstructors.first { it.parameterCount == 0 }.run {
                isAccessible = true
                newInstance()
            }
        }

        private
        fun deserializeList(decoder: Decoder): List<Any?> {
            val size = decoder.readSmallInt()
            val items = ArrayList<Any?>(size)
            for (i in 1..size) {
                items.add(read(decoder))
            }
            return items
        }

        private
        fun deserializeDestinationRootCopySpec(decoder: Decoder, read: StateDeserializer): DestinationRootCopySpec {
            val destDir = read.read(decoder) as? File
            val delegate = read.read(decoder) as CopySpecInternal
            val spec = DestinationRootCopySpec(fileResolver, delegate)
            destDir?.let(spec::into)
            return spec
        }

        private
        fun deserializeDefaultCopySpec(decoder: Decoder, read: StateDeserializer): DefaultCopySpec {
            @Suppress("unchecked_cast")
            val sourceFiles = read.read(decoder) as List<File>
            val copySpec = DefaultCopySpec(fileResolver, instantiator)
            copySpec.from(sourceFiles)
            return copySpec
        }
    }

    private
    inner class FileTreeSerializer : Serializer<FileTreeInternal> {

        override fun read(decoder: Decoder): FileTreeInternal =
            DefaultCompositeFileTree(
                fileSetSerializer.read(decoder).map {
                    FileTreeAdapter(directoryFileTreeFactory.create(it))
                }
            )

        override fun write(encoder: Encoder, value: FileTreeInternal) {
            val visitor = FileTreeVisitor()
            value.visitLeafCollections(visitor)
            fileSetSerializer.write(encoder, visitor.roots)
        }
    }

    private
    class FileTreeVisitor : FileCollectionLeafVisitor {

        internal
        var roots = LinkedHashSet<File>()

        override fun visitCollection(fileCollection: FileCollectionInternal) = throw UnsupportedOperationException()

        override fun visitGenericFileTree(fileTree: FileTreeInternal) = throw UnsupportedOperationException()

        override fun visitFileTree(root: File, patterns: PatternSet) {
            roots.add(root)
        }
    }

    companion object {
        const val NULL_VALUE: Byte = 0
        const val STRING_TYPE: Byte = 1
        const val FILE_TREE_TYPE: Byte = 2
        const val FILE_TYPE: Byte = 3
        const val FILE_COLLECTION_TYPE: Byte = 4
        const val LIST_TYPE: Byte = 5
        const val ARTIFACT_COLLECTION_TYPE: Byte = 6
        const val DEFAULT_COPY_SPEC: Byte = 7
        const val DESTINATION_ROOT_COPY_SPEC: Byte = 8
        const val BEAN: Byte = 9
    }
}


private
fun <T> Encoder.writeWithTag(tag: Byte, serializer: Serializer<T>, value: T) {
    writeByte(tag)
    serializer.write(this, value)
}


private
fun collectSourcePathsFrom(copySpec: DefaultCopySpec, files: MutableList<File>) {
    files.addAll(copySpec.resolveSourceFiles())
    for (child in copySpec.children) {
        collectSourcePathsFrom(child as DefaultCopySpec, files)
    }
}
