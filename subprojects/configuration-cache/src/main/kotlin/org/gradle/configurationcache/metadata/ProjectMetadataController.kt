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
import org.gradle.api.internal.attributes.EmptySchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.configurationcache.ConfigurationCacheIO
import org.gradle.configurationcache.ConfigurationCacheStateStore
import org.gradle.configurationcache.DefaultConfigurationCache
import org.gradle.configurationcache.StateType
import org.gradle.configurationcache.models.ProjectStateStore
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.DefaultLocalComponentMetadata
import org.gradle.internal.component.local.model.LocalComponentMetadata
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
        context.push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec)
        context.runWriteOperation {
            write(value.id)
            write(value.moduleVersionId)
            val configurations = value.configurationNames.mapNotNull {
                val configuration = value.getConfiguration(it)!!
                if (configuration.isCanBeConsumed) configuration else null
            }
            writeSmallInt(configurations.size)
            for (configuration in configurations) {
                writeString(configuration.name)
                write(configuration.attributes)
                val variants = configuration.variants
                writeSmallInt(variants.size)
                for (variant in variants) {
                    writeString(variant.name)
                    write(variant.identifier)
                    write(variant.attributes)
                }
            }
        }
    }

    override fun read(decoder: Decoder): LocalComponentMetadata {
        val (context, codecs) = cacheIO.readerContextFor(decoder)
        context.push(IsolateOwner.OwnerHost(host), codecs.userTypesCodec)
        return context.runReadOperation {
            val id = readNonNull<ComponentIdentifier>()
            val moduleVersionId = readNonNull<ModuleVersionIdentifier>()
            val metadata = DefaultLocalComponentMetadata(moduleVersionId, id, Project.DEFAULT_STATUS, EmptySchema.INSTANCE)
            val configurationCount = readSmallInt()
            for (i in 0 until configurationCount) {
                val configurationName = readString()
                val configurationAttributes = readNonNull<ImmutableAttributes>()
                metadata.addConfiguration(configurationName, null, emptySet(), ImmutableSet.of(configurationName), true, true, configurationAttributes, true, null, true, ImmutableCapabilities.EMPTY, { emptyList() })
                val variantCount = readSmallInt()
                for (j in 0 until variantCount) {
                    val variantName = readString()
                    val identifier = readNonNull<VariantResolveMetadata.Identifier>()
                    val attributes = readNonNull<ImmutableAttributes>()
                    metadata.addVariant(configurationName, variantName, identifier, Describables.of(variantName), attributes, ImmutableCapabilities.EMPTY, emptyList())
                }
            }
            metadata
        }
    }
}
