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

import org.gradle.api.reflect.TypeOf
import org.gradle.cache.internal.CrossBuildInMemoryCache
import org.gradle.kotlin.dsl.accessors.ContainerElementFactoryEntry

typealias ContainerElementFactories = List<ContainerElementFactoryEntry<TypeOf<*>>>

interface KotlinDslDclSchemaCache {
    fun getOrPutContainerElementFactories(forClass: Class<*>, produceIfAbsent: () -> ContainerElementFactories): ContainerElementFactories
}

class CrossBuildInMemoryKotlinDslDclSchemaCache(
    private val containerElementFactoriesCache: CrossBuildInMemoryCache<Class<*>, ContainerElementFactories>
) : KotlinDslDclSchemaCache {
    override fun getOrPutContainerElementFactories(forClass: Class<*>, produceIfAbsent: () -> ContainerElementFactories): ContainerElementFactories =
        containerElementFactoriesCache.getIfPresent(forClass)
            ?: produceIfAbsent().also { result ->
                containerElementFactoriesCache.put(
                    forClass,
                    result.takeIf(Collection<*>::isNotEmpty) ?: emptyList() // avoid referencing lots of empty lists, store a reference to the singleton instead
                )
            }
}
