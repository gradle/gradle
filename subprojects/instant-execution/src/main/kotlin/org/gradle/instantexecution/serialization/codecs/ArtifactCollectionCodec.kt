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

import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.component.Artifact
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.configurations.ArtifactCollectionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFiles
import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readList
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.internal.DisplayName
import java.util.concurrent.Callable


internal
class ArtifactCollectionCodec(private val fileCollectionFactory: FileCollectionFactory) : Codec<ArtifactCollectionInternal> {

    override suspend fun WriteContext.encode(value: ArtifactCollectionInternal) {
        val visitor = CollectingArtifactVisitor()
        value.visitArtifacts(visitor)
        writeCollection(visitor.elements)
        writeCollection(visitor.failures)
    }

    override suspend fun ReadContext.decode(): ArtifactCollectionInternal {
        val elements = readList().uncheckedCast<List<Any>>()
        @Suppress("implicit_cast_to_any")
        val files = fileCollectionFactory.resolving(elements.map {
            when (it) {
                is ResolvedArtifactResult -> it.file
                is ConsumerProvidedVariantSpec -> Callable { it.node.transformedSubject.get().files }
                else -> throw IllegalArgumentException("Unexpected element $it in artifact collection")
            }
        })
        val failures = readList().uncheckedCast<List<Throwable>>()
        return FixedArtifactCollection(files, elements, failures)
    }
}


internal
class ConsumerProvidedVariantSpec(
    val node: TransformationNode,
    val variantDisplayName: DisplayName,
    val variantAttributes: ImmutableAttributes
)


internal
class CollectingArtifactVisitor : ArtifactVisitor {
    val elements = mutableListOf<Any>()
    val failures = mutableListOf<Throwable>()

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType =
        if (source is ConsumerProvidedVariantFiles && source.scheduledNodes.isNotEmpty()) {
            FileCollectionStructureVisitor.VisitType.NoContents
        } else {
            FileCollectionStructureVisitor.VisitType.Visit
        }

    override fun requireArtifactFiles(): Boolean {
        return true
    }

    override fun visitFailure(failure: Throwable) {
        failures.add(failure)
    }

    override fun visitArtifact(variantName: DisplayName, variantAttributes: AttributeContainer, artifact: ResolvableArtifact) {
        elements.add(DefaultResolvedArtifactResult(artifact.id, variantAttributes, variantName, Artifact::class.java, artifact.file))
    }

    override fun endVisitCollection(source: FileCollectionInternal.Source) {
        if (source is ConsumerProvidedVariantFiles && source.scheduledNodes.isNotEmpty()) {
            for (node in source.scheduledNodes) {
                elements.add(
                    ConsumerProvidedVariantSpec(
                        node,
                        source.targetVariantName,
                        source.targetVariantAttributes
                    )
                )
            }
        }
    }
}


internal
class FixedArtifactCollection(
    private val artifactFiles: FileCollection,
    private val elements: List<Any>,
    private val failures: List<Throwable>
) : ArtifactCollectionInternal {

    override fun getFailures() = failures

    override fun iterator(): MutableIterator<ResolvedArtifactResult> =
        artifacts.iterator()

    override fun getArtifactFiles() = artifactFiles

    override fun getArtifacts(): MutableSet<ResolvedArtifactResult> {
        val result = mutableSetOf<ResolvedArtifactResult>()
        for (element in elements) {
            when (element) {
                is ResolvedArtifactResult -> result.add(element)
                is ConsumerProvidedVariantSpec -> {
                    for (output in element.node.transformedSubject.get().files) {
                        val resolvedArtifact: ResolvableArtifact = element.node.inputArtifact.transformedTo(output)
                        result.add(DefaultResolvedArtifactResult(resolvedArtifact.id, element.variantDisplayName, element.variantAttributes, Artifact::class.java, output))
                    }
                    // Ignore
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
