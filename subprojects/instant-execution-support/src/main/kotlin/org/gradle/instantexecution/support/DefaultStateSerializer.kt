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

package org.gradle.instantexecution.support

import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.FileCollection
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
import org.gradle.api.tasks.util.PatternSet
import org.gradle.instantexecution.StateSerializer
import org.gradle.instantexecution.ValueSerializer
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.ListSerializer
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SetSerializer

import java.io.File
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.LinkedHashSet


class DefaultStateSerializer(
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val fileCollectionFactory: FileCollectionFactory,
    private val fileResolver: FileResolver,
    private val instantiator: Instantiator
) : Serializer<Any>, StateSerializer {

    private
    val fileSetSerializer = SetSerializer(BaseSerializerFactory.FILE_SERIALIZER)

    private
    val stringSerializer = BaseSerializerFactory.STRING_SERIALIZER

    private
    val listSerializer = ListSerializer(this)

    private
    val fileTreeSerializer = object : Serializer<FileTreeInternal> {

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

    override fun read(decoder: Decoder): Any? =
        when (decoder.readByte()) {
            NULL_VALUE -> null
            STRING_TYPE -> stringSerializer.read(decoder)
            FILE_TREE_TYPE -> fileTreeSerializer.read(decoder)
            FILE_TYPE -> BaseSerializerFactory.FILE_SERIALIZER.read(decoder)
            FILE_COLLECTION_TYPE -> fileCollectionFactory.fixed(fileSetSerializer.read(decoder))
            LIST_TYPE -> listSerializer.read(decoder)
            ARTIFACT_COLLECTION_TYPE -> EmptyArtifactCollection(ImmutableFileCollection.of())
            BEAN_TYPE -> {
                val beanTypeName = decoder.readString()
                val beanFactory = { loader: ClassLoader ->
                    loader.loadClass(beanTypeName).declaredConstructors.first { it.parameterCount == 0 }.run {
                        isAccessible = true
                        newInstance()
                    }
                }
                beanFactory
            }
            CORE_TYPE -> coreDeserialize(decoder, this)
            else -> throw UnsupportedOperationException()
        }

    override fun write(encoder: Encoder, value: Any?) = encoder.run {
        when (value) {
            null -> writeByte(NULL_VALUE)
            is String -> serialize(STRING_TYPE, stringSerializer, value)
            is FileTreeInternal -> serialize(FILE_TREE_TYPE, fileTreeSerializer, value)
            is File -> serialize(FILE_TYPE, BaseSerializerFactory.FILE_SERIALIZER, value)
            is FileCollection -> serialize(FILE_COLLECTION_TYPE, fileSetSerializer, value.files)
            is List<*> -> serialize(LIST_TYPE, listSerializer, value)
            is ArtifactCollection -> writeByte(ARTIFACT_COLLECTION_TYPE)
            else -> {
                writeByte(BEAN_TYPE)
                writeString(value.javaClass.name)
            }
        }
    }

    private
    fun canWrite(type: Class<*>): Boolean =
        type == String::class.java ||
            type == File::class.java ||
            ArtifactCollection::class.java.isAssignableFrom(type) ||
            FileCollection::class.java.isAssignableFrom(type) ||
            List::class.java.isAssignableFrom(type) ||
            (!Modifier.isAbstract(type.modifiers) && type.declaredConstructors.any { it.parameterCount == 0 })

    private
    fun <T> Encoder.serialize(tag: Byte, serializer: Serializer<T>, value: T) {
        writeByte(tag)
        serializer.write(this, value)
    }

    override fun serializerFor(finalValue: Any): ValueSerializer? {
        if (canWrite(finalValue.javaClass)) {
            return { encoder ->
                write(encoder, finalValue)
            }
        }
        coreSerializerFor(finalValue)?.let { serializeValue ->
            return { encoder ->
                encoder.writeByte(CORE_TYPE)
                serializeValue(encoder)
            }
        }
        return null
    }

    private
    fun coreSerializerFor(value: Any): ValueSerializer? {
        if (value is DefaultCopySpec) {
            return { encoder ->
                val allSourcePaths = ArrayList<File>()
                collectSourcePathsFrom(value, allSourcePaths)
                encoder.writeByte(1.toByte())
                write(encoder, allSourcePaths)
            }
        }
        if (value is DestinationRootCopySpec) {
            return { encoder ->
                encoder.writeByte(2.toByte())
                write(encoder, value.destinationDir)
                coreSerializerFor(value.delegate)!!.invoke(encoder)
            }
        }
        return null
    }

    private
    fun collectSourcePathsFrom(copySpec: DefaultCopySpec, files: MutableList<File>) {
        files.addAll(copySpec.resolveSourceFiles())
        for (child in copySpec.children) {
            collectSourcePathsFrom(child as DefaultCopySpec, files)
        }
    }

    private
    fun coreDeserialize(decoder: Decoder, stateSerializer: Serializer<Any>): Any {
        when (decoder.readByte().toInt()) {
            1 -> {
                val sourceFiles = stateSerializer.read(decoder) as List<File>
                val copySpec = DefaultCopySpec(fileResolver, instantiator)
                copySpec.from(sourceFiles)
                return copySpec
            }
            2 -> {
                val destDir = stateSerializer.read(decoder) as? File
                val delegate = coreDeserialize(decoder, stateSerializer) as CopySpecInternal
                val spec = DestinationRootCopySpec(fileResolver, delegate)
                destDir?.let(spec::into)
                return spec
            }
            else -> throw IllegalStateException()
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
        const val BEAN_TYPE: Byte = 7
        const val CORE_TYPE: Byte = 8
    }
}
