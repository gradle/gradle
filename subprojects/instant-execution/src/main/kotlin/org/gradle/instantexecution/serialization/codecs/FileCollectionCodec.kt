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

import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionLeafVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.tasks.util.PatternSet
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.serialize.SetSerializer
import java.io.File


internal
class FileCollectionCodec(
    private val fileSetSerializer: SetSerializer<File>,
    private val fileCollectionFactory: FileCollectionFactory,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory
) : Codec<FileCollectionInternal> {

    override suspend fun WriteContext.encode(value: FileCollectionInternal) {
        runCatching {
            val visitor = CollectingVisitor(directoryFileTreeFactory)
            value.visitLeafCollections(visitor)
            visitor.files
        }.apply {
            onSuccess { files ->
                writeBoolean(true)
                fileSetSerializer.write(this@encode, files)
            }
            onFailure { ex ->
                writeBoolean(false)
                writeString(ex.message)
            }
        }
    }

    override suspend fun ReadContext.decode(): FileCollectionInternal =
        if (readBoolean()) fileCollectionFactory.fixed(fileSetSerializer.read(this))
        else fileCollectionFactory.create(ErrorFileSet(readString()))
}


private
class CollectingVisitor(
    private val directoryFileTreeFactory: DirectoryFileTreeFactory
) : FileCollectionLeafVisitor {
    val files: MutableSet<File> = mutableSetOf()

    override fun visitCollection(fileCollection: FileCollectionInternal) {
        files.addAll(fileCollection.files)
    }

    override fun visitGenericFileTree(fileTree: FileTreeInternal) {
        visitCollection(fileTree)
    }

    override fun visitFileTree(root: File, patterns: PatternSet) {
        // TODO - should serialize a spec for the tree instead of its current elements
        val fileTree = directoryFileTreeFactory.create(root, patterns)
        visitCollection(FileTreeAdapter(fileTree))
    }
}


private
class ErrorFileSet(private val error: String) : MinimalFileSet {

    override fun getDisplayName() =
        "error-file-collection"

    override fun getFiles() =
        throw Exception(error)
}
