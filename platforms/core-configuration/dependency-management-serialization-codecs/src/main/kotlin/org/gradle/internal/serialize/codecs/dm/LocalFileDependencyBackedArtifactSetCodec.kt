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

package org.gradle.internal.serialize.codecs.dm

import com.google.common.collect.ImmutableMap
import org.gradle.api.Action
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.internal.artifacts.TransformRegistration
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultLocalFileDependencyBackedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalFileDependencyBackedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector
import org.gradle.api.internal.artifacts.transform.TransformChain
import org.gradle.api.internal.artifacts.transform.TransformStep
import org.gradle.api.internal.artifacts.transform.VariantDefinition
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.specs.Spec
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingSharedIdentity
import org.gradle.internal.serialize.graph.encodePreservingSharedIdentityOf
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.writeCollection


class LocalFileDependencyBackedArtifactSetCodec(
    private val attributesFactory: AttributesFactory,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : Codec<LocalFileDependencyBackedArtifactSet> {
    override suspend fun WriteContext.encode(value: LocalFileDependencyBackedArtifactSet) {
        when (value) {
            is DefaultLocalFileDependencyBackedArtifactSet -> encode(value)
            else -> throw UnsupportedOperationException("Cannot encode ${value.javaClass}")
        }
    }

    private
    suspend fun WriteContext.encode(value: DefaultLocalFileDependencyBackedArtifactSet) {
        // TODO - When the set of files is fixed (eg `gradleApi()` or some hard-coded list of files):
        //   - calculate the attributes for each of the files eagerly rather than writing the mappings
        //   - when the selector would not apply a transform, then write only the files and nothing else
        //   - otherwise, write only the transform and attributes for each file rather than writing the transform registry
        val requestedAttributes = !value.requestAttributes.isEmpty
        writeBoolean(requestedAttributes)
        writeBoolean(value.allowNoMatchingVariants)
        write(value.dependencyMetadata.componentId)
        write(value.dependencyMetadata.files)
        write(value.componentFilter)

        // Write the file extension -> attributes mappings
        // TODO - move this to an encoder
        val registry = value.artifactTypeRegistry
        encodePreservingSharedIdentityOf(registry) {
            writeCollection(registry.mappings.entries) {
                writeString(it.key)
                write(it.value)
            }
            write(registry.defaultArtifactAttributes)
        }

        if (requestedAttributes) {
            // Write the file extension -> transform mappings
            // This currently uses a dummy set of variants to calculate the mappings.
            // Do not write this if it will not be used
            // TODO - simplify extracting the mappings
            // TODO - deduplicate this data, as the mapping is project scoped and almost always the same across all projects of a given type
            val artifactType = value.requestAttributes.getAttribute(ARTIFACT_TYPE_ATTRIBUTE)
            writeBoolean(artifactType != null)
            val mappings = mutableMapOf<ImmutableAttributes, MappingSpec>()
            value.artifactTypeRegistry.visitArtifactTypeAttributes(value.transformRegistry.registrations) { sourceAttributes ->
                val recordingSet = RecordingVariantSet(value.dependencyMetadata.componentId, value.dependencyMetadata.files, sourceAttributes)
                val selected = value.variantSelector.select(recordingSet, value.requestAttributes, true)
                if (selected == ResolvedArtifactSet.EMPTY) {
                    // Don't need to record the mapping
                } else if (recordingSet.targetAttributes != null) {
                    mappings[sourceAttributes] = TransformMapping(recordingSet.targetAttributes!!, recordingSet.transformChain!!)
                } else {
                    mappings[sourceAttributes] = IdentityMapping
                }
            }
            if (artifactType != null) {
                val requestedArtifactType = attributesFactory.of(ARTIFACT_TYPE_ATTRIBUTE, artifactType)
                mappings[requestedArtifactType] = IdentityMapping
            }
            write(mappings)
        }
    }

    override suspend fun ReadContext.decode(): LocalFileDependencyBackedArtifactSet {
        val requestedAttributes = readBoolean()
        val allowNoMatchingVariants = readBoolean()
        val componentId = read() as ComponentIdentifier?
        val files = readNonNull<FileCollectionInternal>()
        val filter = readNonNull<Spec<ComponentIdentifier>>()

        val artifactTypeRegistry = decodePreservingSharedIdentity {
            val mappings = readList {
                val name = readString()
                val attributes = readNonNull<ImmutableAttributes>()
                name to attributes
            }.toMap()
            val defaultArtifactAttributes = readNonNull<ImmutableAttributes>()
            ImmutableArtifactTypeRegistry(attributesFactory, ImmutableMap.copyOf(mappings), defaultArtifactAttributes)
        }

        val selector = if (!requestedAttributes) {
            NoTransformsArtifactVariantSelector()
        } else {
            val matchingOnArtifactFormat = readBoolean()
            val transforms = readNonNull<Map<ImmutableAttributes, MappingSpec>>()
            FixedArtifactVariantSelector(matchingOnArtifactFormat, transforms)
        }
        return DeserializedLocalFileDependencyArtifactSet(
            FixedFileMetadata(componentId, files),
            filter,
            selector,
            artifactTypeRegistry,
            calculatedValueContainerFactory,
            allowNoMatchingVariants
        )
    }
}


// Deserialized counterpart of DefaultLocalFileDependencyBackedArtifactSet.
// Stores less state than the original, since we perform selection for each possible extension at serialization time
// This data is encoded in the variantSelector, meaning we no longer need to store the request attributes
private
class DeserializedLocalFileDependencyArtifactSet(
    dependencyMetadata: LocalFileDependencyMetadata,
    componentFilter: Spec<ComponentIdentifier>,
    variantSelector: ArtifactVariantSelector,
    artifactTypeRegistry: ImmutableArtifactTypeRegistry,
    calculatedValueContainerFactory: CalculatedValueContainerFactory,
    allowNoMatchingVariants: Boolean
) : LocalFileDependencyBackedArtifactSet(
    dependencyMetadata,
    componentFilter,
    variantSelector,
    artifactTypeRegistry,
    calculatedValueContainerFactory,
    allowNoMatchingVariants
) {
    // These attributes are ignored by the fixed artifact variant selectors
    override fun getRequestAttributes(): ImmutableAttributes = ImmutableAttributes.EMPTY
}


private
class RecordingVariantSet(
    private val componentId: ComponentIdentifier?,
    private val source: FileCollectionInternal,
    private val attributes: ImmutableAttributes
) : ResolvedVariantSet, ResolvedVariant, ResolvedArtifactSet {
    var targetAttributes: ImmutableAttributes? = null
    var transformChain: TransformChain? = null

    override fun asDescribable(): DisplayName {
        return Describables.of(source)
    }

    override fun getProducerSchema(): ImmutableAttributesSchema {
        return ImmutableAttributesSchema.EMPTY
    }

    override fun getCandidates(): List<ResolvedVariant> {
        return listOf(this)
    }

    override fun getOverriddenAttributes(): ImmutableAttributes {
        return ImmutableAttributes.EMPTY
    }

    override fun getIdentifier(): VariantResolveMetadata.Identifier? {
        return null
    }

    override fun getAttributes(): ImmutableAttributes {
        return attributes
    }

    override fun getCapabilities(): ImmutableCapabilities {
        return ImmutableCapabilities.EMPTY
    }

    override fun getComponentIdentifier(): ComponentIdentifier? {
        return componentId
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        throw UnsupportedOperationException("Should not be called")
    }

    override fun visit(visitor: ResolvedArtifactSet.Visitor) {
        throw UnsupportedOperationException("Should not be called")
    }

    override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) {
        throw UnsupportedOperationException("Should not be called")
    }

    override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
        throw UnsupportedOperationException("Should not be called")
    }

    override fun getArtifacts(): ResolvedArtifactSet {
        return this
    }

    override fun transformCandidate(sourceVariant: ResolvedVariant, variantDefinition: VariantDefinition): ResolvedArtifactSet {
        this.transformChain = variantDefinition.transformChain
        this.targetAttributes = variantDefinition.targetAttributes
        return sourceVariant.artifacts
    }
}


private
sealed class MappingSpec


private
class TransformMapping(private val targetAttributes: ImmutableAttributes, private val transformChain: TransformChain) : MappingSpec(), VariantDefinition {
    override fun getTargetAttributes(): ImmutableAttributes {
        return targetAttributes
    }

    override fun getTransformChain(): TransformChain {
        return transformChain
    }

    override fun getTransformStep(): TransformStep {
        throw UnsupportedOperationException()
    }

    override fun getPrevious(): VariantDefinition? {
        throw UnsupportedOperationException()
    }
}


private
object IdentityMapping : MappingSpec()


private
class FixedArtifactVariantSelector(
    private val matchingOnArtifactFormat: Boolean,
    private val transforms: Map<ImmutableAttributes, MappingSpec>
) : ArtifactVariantSelector {
    override fun select(
        candidates: ResolvedVariantSet,
        requestAttributes: ImmutableAttributes,
        allowNoMatchingVariants: Boolean
    ): ResolvedArtifactSet {
        require(candidates.candidates.size == 1)
        val variant = candidates.candidates.first()
        return when (val spec = transforms[variant.attributes.asImmutable()]) {
            null -> {
                // no mapping for extension, so it can be discarded
                if (matchingOnArtifactFormat) {
                    ResolvedArtifactSet.EMPTY
                } else {
                    variant.artifacts
                }
            }

            is IdentityMapping -> variant.artifacts
            is TransformMapping -> candidates.transformCandidate(variant, spec)
        }
    }
}


private
class NoTransformsArtifactVariantSelector : ArtifactVariantSelector {
    override fun select(
        candidates: ResolvedVariantSet,
        requestAttributes: ImmutableAttributes,
        allowNoMatchingVariants: Boolean
    ): ResolvedArtifactSet {
        require(candidates.candidates.size == 1)
        return candidates.candidates.first().artifacts
    }
}


private
class FixedFileMetadata(
    private val compId: ComponentIdentifier?,
    private val source: FileCollectionInternal
) : LocalFileDependencyMetadata {
    override fun getComponentId(): ComponentIdentifier? {
        return compId
    }

    override fun getFiles(): FileCollectionInternal {
        return source
    }

    override fun getSource(): FileCollectionDependency {
        throw UnsupportedOperationException("Should not be called")
    }
}


private
object EmptyVariantTransformRegistry : VariantTransformRegistry {
    override fun <T : TransformParameters?> registerTransform(name: String?, actionType: Class<out TransformAction<T>>, registrationAction: Action<in org.gradle.api.artifacts.transform.TransformSpec<T>>) {
        throw UnsupportedOperationException("Should not be called")
    }

    override fun getRegistrations(): MutableList<TransformRegistration> {
        throw UnsupportedOperationException("Should not be called")
    }
}
