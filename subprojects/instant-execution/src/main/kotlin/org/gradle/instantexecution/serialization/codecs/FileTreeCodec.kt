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
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.tasks.util.PatternSet
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import java.io.File


private
typealias FileTreeSpec = Pair<File, PatternSet>


internal
class FileTreeCodec(
    private val directoryFileTreeFactory: DirectoryFileTreeFactory
) : Codec<FileTreeInternal> {

    override suspend fun WriteContext.encode(value: FileTreeInternal) {
        write(fileTreeRootsOf(value))
    }

    override suspend fun ReadContext.decode(): FileTreeInternal? =
        DefaultCompositeFileTree(
            read()!!.uncheckedCast<List<FileTreeSpec>>().map {
                FileTreeAdapter(directoryFileTreeFactory.create(it.first, it.second))
            }
        )

    private
    fun fileTreeRootsOf(value: FileTreeInternal): List<FileTreeSpec> {
        val visitor = FileTreeVisitor()
        value.visitStructure(visitor)
        return visitor.roots
    }

    private
    class FileTreeVisitor : FileCollectionStructureVisitor {

        internal
        var roots = mutableListOf<FileTreeSpec>()

        override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) = throw UnsupportedOperationException()

        override fun visitGenericFileTree(fileTree: FileTreeInternal) = throw UnsupportedOperationException()

        override fun visitFileTree(root: File, patterns: PatternSet, fileTree: FileTreeInternal) {
            roots.add(root to patterns)
        }

        override fun visitFileTreeBackedByFile(file: File, fileTree: FileTreeInternal) = throw UnsupportedOperationException()
    }
}
