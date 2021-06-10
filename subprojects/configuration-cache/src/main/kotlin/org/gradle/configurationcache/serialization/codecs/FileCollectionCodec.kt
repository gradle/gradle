/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetToFileCollectionFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalFileDependencyBackedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.transform.TransformedExternalArtifactSet
import org.gradle.api.internal.artifacts.transform.TransformedProjectArtifactSet
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.FilteredFileCollection
import org.gradle.api.internal.file.SubtractingFileCollection
import org.gradle.api.internal.file.collections.FailingFileCollection
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.file.collections.ProviderBackedFileCollection
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.util.PatternSet
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.decodePreservingIdentity
import org.gradle.configurationcache.serialization.encodePreservingIdentityOf
import org.gradle.configurationcache.serialization.logPropertyProblem
import java.io.File


internal
class FileCollectionCodec(
    private val fileCollectionFactory: FileCollectionFactory,
    private val artifactSetConverter: ArtifactSetToFileCollectionFactory
) : Codec<FileCollectionInternal> {

    override suspend fun WriteContext.encode(value: FileCollectionInternal) {
        encodePreservingIdentityOf(value) {
            runCatching {
                val visitor = CollectingVisitor()
                value.visitStructure(visitor)
                visitor.elements
            }.apply {
                onSuccess { elements ->
                    write(elements)
                }
                onFailure { ex ->
                    logPropertyProblem("serialize", ex) {
                        text("value ")
                        reference(value.toString())
                        text(" failed to visit file collection")
                    }
                    write(BrokenValue(ex))
                }
            }
        }
    }

    override suspend fun ReadContext.decode(): FileCollectionInternal {
        return decodePreservingIdentity { id ->
            val contents = read()
            val collection = if (contents is Collection<*>) {
                fileCollectionFactory.resolving(
                    contents.map { element ->
                        when (element) {
                            is File -> element
                            is SubtractingFileCollectionSpec -> element.left.minus(element.right)
                            is FilteredFileCollectionSpec -> element.collection.filter(element.filter)
                            is ProviderBackedFileCollectionSpec -> element.provider
                            is FileTree -> element
                            is ResolvedArtifactSet -> artifactSetConverter.asFileCollection(element)
                            is BeanSpec -> element.bean
                            else -> throw IllegalArgumentException("Unexpected item $element in file collection contents")
                        }
                    }
                )
            } else {
                fileCollectionFactory.create(ErrorFileSet(contents as BrokenValue))
            }
            isolate.identities.putInstance(id, collection)
            collection
        }
    }
}


private
class SubtractingFileCollectionSpec(val left: FileCollection, val right: FileCollection)


private
class FilteredFileCollectionSpec(val collection: FileCollection, val filter: Spec<in File>)


private
class ProviderBackedFileCollectionSpec(val provider: ProviderInternal<*>)


private
class CollectingVisitor : FileCollectionStructureVisitor {
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
            else -> {
                true
            }
        }

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType =
        if (source is TransformedProjectArtifactSet || source is LocalFileDependencyBackedArtifactSet || source is TransformedExternalArtifactSet) {
            // Represents artifact transform outputs. Visit the source rather than the files
            // Transforms may have inputs or parameters that are task outputs or other changing files
            // When this is not the case, we should run the transform now and write the result.
            // However, currently it is not easy to determine whether or not this is the case so assume that all transforms
            // have changing inputs
            FileCollectionStructureVisitor.VisitType.NoContents
        } else {
            FileCollectionStructureVisitor.VisitType.Visit
        }

    override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) {
        when (source) {
            is TransformedProjectArtifactSet -> {
                elements.add(source)
            }
            is LocalFileDependencyBackedArtifactSet -> {
                elements.add(source)
            }
            is TransformedExternalArtifactSet -> {
                elements.add(source)
            }
            else -> {
                elements.addAll(contents)
            }
        }
    }

    override fun visitGenericFileTree(fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) =
        unsupportedFileTree(fileTree)

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
class ErrorFileSet(private val error: BrokenValue) : MinimalFileSet {

    override fun getDisplayName() =
        "error-file-collection"

    override fun getFiles() =
        error.rethrow()
}
