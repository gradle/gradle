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

import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.FileCollectionBackFileTree
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.FilteredFileTree
import org.gradle.api.internal.file.archive.TarFileTree
import org.gradle.api.internal.file.archive.ZipFileTree
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.FilteredMinimalFileTree
import org.gradle.api.internal.file.collections.GeneratedSingletonFileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.decodePreservingIdentity
import org.gradle.instantexecution.serialization.encodePreservingIdentityOf
import org.gradle.instantexecution.serialization.readNonNull
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import java.io.File
import java.nio.file.Files


private
sealed class FileTreeSpec


private
class WrappedFileCollectionTreeSpec(val collection: AbstractFileCollection) : FileTreeSpec()


private
class DirectoryTreeSpec(val file: File, val patterns: PatternSet) : FileTreeSpec()


private
class ZipTreeSpec(val file: File) : FileTreeSpec()


private
class TarTreeSpec(val file: File) : FileTreeSpec()


private
class GeneratedTreeSpec(val file: File) : FileTreeSpec()


private
class DummyGeneratedFileTree(file: File, fileSystem: FileSystem) : GeneratedSingletonFileTree(
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
    },
    fileSystem
) {
    override fun getDisplayName(): String {
        return "generated ${file.name}"
    }
}


internal
class FileTreeCodec(
    private val fileCollectionFactory: FileCollectionFactory,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val fileOperations: FileOperations,
    private val fileSystem: FileSystem
) : Codec<FileTreeInternal> {

    override suspend fun WriteContext.encode(value: FileTreeInternal) {
        encodePreservingIdentityOf(value) {
            write(rootSpecOf(value))
        }
    }

    override suspend fun ReadContext.decode(): FileTreeInternal? {
        return decodePreservingIdentity { id ->
            val specs = readNonNull<List<FileTreeSpec>>()
            val fileTrees = specs.map {
                when (it) {
                    is WrappedFileCollectionTreeSpec -> it.collection.asFileTree as FileTreeInternal
                    is DirectoryTreeSpec -> fileCollectionFactory.treeOf(directoryFileTreeFactory.create(it.file, it.patterns))
                    is GeneratedTreeSpec -> fileCollectionFactory.treeOf(DummyGeneratedFileTree(it.file, fileSystem))
                    is ZipTreeSpec -> fileOperations.zipTree(it.file) as FileTreeInternal
                    is TarTreeSpec -> fileOperations.tarTree(it.file) as FileTreeInternal
                }
            }
            val tree = fileCollectionFactory.treeOf(fileTrees)
            isolate.identities.putInstance(id, tree)
            tree
        }
    }

    private
    fun rootSpecOf(value: FileTreeInternal): List<FileTreeSpec> {
        val visitor = FileTreeVisitor()
        value.visitStructure(visitor)
        return visitor.roots
    }

    private
    class FileTreeVisitor : FileCollectionStructureVisitor {
        var roots = mutableListOf<FileTreeSpec>()

        override fun startVisit(source: FileCollectionInternal.Source, fileCollection: FileCollectionInternal): Boolean {
            if (fileCollection is FileCollectionBackFileTree) {
                roots.add(WrappedFileCollectionTreeSpec(fileCollection.collection))
                return false
            } else if (fileCollection is FilteredFileTree && fileCollection.patterns.isEmpty) {
                // Optimize a common case, where fileCollection.asFileTree.matching(emptyPatterns) is used, eg in SourceTask and in CopySpec
                // Skip applying the filters to the tree
                fileCollection.tree.visitStructure(this)
                return false
            } else {
                return true
            }
        }

        override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) = throw UnsupportedOperationException()

        override fun visitGenericFileTree(fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) {
            // Visit the contents to create the mirror
            sourceTree.visit(object : FileVisitor {
                override fun visitFile(fileDetails: FileVisitDetails) {
                    fileDetails.file
                }

                override fun visitDir(dirDetails: FileVisitDetails) {
                    dirDetails.file
                }
            })
            val mirror = sourceTree.mirror
            roots.add(DirectoryTreeSpec(mirror.dir, mirror.patterns))
        }

        override fun visitFileTree(root: File, patterns: PatternSet, fileTree: FileTreeInternal) {
            if (fileTree is FileTreeAdapter) {
                val sourceTree = fileTree.tree
                if (sourceTree is GeneratedSingletonFileTree) {
                    // TODO - should generate the file into some persistent cache dir (eg the instant execution cache dir) and/or persist enough of the generator to recreate the file
                    // For example, for the Jar task persist the effective manifest (not all the stuff that produces it) and an action bean to generate the file from this
                    roots.add(GeneratedTreeSpec(sourceTree.file))
                    return
                }
            }
            roots.add(DirectoryTreeSpec(root, patterns))
        }

        override fun visitFileTreeBackedByFile(file: File, fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) {
            roots.add(toSpec(sourceTree))
        }

        private
        fun toSpec(tree: FileSystemMirroringFileTree): FileTreeSpec =
            when {
                // TODO - deal with tree that is not backed by a file
                tree is ZipFileTree && tree.backingFile != null -> ZipTreeSpec(tree.backingFile!!)
                tree is TarFileTree && tree.backingFile != null -> TarTreeSpec(tree.backingFile!!)
                // TODO - capture the patterns
                tree is FilteredMinimalFileTree -> toSpec(tree.tree)
                else -> throw UnsupportedOperationException()
            }
    }
}
