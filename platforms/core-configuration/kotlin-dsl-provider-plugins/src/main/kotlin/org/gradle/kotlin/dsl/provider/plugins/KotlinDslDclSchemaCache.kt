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

package org.gradle.kotlin.dsl.provider.plugins

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.reflect.TypeOf
import org.gradle.cache.internal.CrossBuildInMemoryCache
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory
import org.gradle.declarative.dsl.evaluation.InterpretationSequence
import org.gradle.kotlin.dsl.accessors.ContainerElementFactoryEntry
import org.gradle.kotlin.dsl.accessors.SoftwareTypeEntry
import org.gradle.plugin.software.internal.SoftwareTypeRegistry

typealias ContainerElementFactories = List<ContainerElementFactoryEntry<TypeOf<*>>>
typealias SoftwareTypeEntries = List<SoftwareTypeEntry<TypeOf<*>>>

interface KotlinDslDclSchemaCache {
    fun getOrPutContainerElementFactories(
        forInterpretationSequence: InterpretationSequence,
        classLoaderScope: ClassLoaderScope,
        produceIfAbsent: () -> ContainerElementFactories
    ): ContainerElementFactories

    fun getOrPutContainerElementSoftwareTypes(
        forRegistry: SoftwareTypeRegistry,
        produceIfAbsent: () -> SoftwareTypeEntries
    ): SoftwareTypeEntries
}

class CrossBuildInMemoryKotlinDslDclSchemaCache(
    crossBuildInMemoryCacheFactory: CrossBuildInMemoryCacheFactory
) : KotlinDslDclSchemaCache {

    private val containerElementFactoriesCache: CrossBuildInMemoryCache<ContainerElementFactoriesKey, ContainerElementFactories> =
        crossBuildInMemoryCacheFactory.newCache()

    private val softwareTypeEntriesCache: CrossBuildInMemoryCache<SoftwareTypeRegistry, SoftwareTypeEntries> =
        crossBuildInMemoryCacheFactory.newCache()

    private data class ContainerElementFactoriesKey(
        val interpretationSequence: InterpretationSequence,
        val classLoaderScope: ClassLoaderScope
    )

    override fun getOrPutContainerElementFactories(
        forInterpretationSequence: InterpretationSequence,
        classLoaderScope: ClassLoaderScope,
        produceIfAbsent: () -> ContainerElementFactories
    ): ContainerElementFactories = containerElementFactoriesCache
        .get(ContainerElementFactoriesKey(forInterpretationSequence, classLoaderScope)) { _ ->
            produceIfAbsent().ifEmpty { emptyList() } // avoid referencing lots of empty lists, store a reference to the singleton instead
        }

    override fun getOrPutContainerElementSoftwareTypes(
        forRegistry: SoftwareTypeRegistry,
        produceIfAbsent: () -> SoftwareTypeEntries
    ): SoftwareTypeEntries = softwareTypeEntriesCache.get(forRegistry) { _ ->
        produceIfAbsent()
    }
}
