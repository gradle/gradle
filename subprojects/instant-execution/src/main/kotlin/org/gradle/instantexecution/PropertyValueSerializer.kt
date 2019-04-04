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

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.DefaultCompositeFileTree
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionLeafVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SetSerializer

import java.io.File
import java.util.LinkedHashSet


internal
class PropertyValueSerializer(
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val fileCollectionFactory: FileCollectionFactory
) : Serializer<Any> {

    private
    val fileSetSerializer = SetSerializer(BaseSerializerFactory.FILE_SERIALIZER)

    private
    val stringSerializer = BaseSerializerFactory.STRING_SERIALIZER

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
            else -> throw UnsupportedOperationException()
        }

    override fun write(encoder: Encoder, value: Any?) =
        when (value) {
            null -> encoder.writeByte(NULL_VALUE)
            is String -> {
                encoder.writeByte(STRING_TYPE)
                stringSerializer.write(encoder, value)
            }
            is FileTreeInternal -> {
                encoder.writeByte(FILE_TREE_TYPE)
                fileTreeSerializer.write(encoder, value)
            }
            is File -> {
                encoder.writeByte(FILE_TYPE)
                BaseSerializerFactory.FILE_SERIALIZER.write(encoder, value)
            }
            is FileCollection -> {
                encoder.writeByte(FILE_COLLECTION_TYPE)
                fileSetSerializer.write(encoder, value.files)
            }
            else -> throw UnsupportedOperationException()
        }

    fun canWrite(type: Class<*>): Boolean =
        type == String::class.java || type == FileTree::class.java || type == File::class.java || type == FileCollection::class.java

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
    }
}
