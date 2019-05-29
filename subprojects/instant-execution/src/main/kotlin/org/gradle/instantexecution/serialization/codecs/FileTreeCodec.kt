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

import org.gradle.api.internal.file.DefaultCompositeFileTree
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionLeafVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.tasks.util.PatternSet
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.serialize.SetSerializer
import java.io.File


internal
class FileTreeCodec(
    private val fileSetSerializer: SetSerializer<File>,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory
) : Codec<FileTreeInternal> {

    override fun WriteContext.encode(value: FileTreeInternal) {
        fileSetSerializer.write(this, fileTreeRootsOf(value))
    }

    override fun ReadContext.decode(): FileTreeInternal? =
        DefaultCompositeFileTree(
            fileSetSerializer.read(this).map {
                FileTreeAdapter(directoryFileTreeFactory.create(it))
            }
        )

    private
    fun fileTreeRootsOf(value: FileTreeInternal): LinkedHashSet<File> {
        val visitor = FileTreeVisitor()
        value.visitLeafCollections(visitor)
        return visitor.roots
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
}
