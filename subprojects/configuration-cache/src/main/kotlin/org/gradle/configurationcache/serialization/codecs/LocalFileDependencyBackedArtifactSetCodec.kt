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

import org.gradle.api.Action
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.CapabilitiesMetadata
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.ArtifactTransformRegistration
import org.gradle.api.internal.artifacts.VariantTransformRegistry
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalFileDependencyBackedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.artifacts.transform.ArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.DefaultArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependencies
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver
import org.gradle.api.internal.artifacts.transform.TransformationChain
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory
import org.gradle.api.internal.artifacts.transform.VariantDefinition
import org.gradle.api.internal.artifacts.transform.VariantSelector
import org.gradle.api.internal.artifacts.type.DefaultArtifactTypeRegistry
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.specs.Spec
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.decodePreservingSharedIdentity
import org.gradle.configurationcache.serialization.encodePreservingSharedIdentityOf
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.Try
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity


class LocalFileDependencyBackedArtifactSetCodec(
    private val instantiator: Instantiator,
    private val attributesFactory: ImmutableAttributesFactory,
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
) : Codec<LocalFileDependencyBackedArtifactSet> {
    override suspend fun WriteContext.encode(value: LocalFileDependencyBackedArtifactSet) {
        // TODO - When the set of files is fixed (eg `gradleApi()` or some hard-coded list of files):
        //   - calculate the attributes for each of the files eagerly rather than writing the mappings
        //   - when the selector would not apply a transform, then write only the files and nothing else
        //   - otherwise, write only the transform and attributes for each file rather than writing the transform registry
        val requestedAttributes = !value.selector.requestedAttributes.isEmpty
        writeBoolean(requestedAttributes)
        write(value.dependencyMetadata.componentId)
        write(value.dependencyMetadata.files)
        write(value.componentFilter)

        // Write the file extension -> attributes mappings
        // TODO - move this to an encoder
        encodePreservingSharedIdentityOf(value.artifactTypeRegistry) {
            val mappings = value.artifactTypeRegistry.create()!!
            writeCollection(mappings) {
                writeString(it.name)
                write(it.attributes)
            }
        }

        if (requestedAttributes) {
            // Write the file extension -> transformation mappings
            // This currently uses a dummy set of variants to calculate the mappings.
            // Do not write this if it will not be used
            // TODO - simplify extracting the mappings
            // TODO - deduplicate this data, as the mapping is project scoped and almost always the same across all projects of a given type
            val artifactType = value.selector.requestedAttributes.getAttribute(ARTIFACT_TYPE_ATTRIBUTE)
            writeBoolean(artifactType != null)
            val mappings = mutableMapOf<ImmutableAttributes, MappingSpec>()
            value.artifactTypeRegistry.visitArtifactTypes { sourceAttributes ->
                val recordingSet = RecordingVariantSet(value.dependencyMetadata.files, sourceAttributes)
                val selected = value.selector.maybeSelect(recordingSet, recordingSet)
                if (selected == ResolvedArtifactSet.EMPTY) {
                    // Don't need to record the mapping
                } else if (recordingSet.targetAttributes != null) {
                    mappings[sourceAttributes] = TransformMapping(recordingSet.targetAttributes!!, recordingSet.transformationChain!!)
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
        val componentId = read() as ComponentIdentifier?
        val files = readNonNull<FileCollectionInternal>()
        val filter = readNonNull<Spec<ComponentIdentifier>>()

        // TODO - use an immutable registry implementation
        val artifactTypeRegistry = decodePreservingSharedIdentity {
            val registry = DefaultArtifactTypeRegistry(instantiator, attributesFactory, CollectionCallbackActionDecorator.NOOP, EmptyVariantTransformRegistry)
            val mappings = registry.create()!!
            readCollection {
                val name = readString()
                val attributes = readNonNull<AttributeContainer>()
                val mapping = mappings.create(name).attributes
                @Suppress("UNCHECKED_CAST")
                for (attribute in attributes.keySet() as Set<Attribute<Any>>) {
                    mapping.attribute(attribute, attributes.getAttribute(attribute) as Any)
                }
            }
            registry
        }

        val selector = if (!requestedAttributes) {
            NoTransformsSelector()
        } else {
            val matchingOnArtifactFormat = readBoolean()
            val transforms = readNonNull<Map<ImmutableAttributes, MappingSpec>>()
            FixedVariantSelector(matchingOnArtifactFormat, transforms, NoOpTransformedVariantFactory)
        }
        return LocalFileDependencyBackedArtifactSet(FixedFileMetadata(componentId, files), filter, selector, artifactTypeRegistry, calculatedValueContainerFactory)
    }
}


private
class RecordingVariantSet(
    private val source: FileCollectionInternal,
    private val attributes: ImmutableAttributes
) : ResolvedVariantSet, ResolvedVariant, VariantSelector.Factory, ResolvedArtifactSet {
    var targetAttributes: ImmutableAttributes? = null
    var transformationChain: TransformationChain? = null

    override fun asDescribable(): DisplayName {
        return Describables.of(source)
    }

    override fun getSchema(): AttributesSchemaInternal {
        return EmptySchema.INSTANCE
    }

    override fun getVariants(): Set<ResolvedVariant> {
        return setOf(this)
    }

    override fun getAllVariants(): Set<ResolvedVariant> {
        return setOf(this)
    }

    override fun getOverriddenAttributes(): ImmutableAttributes {
        return ImmutableAttributes.EMPTY
    }

    override fun getIdentifier(): VariantResolveMetadata.Identifier? {
        return null
    }

    override fun getAttributes(): AttributeContainerInternal {
        return attributes
    }

    override fun getCapabilities(): CapabilitiesMetadata {
        return ImmutableCapabilities.EMPTY
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

    override fun asTransformed(
        sourceVariant: ResolvedVariant,
        variantDefinition: VariantDefinition,
        dependenciesResolver: ExtraExecutionGraphDependenciesResolverFactory,
        transformedVariantFactory: TransformedVariantFactory
    ): ResolvedArtifactSet {
        this.transformationChain = variantDefinition.transformationChain
        this.targetAttributes = variantDefinition.targetAttributes
        return sourceVariant.artifacts
    }
}


private
sealed class MappingSpec


private
class TransformMapping(private val targetAttributes: ImmutableAttributes, private val transformationChain: TransformationChain) : MappingSpec(), VariantDefinition {
    override fun getTargetAttributes(): ImmutableAttributes {
        return targetAttributes
    }

    override fun getTransformationChain(): TransformationChain {
        return transformationChain
    }

    override fun getTransformationStep(): TransformationStep {
        throw UnsupportedOperationException()
    }

    override fun getPrevious(): VariantDefinition? {
        throw UnsupportedOperationException()
    }
}


private
object IdentityMapping : MappingSpec()


private
class FixedVariantSelector(
    private val matchingOnArtifactFormat: Boolean,
    private val transforms: Map<ImmutableAttributes, MappingSpec>,
    private val transformedVariantFactory: TransformedVariantFactory
) : VariantSelector {
    override fun getRequestedAttributes() = throw UnsupportedOperationException("Should not be called")

    override fun select(candidates: ResolvedVariantSet, factory: VariantSelector.Factory): ResolvedArtifactSet {
        require(candidates.variants.size == 1)
        val variant = candidates.variants.first()
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
            is TransformMapping -> factory.asTransformed(variant, spec, EmptyDependenciesResolverFactory(), transformedVariantFactory)
        }
    }
}


private
class NoTransformsSelector : VariantSelector {
    override fun getRequestedAttributes() = throw UnsupportedOperationException("Should not be called")

    override fun select(candidates: ResolvedVariantSet, factory: VariantSelector.Factory): ResolvedArtifactSet {
        require(candidates.variants.size == 1)
        return candidates.variants.first().artifacts
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
class EmptyDependenciesResolverFactory : ExtraExecutionGraphDependenciesResolverFactory, TransformUpstreamDependenciesResolver, TransformUpstreamDependencies {

    override fun getConfigurationIdentity(): ConfigurationIdentity? {
        return null
    }

    override fun create(componentIdentifier: ComponentIdentifier, transformationChain: TransformationChain): TransformUpstreamDependenciesResolver {
        return this
    }

    override fun dependenciesFor(transformationStep: TransformationStep): TransformUpstreamDependencies {
        return this
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        throw UnsupportedOperationException("Should not be called")
    }

    override fun selectedArtifacts(): FileCollection {
        throw UnsupportedOperationException("Should not be called")
    }

    override fun finalizeIfNotAlready() {
    }

    override fun computeArtifacts(): Try<ArtifactTransformDependencies> {
        return Try.successful(DefaultArtifactTransformDependencies(FileCollectionFactory.empty()))
    }
}


private
object NoOpTransformedVariantFactory : TransformedVariantFactory {
    override fun transformedExternalArtifacts(
        componentIdentifier: ComponentIdentifier,
        sourceVariant: ResolvedVariant,
        variantDefinition: VariantDefinition,
        dependenciesResolverFactory: ExtraExecutionGraphDependenciesResolverFactory
    ): ResolvedArtifactSet {
        throw UnsupportedOperationException("Should not be called")
    }

    override fun transformedProjectArtifacts(
        componentIdentifier: ComponentIdentifier,
        sourceVariant: ResolvedVariant,
        variantDefinition: VariantDefinition,
        dependenciesResolverFactory: ExtraExecutionGraphDependenciesResolverFactory
    ): ResolvedArtifactSet {
        throw UnsupportedOperationException("Should not be called")
    }
}


private
object EmptyVariantTransformRegistry : VariantTransformRegistry {
    override fun <T : TransformParameters?> registerTransform(actionType: Class<out TransformAction<T>>, registrationAction: Action<in org.gradle.api.artifacts.transform.TransformSpec<T>>) {
        throw UnsupportedOperationException("Should not be called")
    }

    override fun getTransforms(): MutableList<ArtifactTransformRegistration> {
        throw UnsupportedOperationException("Should not be called")
    }
}
