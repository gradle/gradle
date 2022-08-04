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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.Artifact
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.configurations.ArtifactCollectionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetToFileCollectionFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalFileDependencyBackedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult
import org.gradle.api.internal.artifacts.transform.TransformedExternalArtifactSet
import org.gradle.api.internal.artifacts.transform.TransformedProjectArtifactSet
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Provider
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.DisplayName
import java.io.File


internal
class ArtifactCollectionCodec(
    private val fileCollectionFactory: FileCollectionFactory,
    private val artifactSetConverter: ArtifactSetToFileCollectionFactory
) : Codec<ArtifactCollectionInternal> {

    override suspend fun WriteContext.encode(value: ArtifactCollectionInternal) {
        val visitor = CollectingArtifactVisitor()
        value.visitArtifacts(visitor)
        writeCollection(visitor.elements)
        writeCollection(visitor.failures)
    }

    override suspend fun ReadContext.decode(): ArtifactCollectionInternal {
        val elements = readList().uncheckedCast<List<Any>>()

        @Suppress("implicit_cast_to_any")
        val files = fileCollectionFactory.resolving(
            elements.map { element ->
                when (element) {
                    is FixedFileArtifactSpec -> element.file
                    is ResolvedArtifactSet -> artifactSetConverter.asFileCollection(element)
                    else -> throw IllegalArgumentException("Unexpected element $element in artifact collection")
                }
            }
        )
        val failures = readList().uncheckedCast<List<Throwable>>()
        return FixedArtifactCollection(files, elements, failures, artifactSetConverter)
    }
}


private
class FixedFileArtifactSpec(
    val id: ComponentArtifactIdentifier,
    val variantAttributes: AttributeContainer,
    val capabilities: List<Capability>,
    val variantDisplayName: DisplayName,
    val file: File
)


private
class CollectingArtifactVisitor : ArtifactVisitor {
    val elements = mutableListOf<Any>()
    val failures = mutableListOf<Throwable>()

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
        return if (source is TransformedProjectArtifactSet || source is LocalFileDependencyBackedArtifactSet || source is TransformedExternalArtifactSet) {
            // Represents artifact transform outputs. Visit the source rather than the files
            // Transforms may have inputs or parameters that are task outputs or other changing files
            // When this is not the case, we should run the transform now and write the result.
            // However, currently it is not easy to determine whether or not this is the case so assume that all transforms
            // have changing inputs
            FileCollectionStructureVisitor.VisitType.NoContents
        } else {
            FileCollectionStructureVisitor.VisitType.Visit
        }
    }

    override fun requireArtifactFiles(): Boolean {
        return true
    }

    override fun visitFailure(failure: Throwable) {
        failures.add(failure)
    }

    override fun visitArtifact(variantName: DisplayName, variantAttributes: AttributeContainer, capabilities: List<Capability>, artifact: ResolvableArtifact) {
        elements.add(FixedFileArtifactSpec(artifact.id, variantAttributes, capabilities, variantName, artifact.file))
    }

    override fun endVisitCollection(source: FileCollectionInternal.Source) {
        if (source is TransformedProjectArtifactSet) {
            elements.add(source)
        } else if (source is LocalFileDependencyBackedArtifactSet) {
            elements.add(source)
        } else if (source is TransformedExternalArtifactSet) {
            elements.add(source)
        }
    }
}


private
class FixedArtifactCollection(
    private val artifactFiles: FileCollection,
    private val elements: List<Any>,
    private val failures: List<Throwable>,
    private val artifactSetConverter: ArtifactSetToFileCollectionFactory
) : ArtifactCollectionInternal {

    private
    var artifactResults: MutableSet<ResolvedArtifactResult>? = null

    override fun getFailures() =
        failures

    override fun iterator(): MutableIterator<ResolvedArtifactResult> =
        artifacts.iterator()

    override fun getArtifactFiles() =
        artifactFiles

    @Synchronized
    override fun getArtifacts(): MutableSet<ResolvedArtifactResult> =
        artifactResults ?: resolve().also {
            artifactResults = it
        }

    override fun getResolvedArtifacts(): Provider<Set<ResolvedArtifactResult>> =
        Providers.of(artifacts)

    private
    fun resolve(): MutableSet<ResolvedArtifactResult> {
        val result = mutableSetOf<ResolvedArtifactResult>()
        for (element in elements) {
            when (element) {
                is FixedFileArtifactSpec -> {
                    result.add(
                        DefaultResolvedArtifactResult(
                            element.id,
                            element.variantAttributes,
                            element.capabilities,
                            element.variantDisplayName,
                            Artifact::class.java,
                            element.file
                        )
                    )
                }
                is ResolvedArtifactSet -> {
                    result.addAll(artifactSetConverter.asResolvedArtifacts(element))
                }
                else -> throw IllegalArgumentException("Unexpected element $element in artifact collection")
            }
        }
        return result
    }

    override fun visitArtifacts(visitor: ArtifactVisitor) {
        throw UnsupportedOperationException()
    }
}
