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
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultFileCollectionDependency
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantGraphResolveStateBuilder
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.internal.Describables
import org.gradle.internal.cc.impl.ConfigurationCacheOperationIO
import org.gradle.internal.cc.impl.ConfigurationCacheStateStore
import org.gradle.internal.cc.impl.StateType
import org.gradle.internal.cc.impl.models.ProjectStateStore
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.DefaultLocalVariantGraphResolveMetadata
import org.gradle.internal.component.local.model.DefaultLocalVariantGraphResolveState
import org.gradle.internal.component.local.model.DslOriginDependencyMetadataWrapper
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.local.model.LocalVariantMetadata
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.extensions.stdlib.uncheckedCast
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
                writeComponent(value)
            }
        }
    }

    private
    suspend fun WriteContext.writeComponent(value: LocalComponentGraphResolveState) {
        writeComponentMetadata(value.metadata)
        writeVariants(value.candidatesForGraphVariantSelection)
    }

    private
    suspend fun WriteContext.writeComponentMetadata(metadata: LocalComponentGraphResolveMetadata) {
        write(metadata.id)
        write(metadata.moduleVersionId)
        writeString(metadata.status)
        write(metadata.attributesSchema)
    }

    private
    suspend fun WriteContext.writeVariants(candidates: LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates) {
        writeCollection(candidates.allSelectableVariants) {
            writeVariant(it)
        }
    }

    private
    suspend fun WriteContext.writeVariant(variant: LocalVariantGraphResolveState) {
        writeVariantMetadata(variant.metadata)
        writeDependencies(variant.dependencies)
        writeFileDependencies(variant.files)
        writeCollection(variant.excludes)
        writeVariantArtifactSets(variant.prepareForArtifactResolution().artifactVariants)
    }

    private
    suspend fun WriteContext.writeVariantMetadata(variant: LocalVariantGraphResolveMetadata) {
        writeString(variant.name)
        write(variant.attributes)
        write(variant.capabilities)
        writeBoolean(variant.isTransitive)
        writeBoolean(variant.isDeprecated)
    }

    private
    suspend fun WriteContext.writeDependencies(dependencies: List<LocalOriginDependencyMetadata>) {
        writeCollection(dependencies) {
            when (it) {
                is DslOriginDependencyMetadataWrapper -> {
                    write(it.delegate)
                }
                else -> write(it)
            }
        }
    }

    private
    suspend fun WriteContext.writeFileDependencies(files: Set<LocalFileDependencyMetadata>) {
        writeCollection(files) {
            write(it.componentId)
            write(it.files)
        }
    }

    private
    suspend fun WriteContext.writeVariantArtifactSets(variants: Set<VariantResolveMetadata>) {
        writeCollection(variants) {
            writeVariantArtifactSet(it)
        }
    }

    private
    suspend fun WriteContext.writeVariantArtifactSet(variant: VariantResolveMetadata) {
        writeString(variant.name)
        write(variant.identifier)
        write(variant.attributes)
        write(variant.capabilities)
        writeCollection(variant.artifacts)
    }

    override fun read(decoder: Decoder): LocalComponentGraphResolveState {
        return cacheIO.runReadOperation(decoder) { codecs ->
            withIsolate(isolateOwner, codecs.userTypesCodec()) {
                readComponent()
            }
        }
    }

    private
    suspend fun ReadContext.readComponent(): LocalComponentGraphResolveState {
        val metadata = readComponentMetadata()
        val variants = readVariants(metadata.id, ownerService())
        return resolveStateFactory.realizedStateFor(metadata, variants)
    }

    private
    suspend fun ReadContext.readComponentMetadata(): LocalComponentGraphResolveMetadata {
        val id = readNonNull<ComponentIdentifier>()
        val moduleVersionId = readNonNull<ModuleVersionIdentifier>()
        val status = readString()
        val schema = readNonNull<ImmutableAttributesSchema>()

        return LocalComponentGraphResolveMetadata(
            moduleVersionId,
            id,
            status,
            schema
        )
    }

    private
    suspend fun ReadContext.readVariants(componentId: ComponentIdentifier, factory: CalculatedValueContainerFactory): List<LocalVariantGraphResolveState> {
        return readList {
            readVariant(componentId, factory)
        }
    }

    private
    suspend fun ReadContext.readVariant(componentId: ComponentIdentifier, factory: CalculatedValueContainerFactory): LocalVariantGraphResolveState {
        val metadata = readVariantMetadata()
        val dependencies = readList<LocalOriginDependencyMetadata>()
        val files = readFileDependencies().toSet()
        val excludes = readList<ExcludeMetadata>()
        val variants = readVariantArtifactSets(factory).toSet()

        val dependencyMetadata = DefaultLocalVariantGraphResolveState.VariantDependencyMetadata(
            dependencies, files, excludes,
        )

        return resolveStateFactory.realizedVariantStateFor(
            componentId,
            metadata,
            dependencyMetadata,
            variants
        )
    }

    private
    suspend fun ReadContext.readVariantMetadata(): LocalVariantGraphResolveMetadata {
        val variantName = readString()
        val attributes = readNonNull<ImmutableAttributes>()
        val capabilities = readNonNull<ImmutableCapabilities>()
        val transitive = readBoolean()
        val deprecated = readBoolean()

        return DefaultLocalVariantGraphResolveMetadata(
            variantName,
            transitive,
            attributes,
            capabilities,
            deprecated
        )
    }

    private
    suspend fun ReadContext.readFileDependencies(): List<LocalFileDependencyMetadata> {
        return readList {
            val componentId = read()
            val files = readNonNull<FileCollectionInternal>()
            val dependency = if (componentId == null) {
                DefaultFileCollectionDependency(files)
            } else {
                DefaultFileCollectionDependency(
                    componentId.uncheckedCast(),
                    files
                )
            }
            DefaultLocalVariantGraphResolveStateBuilder.DefaultLocalFileDependencyMetadata(
                dependency
            )
        }
    }

    private
    suspend fun ReadContext.readVariantArtifactSets(factory: CalculatedValueContainerFactory): List<LocalVariantMetadata> {
        return readList {
            readVariantArtifactSet(factory)
        }
    }

    private
    suspend fun ReadContext.readVariantArtifactSet(factory: CalculatedValueContainerFactory): LocalVariantMetadata {
        val variantName = readString()
        val identifier = readNonNull<VariantResolveMetadata.Identifier>()
        val attributes = readNonNull<ImmutableAttributes>()
        val capabilities = readNonNull<ImmutableCapabilities>()
        val artifacts = readList<PublishArtifactLocalArtifactMetadata>()
        val displayName = Describables.of(variantName)
        val artifactMetadata = factory.create(Describables.of(displayName, "artifacts"), ValueCalculator {
            ImmutableList.copyOf<LocalComponentArtifactMetadata>(artifacts)
        })
        return LocalVariantMetadata(variantName, identifier, displayName, attributes, capabilities, artifactMetadata)
    }

    private
    suspend fun <T : Any> ReadContext.readList() = readList {
        readNonNull<T>()
    }
}
