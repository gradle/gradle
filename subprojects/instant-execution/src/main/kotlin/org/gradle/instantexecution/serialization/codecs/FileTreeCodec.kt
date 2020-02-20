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

import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.DefaultCompositeFileTree
import org.gradle.api.internal.file.FileCollectionBackFileTree
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.FilteredFileTree
import org.gradle.api.internal.file.archive.TarFileTree
import org.gradle.api.internal.file.archive.ZipFileTree
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.GeneratedSingletonFileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.decodePreservingIdentity
import org.gradle.instantexecution.serialization.encodePreservingIdentityOf
import org.gradle.instantexecution.serialization.ownerService
import org.gradle.instantexecution.serialization.readNonNull
import org.gradle.internal.Factory
import java.io.File
import java.nio.file.Files


private
sealed class FileTreeRootSpec


private
class CompositeFileTreeRootSpec(val roots: List<FileTreeSpec>) : FileTreeRootSpec()


private
class WrappedFileCollectionTreeRootSpec(val collection: AbstractFileCollection) : FileTreeRootSpec()


private
sealed class FileTreeSpec


private
class DirectoryTreeSpec(val file: File, val patterns: PatternSet) : FileTreeSpec()


private
class ZipTreeSpec(val file: File) : FileTreeSpec()


private
class TarTreeSpec(val file: File) : FileTreeSpec()


private
class GeneratedTreeSpec(val file: File) : FileTreeSpec()


private
class DummyGeneratedFileTree(file: File) : GeneratedSingletonFileTree(
    { file.parentFile },
    file.name,
    {},
    { outStr ->
        if (!file.exists()) {
            // Generate some dummy content if the file does not exist
            // TODO - rework this so that content is generated into some fixed workspace location and reused from there
            file.parentFile.mkdirs()
            file.writeText("")
        }
        Files.copy(file.toPath(), outStr)
    }
) {
    override fun getDisplayName(): String {
        return "generated ${file.name}"
    }
}


internal
class FileTreeCodec(
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val patternSetFactory: Factory<PatternSet>
) : Codec<FileTreeInternal> {

    override suspend fun WriteContext.encode(value: FileTreeInternal) {
        encodePreservingIdentityOf(value) {
            write(rootSpecOf(value))
        }
    }

    override suspend fun ReadContext.decode(): FileTreeInternal? {
        return decodePreservingIdentity { id ->
            val spec = readNonNull<FileTreeRootSpec>()
            val tree = when (spec) {
                is CompositeFileTreeRootSpec ->
                    DefaultCompositeFileTree(
                        spec.roots.map {
                            when (it) {
                                is DirectoryTreeSpec -> FileTreeAdapter(directoryFileTreeFactory.create(it.file, it.patterns))
                                is GeneratedTreeSpec -> FileTreeAdapter(DummyGeneratedFileTree(it.file))
                                is ZipTreeSpec -> ownerService<FileOperations>().zipTree(it.file) as FileTreeInternal
                                is TarTreeSpec -> ownerService<FileOperations>().tarTree(it.file) as FileTreeInternal
                            }
                        }
                    )
                is WrappedFileCollectionTreeRootSpec -> FileCollectionBackFileTree(patternSetFactory, spec.collection)
            }
            isolate.identities.putInstance(id, tree)
            tree
        }
    }

    private
    fun rootSpecOf(value: FileTreeInternal): FileTreeRootSpec {
        // Optimize a common case, where fileCollection.asFileTree.matching(emptyPatterns) is used, eg in SourceTask and in CopySpec
        // TODO - it would be better to apply this while visiting the tree structure, so that the same short circuiting is applied everywhere
        //   Would needs some rework of the visiting interfaces
        val effectiveTree = if (value is FilteredFileTree && value.patterns.isEmpty) {
            // No filters are applied, so discard the filtering tree
            value.tree
        } else {
            value
        }
        if (effectiveTree is FileCollectionBackFileTree) {
            return WrappedFileCollectionTreeRootSpec(effectiveTree.collection)
        }

        val visitor = FileTreeVisitor()
        value.visitStructure(visitor)
        return CompositeFileTreeRootSpec(visitor.roots)
    }

    private
    class FileTreeVisitor : FileCollectionStructureVisitor {

        var roots = mutableListOf<FileTreeSpec>()

        override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) = throw UnsupportedOperationException()

        override fun visitGenericFileTree(fileTree: FileTreeInternal) = throw UnsupportedOperationException()

        override fun visitFileTree(root: File, patterns: PatternSet, fileTree: FileTreeInternal) {
            if (fileTree is FileTreeAdapter) {
                val tree = fileTree.tree
                if (tree is GeneratedSingletonFileTree) {
                    // TODO - should generate the file into some persistent cache dir (eg the instant execution cache dir) and/or persist enough of the generator to recreate the file
                    // For example, for the Jar task persist the effective manifest (not all the stuff that produces it) and an action bean to generate the file from this
                    roots.add(GeneratedTreeSpec(tree.file))
                    return
                }
            }
            roots.add(DirectoryTreeSpec(root, patterns))
        }

        override fun visitFileTreeBackedByFile(file: File, fileTree: FileTreeInternal) {
            if (fileTree is FileTreeAdapter) {
                val tree = fileTree.tree
                if (tree is ZipFileTree) {
                    roots.add(ZipTreeSpec(tree.backingFile!!))
                    return
                } else if (tree is TarFileTree) {
                    roots.add(TarTreeSpec(tree.backingFile!!))
                    return
                }
            }
            throw UnsupportedOperationException()
        }
    }
}
