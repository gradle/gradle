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

import org.gradle.api.file.FileTree
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFiles
import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.tasks.util.PatternSet
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import java.io.File
import java.util.concurrent.Callable


internal
class FileCollectionCodec(
    private val fileCollectionFactory: FileCollectionFactory
) : Codec<FileCollectionInternal> {

    override suspend fun WriteContext.encode(value: FileCollectionInternal) {
        runCatching {
            val visitor = CollectingVisitor()
            value.visitStructure(visitor)
            visitor.elements
        }.apply {
            onSuccess { elements ->
                write(elements)
            }
            onFailure { ex ->
                write(BrokenValue(ex))
            }
        }
    }

    override suspend fun ReadContext.decode(): FileCollectionInternal {
        val contents = read()
        return if (contents is Collection<*>) {
            fileCollectionFactory.resolving(contents.map { element ->
                when (element) {
                    is File -> element
                    is TransformationNode -> Callable { element.transformedSubject.get().files }
                    is FileTree -> element
                    else -> throw IllegalArgumentException("Unexpected item $element in file collection contents")
                }
            })
        } else {
            fileCollectionFactory.create(ErrorFileSet(contents as BrokenValue))
        }
    }
}


private
class CollectingVisitor : FileCollectionStructureVisitor {
    val elements: MutableSet<Any> = mutableSetOf()

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
        return if (source is ConsumerProvidedVariantFiles && source.scheduledNodes.isNotEmpty()) {
            // Visit the source only for scheduled transforms
            FileCollectionStructureVisitor.VisitType.NoContents
        } else {
            FileCollectionStructureVisitor.VisitType.Visit
        }
    }

    override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) {
        if (source is ConsumerProvidedVariantFiles) {
            elements.addAll(source.scheduledNodes)
        } else {
            elements.addAll(contents)
        }
    }

    override fun visitGenericFileTree(fileTree: FileTreeInternal) {
        elements.addAll(fileTree)
    }

    override fun visitFileTree(root: File, patterns: PatternSet, fileTree: FileTreeInternal) {
        elements.add(fileTree)
    }

    override fun visitFileTreeBackedByFile(file: File, fileTree: FileTreeInternal) {
        elements.add(fileTree)
    }
}


private
class ErrorFileSet(private val error: BrokenValue) : MinimalFileSet {

    override fun getDisplayName() =
        "error-file-collection"

    override fun getFiles() =
        error.rethrow()
}
