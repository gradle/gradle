/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl.metadata

import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.cc.impl.ConfigurationCacheOperationIO
import org.gradle.internal.cc.impl.ConfigurationCacheStateStore
import org.gradle.internal.cc.impl.StateType
import org.gradle.internal.cc.impl.models.ProjectStateStore
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.DefaultLocalVariantGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.local.model.LocalVariantMetadata
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.ownerService
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.util.Path


/**
 * In charge of serializing and deserializing the project components for Isolated Projects.
 */
internal
class ProjectMetadataController(
    private val isolateOwner: IsolateOwner,
    private val cacheIO: ConfigurationCacheOperationIO,
    private val resolveStateFactory: LocalComponentGraphResolveStateFactory,
    store: ConfigurationCacheStateStore,
    calculatedValueContainerFactory: CalculatedValueContainerFactory
) : ProjectStateStore<Path, LocalComponentGraphResolveState>(store, StateType.ProjectMetadata, "project metadata", calculatedValueContainerFactory) {

    override fun projectPathForKey(key: Path) = key

    override fun write(encoder: Encoder, value: LocalComponentGraphResolveState) {
        cacheIO.runWriteOperation(encoder) { codecs ->
            withIsolate(isolateOwner, codecs.userTypesCodec()) {
                write(value.id)
                write(value.moduleVersionId)
                writeVariants(value.candidatesForGraphVariantSelection)
            }
        }
    }

    private
    suspend fun WriteContext.writeVariants(candidates: LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates) {
        writeCollection(candidates.allSelectableVariants) {
            writeVariant(it)
        }
    }

    private
    suspend fun WriteContext.writeVariant(variant: LocalVariantGraphResolveState) {
        writeString(variant.name)
        write(variant.attributes)
        writeDependencies(variant.metadata.dependencies)
        writeArtifactVariants(variant.prepareForArtifactResolution().artifactVariants)
    }

    private
    suspend fun WriteContext.writeDependencies(dependencies: List<DependencyMetadata>) {
        writeCollection(dependencies) {
            write(it.selector)
            writeBoolean(it.isConstraint)
        }
    }

    private
    suspend fun WriteContext.writeArtifactVariants(variants: Set<VariantResolveMetadata>) {
        writeCollection(variants) {
            writeArtifactVariant(it)
        }
    }

    private
    suspend fun WriteContext.writeArtifactVariant(variant: VariantResolveMetadata) {
        writeString(variant.name)
        write(variant.identifier)
        write(variant.attributes)
        writeCollection(variant.artifacts)
    }

    override fun read(decoder: Decoder): LocalComponentGraphResolveState {
        return cacheIO.runReadOperation(decoder) { codecs ->
            withIsolate(isolateOwner, codecs.userTypesCodec()) {
                val id = readNonNull<ComponentIdentifier>()
                val moduleVersionId = readNonNull<ModuleVersionIdentifier>()

                val variants = readVariants(id, ownerService())
                resolveStateFactory.realizedStateFor(
                    id,
                    moduleVersionId,
                    Project.DEFAULT_STATUS,
                    EmptySchema.INSTANCE,
                    variants
                )
            }
        }
    }

    private
    suspend fun ReadContext.readVariants(componentId: ComponentIdentifier, factory: CalculatedValueContainerFactory): List<LocalVariantGraphResolveMetadata> {
        return readList {
            readVariant(componentId, factory)
        }
    }

    private
    suspend fun ReadContext.readVariant(componentId: ComponentIdentifier, factory: CalculatedValueContainerFactory): LocalVariantGraphResolveMetadata {
        val variantName = readString()
        val attributes = readNonNull<ImmutableAttributes>()
        val dependencies = readDependencies()
        val variants = readArtifactVariants(factory).toSet()

        val dependencyMetadata = factory.create(Describables.of(variantName, "dependencies"), ValueCalculator {
            DefaultLocalVariantGraphResolveMetadata.VariantDependencyMetadata(
                dependencies, emptySet(), emptyList(),
            )
        })

        val artifactMetadata = factory.create(Describables.of(variantName, "artifacts"), ValueCalculator {
            ImmutableList.of<LocalComponentArtifactMetadata>()
        })

        return DefaultLocalVariantGraphResolveMetadata(
            variantName, variantName, componentId, true, attributes,
            ImmutableCapabilities.EMPTY, false, dependencyMetadata,
            variants, factory, artifactMetadata
        )
    }

    private
    suspend fun ReadContext.readDependencies(): List<LocalComponentDependencyMetadata> {
        return readList {
            val selector = readNonNull<ComponentSelector>()
            val constraint = readBoolean()
            LocalComponentDependencyMetadata(
                selector,
                null,
                emptyList(),
                emptyList(),
                false,
                false,
                true,
                constraint,
                false,
                null
            )
        }
    }

    private
    suspend fun ReadContext.readArtifactVariants(factory: CalculatedValueContainerFactory): List<LocalVariantMetadata> {
        return readList {
            readArtifactVariant(factory)
        }
    }

    private
    suspend fun ReadContext.readArtifactVariant(factory: CalculatedValueContainerFactory): LocalVariantMetadata {
        val variantName = readString()
        val identifier = readNonNull<VariantResolveMetadata.Identifier>()
        val attributes = readNonNull<ImmutableAttributes>()
        val artifacts = readList {
            readNonNull<PublishArtifactLocalArtifactMetadata>()
        }
        val displayName = Describables.of(variantName)
        val artifactMetadata = factory.create(Describables.of(displayName, "artifacts"), ValueCalculator {
            ImmutableList.copyOf<LocalComponentArtifactMetadata>(artifacts)
        })
        return LocalVariantMetadata(variantName, identifier, displayName, attributes, ImmutableCapabilities.EMPTY, artifactMetadata)
    }
}
