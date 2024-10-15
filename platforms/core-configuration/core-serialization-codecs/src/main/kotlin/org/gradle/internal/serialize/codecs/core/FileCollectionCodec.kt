/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.artifacts.configurations.ResolutionBackedFileCollection
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetToFileCollectionFactory
import org.gradle.api.internal.artifacts.transform.TransformedArtifactSet
import org.gradle.api.internal.file.FileCollectionExecutionTimeValue
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.FilteredFileCollection
import org.gradle.api.internal.file.SubtractingFileCollection
import org.gradle.api.internal.file.collections.FailingFileCollection
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.internal.file.collections.ProviderBackedFileCollection
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.BeanSpec
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeCollection
import java.io.File
import kotlin.jvm.optionals.getOrNull


class FileCollectionCodec(
    private val fileCollectionFactory: FileCollectionFactory,
    private val artifactSetConverter: ArtifactSetToFileCollectionFactory
) : Codec<FileCollectionInternal> {

    override suspend fun WriteContext.encode(value: FileCollectionInternal) {
        encodePreservingIdentityOf(value) {
            encodeContents(value)
        }
    }

    suspend fun WriteContext.encodeContents(value: FileCollectionInternal) {
        val executionTimeValue = value.calculateExecutionTimeValue().getOrNull()
        if (executionTimeValue != null) {
            writeBoolean(true)
            write(executionTimeValue)
        } else {
            writeBoolean(false)
            encodeViaCollectingVisitor(value)
        }
    }

    private
    suspend fun WriteContext.encodeViaCollectingVisitor(value: FileCollectionInternal) {
        val visitor = CollectingVisitor()
        value.visitStructure(visitor)
        writeCollection(visitor.elements)
    }

    override suspend fun ReadContext.decode(): FileCollectionInternal {
        return decodePreservingIdentity { id ->
            val fileCollection = decodeContents()
            isolate.identities.putInstance(id, fileCollection)
            fileCollection
        }
    }

    suspend fun ReadContext.decodeContents(): FileCollectionInternal = if (readBoolean()) {
        readNonNull<FileCollectionExecutionTimeValue>().toFileCollection(fileCollectionFactory)
    } else {
        fileCollectionFactory.resolving(
            readList().map { element ->
                when (element) {
                    is File -> element
                    is SubtractingFileCollectionSpec -> element.left.minus(element.right)
                    is FilteredFileCollectionSpec -> element.collection.filter(element.filter)
                    is ProviderBackedFileCollectionSpec -> element.provider
                    is FileTree -> element
                    is ResolutionBackedFileCollectionSpec -> artifactSetConverter.asFileCollection(element.displayName, element.lenient, element.elements)
                    is BeanSpec -> element.bean
                    else -> throw IllegalArgumentException("Unexpected item $element in file collection contents")
                }
            }
        )
    }
}


private
class SubtractingFileCollectionSpec(val left: FileCollection, val right: FileCollection)


private
class FilteredFileCollectionSpec(val collection: FileCollection, val filter: Spec<in File>)


private
class ProviderBackedFileCollectionSpec(val provider: ProviderInternal<*>)


private
class ResolutionBackedFileCollectionSpec(val displayName: String, val lenient: Boolean, val elements: List<Any>)


private
abstract class AbstractVisitor : FileCollectionStructureVisitor {
    override fun visitFileTree(root: File, patterns: PatternSet, fileTree: FileTreeInternal) =
        unsupportedFileTree(fileTree)

    override fun visitFileTreeBackedByFile(file: File, fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) =
        unsupportedFileTree(fileTree)

    private
    fun unsupportedFileTree(fileTree: FileTreeInternal): Nothing =
        throw UnsupportedOperationException(
            "Unexpected file tree '$fileTree' of type '${fileTree.javaClass}' found while serializing a file collection."
        )
}


private
class CollectingVisitor : AbstractVisitor() {
    val elements: MutableSet<Any> = mutableSetOf()

    override fun startVisit(source: FileCollectionInternal.Source, fileCollection: FileCollectionInternal): Boolean =
        when (fileCollection) {
            is SubtractingFileCollection -> {
                // TODO - when left and right are both static then we should serialize the current contents of the collection
                elements.add(SubtractingFileCollectionSpec(fileCollection.left, fileCollection.right))
                false
            }

            is FilteredFileCollection -> {
                // TODO - when the collection is static then we should serialize the current contents of the collection
                elements.add(FilteredFileCollectionSpec(fileCollection.collection, fileCollection.filterSpec))
                false
            }

            is ProviderBackedFileCollection -> {
                // Guard against file collection created from a task provider such as `layout.files(compileJava)`
                // being referenced from a different task.
                val provider = fileCollection.provider
                if (provider !is TaskProvider<*>) {
                    elements.add(ProviderBackedFileCollectionSpec(provider))
                    false
                } else {
                    true
                }
            }

            is FileTreeInternal -> {
                elements.add(fileCollection)
                false
            }

            is FailingFileCollection -> {
                elements.add(BeanSpec(fileCollection))
                false
            }

            is ResolutionBackedFileCollection -> {
                val displayName = fileCollection.resolutionHost.displayName
                val lenient = fileCollection.isLenient
                val nestedVisitor = ResolutionContentsCollectingVisitor(displayName, lenient)
                fileCollection.visitStructure(nestedVisitor)
                nestedVisitor.addElements(elements)
                false
            }

            else -> {
                true
            }
        }

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
        require(source !is TransformedArtifactSet) { "Found artifact set $source but was not expecting an artifact set" }
        return FileCollectionStructureVisitor.VisitType.Visit
    }

    override fun visitCollection(source: FileCollectionInternal.Source, contents: MutableIterable<File>) {
        elements.addAll(contents)
    }
}


private
class ResolutionContentsCollectingVisitor(
    private val resolutionHostDisplayName: String,
    private val lenient: Boolean
) : AbstractVisitor() {
    var containsTransforms = false
    val elements: MutableSet<Any> = mutableSetOf()

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType =
        if (source is TransformedArtifactSet) {
            // Represents artifact transform outputs. Visit the source rather than the files
            // Transforms may have inputs or parameters that are task outputs or other changing files
            // When this is not the case, we should run the transform now and write the result.
            // However, currently it is not easy to determine whether this is the case so assume that all transforms
            // have changing inputs
            FileCollectionStructureVisitor.VisitType.NoContents
        } else {
            FileCollectionStructureVisitor.VisitType.Visit
        }

    override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) {
        if (source is TransformedArtifactSet) {
            containsTransforms = true
            elements.add(source)
        } else {
            elements.addAll(contents)
        }
    }

    fun addElements(target: MutableSet<Any>) {
        if (containsTransforms) {
            target.add(ResolutionBackedFileCollectionSpec(resolutionHostDisplayName, lenient, elements.toList()))
        } else {
            // Contains a fixed set of files - can throw away this instance
            target.addAll(elements)
        }
    }
}
