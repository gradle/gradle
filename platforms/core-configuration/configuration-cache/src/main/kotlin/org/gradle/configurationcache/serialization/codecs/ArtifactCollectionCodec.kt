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
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.configurations.ArtifactCollectionInternal
import org.gradle.api.internal.artifacts.configurations.DefaultArtifactCollection
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetToFileCollectionFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.transform.TransformedArtifactSet
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.model.CalculatedValueContainerFactory
import java.io.File


internal
class ArtifactCollectionCodec(
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory,
    private val artifactSetConverter: ArtifactSetToFileCollectionFactory
) : Codec<ArtifactCollectionInternal> {

    override suspend fun WriteContext.encode(value: ArtifactCollectionInternal) {
        val visitor = CollectingArtifactVisitor()
        value.visitArtifacts(visitor)
        writeString(value.resolutionHost.displayName)
        writeBoolean(value.isLenient)
        writeCollection(visitor.elements)
    }

    override suspend fun ReadContext.decode(): ArtifactCollectionInternal {
        val displayName = readString()
        val lenient = readBoolean()
        val elements = readList().uncheckedCast<List<Any>>()

        val files = artifactSetConverter.asFileCollection(displayName, lenient,
            elements.map { element ->
                when (element) {
                    is Throwable -> artifactSetConverter.asResolvedArtifactSet(element)
                    is FixedFileArtifactSpec -> artifactSetConverter.asResolvedArtifactSet(element.id, element.variantAttributes, element.capabilities, element.variantDisplayName, element.file)
                    is ResolvedArtifactSet -> element
                    else -> throw IllegalArgumentException("Unexpected element $element in artifact collection")
                }
            }
        )
        return DefaultArtifactCollection(files, lenient, artifactSetConverter.resolutionHost(displayName), calculatedValueContainerFactory)
    }
}


private
data class FixedFileArtifactSpec(
    val id: ComponentArtifactIdentifier,
    val variantAttributes: AttributeContainer,
    val capabilities: ImmutableCapabilities,
    val variantDisplayName: DisplayName,
    val file: File
)


private
class CollectingArtifactVisitor : ArtifactVisitor {
    val elements = mutableListOf<Any>()
    val artifacts = mutableSetOf<ResolvableArtifact>()

    override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
        return if (source is TransformedArtifactSet) {
            // Represents artifact transform outputs. Visit the source rather than the files
            // Transforms may have inputs or parameters that are task outputs or other changing files
            // When this is not the case, we should run the transform now and write the result.
            // However, currently it is not easy to determine whether this is the case so assume that all transforms
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
        elements.add(failure)
    }

    override fun visitArtifact(variantName: DisplayName, variantAttributes: AttributeContainer, capabilities: ImmutableCapabilities, artifact: ResolvableArtifact) {
        if (artifacts.add(artifact)) {
            elements.add(FixedFileArtifactSpec(artifact.id, variantAttributes, capabilities, variantName, artifact.file))
        }
    }

    override fun endVisitCollection(source: FileCollectionInternal.Source) {
        if (source is TransformedArtifactSet) {
            elements.add(source)
        }
    }
}
