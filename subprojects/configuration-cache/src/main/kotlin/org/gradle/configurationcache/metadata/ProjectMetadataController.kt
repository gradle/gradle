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

import com.google.common.collect.ImmutableSet
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
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readCollection
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata
import org.gradle.internal.component.local.model.LocalComponentMetadata
import org.gradle.internal.component.local.model.LocalConfigurationMetadata
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.util.Path


internal
class ProjectMetadataController(
    private val host: DefaultConfigurationCache.Host,
    private val cacheIO: ConfigurationCacheIO,
    store: ConfigurationCacheStateStore
) : ProjectStateStore<Path, LocalComponentMetadata>(store, StateType.ProjectMetadata) {

    override fun projectPathForKey(key: Path) = key

    override fun write(encoder: Encoder, value: LocalComponentMetadata) {
        val (context, codecs) = cacheIO.writerContextFor(encoder)
        context.push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec())
        context.runWriteOperation {
            write(value.id)
            write(value.moduleVersionId)
            val configurations = value.configurationsToPersist()
            writeConfigurations(configurations)
        }
    }

    private
    fun LocalComponentMetadata.configurationsToPersist() = configurationNames.mapNotNull {
        val configuration = getConfiguration(it)!!
        if (configuration.isCanBeConsumed) configuration else null
    }

    private
    suspend fun WriteContext.writeConfigurations(configurations: List<LocalConfigurationMetadata>) {
        writeCollection(configurations) {
            writeConfiguration(it)
        }
    }

    private
    suspend fun WriteContext.writeConfiguration(configuration: LocalConfigurationMetadata) {
        writeString(configuration.name)
        write(configuration.attributes)
        writeDependencies(configuration.dependencies)
        writeVariants(configuration.variants)
    }

    private
    suspend fun WriteContext.writeDependencies(dependencies: List<LocalOriginDependencyMetadata>) {
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

    override fun read(decoder: Decoder): LocalComponentMetadata {
        val (context, codecs) = cacheIO.readerContextFor(decoder)
        context.push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec())
        return context.runReadOperation {
            val id = readNonNull<ComponentIdentifier>()
            val moduleVersionId = readNonNull<ModuleVersionIdentifier>()
            val metadata = DefaultLocalComponentMetadata(moduleVersionId, id, Project.DEFAULT_STATUS, EmptySchema.INSTANCE)
            readConfigurationsInto(metadata)
            metadata
        }
    }

    private
    suspend fun ReadContext.readConfigurationsInto(metadata: DefaultLocalComponentMetadata) {
        readCollection {
            readConfigurationInto(metadata)
        }
    }

    private
    suspend fun ReadContext.readConfigurationInto(metadata: DefaultLocalComponentMetadata) {
        val configurationName = readString()
        val configurationAttributes = readNonNull<ImmutableAttributes>()
        val configuration = metadata.addConfiguration(configurationName, null, emptySet(), ImmutableSet.of(configurationName), true, true, configurationAttributes, true, null, true, ImmutableCapabilities.EMPTY, { emptyList() })
        readDependenciesInto(metadata, configuration)
        readVariantsInto(metadata, configurationName)
    }

    private
    suspend fun ReadContext.readDependenciesInto(metadata: DefaultLocalComponentMetadata, configuration: BuildableLocalConfigurationMetadata) {
        readCollection {
            val selector = readNonNull<ComponentSelector>()
            val constraint = readBoolean()
            configuration.addDependency(
                LocalComponentDependencyMetadata(
                    metadata.id,
                    selector,
                    null,
                    null,
                    ImmutableAttributes.EMPTY,
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
            )
        }
    }

    private
    suspend fun ReadContext.readVariantsInto(metadata: DefaultLocalComponentMetadata, configurationName: String) {
        readCollection {
            readVariantInto(metadata, configurationName)
        }
    }

    private
    suspend fun ReadContext.readVariantInto(metadata: DefaultLocalComponentMetadata, configurationName: String) {
        val variantName = readString()
        val identifier = readNonNull<VariantResolveMetadata.Identifier>()
        val attributes = readNonNull<ImmutableAttributes>()
        // TODO - don't unpack the artifacts
        val artifacts = readList {
            readNonNull<PublishArtifactLocalArtifactMetadata>().publishArtifact
        }
        metadata.addVariant(configurationName, variantName, identifier, Describables.of(variantName), attributes, ImmutableCapabilities.EMPTY, artifacts)
    }
}
