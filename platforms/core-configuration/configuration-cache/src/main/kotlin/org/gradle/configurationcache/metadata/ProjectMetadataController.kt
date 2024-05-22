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

package org.gradle.configurationcache.metadata

import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.configurationcache.ConfigurationCacheIO
import org.gradle.configurationcache.ConfigurationCacheStateStore
import org.gradle.configurationcache.DefaultConfigurationCache
import org.gradle.configurationcache.StateType
import org.gradle.configurationcache.models.ProjectStateStore
import org.gradle.configurationcache.serialization.IsolateOwners
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.ownerService
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.DefaultLocalConfigurationMetadata
import org.gradle.internal.component.local.model.DefaultLocalConfigurationMetadata.ConfigurationDependencyMetadata
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalComponentGraphResolveStateFactory
import org.gradle.internal.component.local.model.LocalConfigurationGraphResolveMetadata
import org.gradle.internal.component.local.model.LocalVariantMetadata
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.util.Path


/**
 * In charge of serializing and deserializing the project components for Isolated Projects.
 */
internal
class ProjectMetadataController(
    private val host: DefaultConfigurationCache.Host,
    private val cacheIO: ConfigurationCacheIO,
    private val resolveStateFactory: LocalComponentGraphResolveStateFactory,
    store: ConfigurationCacheStateStore,
    calculatedValueContainerFactory: CalculatedValueContainerFactory
) : ProjectStateStore<Path, LocalComponentGraphResolveState>(store, StateType.ProjectMetadata, "project metadata", calculatedValueContainerFactory) {

    override fun projectPathForKey(key: Path) = key

    override fun write(encoder: Encoder, value: LocalComponentGraphResolveState) {
        val (context, codecs) = cacheIO.writerContextFor(encoder)
        context.push(IsolateOwners.OwnerHost(host), codecs.userTypesCodec())
        context.runWriteOperation {
            write(value.id)
            write(value.moduleVersionId)
            writeConfigurations(value.candidatesForGraphVariantSelection)
        }
    }

    private
    suspend fun WriteContext.writeConfigurations(candidates: LocalComponentGraphResolveState.LocalComponentGraphSelectionCandidates) {
        writeCollection(candidates.allSelectableVariants) {
            writeConfiguration(it)
        }
    }

    private
    suspend fun WriteContext.writeConfiguration(configuration: VariantGraphResolveState) {
        writeString(configuration.name)
        write(configuration.attributes)
        writeDependencies(configuration.metadata.dependencies)
        writeVariants(configuration.prepareForArtifactResolution().artifactVariants)
    }

    private
    suspend fun WriteContext.writeDependencies(dependencies: List<DependencyMetadata>) {
        writeCollection(dependencies) {
            write(it.selector)
            writeBoolean(it.isConstraint)
        }
    }

    private
    suspend fun WriteContext.writeVariants(variants: Set<VariantResolveMetadata>) {
        writeCollection(variants) {
            writeVariant(it)
        }
    }

    private
    suspend fun WriteContext.writeVariant(variant: VariantResolveMetadata) {
        writeString(variant.name)
        write(variant.identifier)
        write(variant.attributes)
        writeCollection(variant.artifacts)
    }

    override fun read(decoder: Decoder): LocalComponentGraphResolveState {
        val (context, codecs) = cacheIO.readerContextFor(decoder)
        context.push(IsolateOwners.OwnerHost(host), codecs.userTypesCodec())
        return context.runReadOperation {
            val id = readNonNull<ComponentIdentifier>()
            val moduleVersionId = readNonNull<ModuleVersionIdentifier>()

            val configurations = readConfigurations(id, ownerService())
            resolveStateFactory.realizedStateFor(
                id,
                moduleVersionId,
                Project.DEFAULT_STATUS,
                EmptySchema.INSTANCE,
                configurations
            )
        }
    }

    private
    suspend fun ReadContext.readConfigurations(componentId: ComponentIdentifier, factory: CalculatedValueContainerFactory): List<LocalConfigurationGraphResolveMetadata> {
        return readList {
            readConfiguration(componentId, factory)
        }
    }

    private
    suspend fun ReadContext.readConfiguration(componentId: ComponentIdentifier, factory: CalculatedValueContainerFactory): LocalConfigurationGraphResolveMetadata {
        val configurationName = readString()
        val configurationAttributes = readNonNull<ImmutableAttributes>()
        val dependencies = readDependencies()
        val variants = readVariants(factory).toSet()

        val dependencyMetadata = factory.create(Describables.of(configurationName, "dependencies"), ValueCalculator {
            ConfigurationDependencyMetadata(
                dependencies, emptySet(), emptyList(),
            )
        })

        val artifactMetadata = factory.create(Describables.of(configurationName, "artifacts"), ValueCalculator {
            ImmutableList.of<LocalComponentArtifactMetadata>()
        })

        return DefaultLocalConfigurationMetadata(
            configurationName, configurationName, componentId, true, true, setOf(configurationName), configurationAttributes,
            ImmutableCapabilities.EMPTY, true, false, true, dependencyMetadata,
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
    suspend fun ReadContext.readVariants(factory: CalculatedValueContainerFactory): List<LocalVariantMetadata> {
        return readList {
            readVariant(factory)
        }
    }

    private
    suspend fun ReadContext.readVariant(factory: CalculatedValueContainerFactory): LocalVariantMetadata {
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
