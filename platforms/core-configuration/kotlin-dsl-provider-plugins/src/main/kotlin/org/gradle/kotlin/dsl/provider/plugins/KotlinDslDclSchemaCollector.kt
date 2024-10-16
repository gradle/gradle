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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.declarativedsl.utils.DclContainerMemberExtractionUtils
import org.gradle.kotlin.dsl.accessors.ContainerElementFactoryEntry

internal interface KotlinDslDclSchemaCollector {
    fun collectNestedContainerFactories(containerClass: Class<*>): List<ContainerElementFactoryEntry<TypeOf<*>>>
}

internal class CachedKotlinDslDclSchemaCollector(
    private val cache: KotlinDslDclSchemaCache,
    private val delegate: KotlinDslDclSchemaCollector
) : KotlinDslDclSchemaCollector {
    override fun collectNestedContainerFactories(containerClass: Class<*>): List<ContainerElementFactoryEntry<TypeOf<*>>> =
        cache.getOrPutContainerElementFactories(containerClass) { delegate.collectNestedContainerFactories(containerClass) }
}

internal class DefaultKotlinDslDclSchemaCollector : KotlinDslDclSchemaCollector {
    override fun collectNestedContainerFactories(containerClass: Class<*>): List<ContainerElementFactoryEntry<TypeOf<*>>> {
        val getters = containerClass.methods.filter { it.name.startsWith("get") && it.name.substringAfter("get").firstOrNull()?.isUpperCase() ?: false }

        val elementTypes = getters.mapNotNullTo(hashSetOf()) {
            DclContainerMemberExtractionUtils.elementTypeFromNdocContainerType(it.genericReturnType)
        }

        return elementTypes.map { type ->
            val elementType = TypeOf.typeOf<Any>(type)
            val scopeReceiverType = TypeOf.parameterizedTypeOf(typeOf<NamedDomainObjectContainer<*>>(), elementType)
            val factoryName = DclContainerMemberExtractionUtils.elementFactoryFunctionNameFromElementType(elementType.concreteClass.kotlin)
            ContainerElementFactoryEntry(factoryName, scopeReceiverType, elementType)
        }
    }
}
