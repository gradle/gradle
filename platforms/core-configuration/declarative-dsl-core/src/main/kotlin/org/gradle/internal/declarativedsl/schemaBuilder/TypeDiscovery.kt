/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.declarativedsl.schemaBuilder

import kotlin.reflect.KClass


interface TypeDiscovery {
    interface TypeDiscoveryServices {
        val host: SchemaBuildingHost
        val propertyExtractor: PropertyExtractor
    }

    data class DiscoveredClass(val kClass: KClass<*>, val isHidden: Boolean)

    fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscoveryServices, kClass: KClass<*>): Iterable<DiscoveredClass>

    companion object {
        val none = object : TypeDiscovery {
            override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscoveryServices, kClass: KClass<*>): Iterable<DiscoveredClass> = emptyList()
        }
    }
}


class CompositeTypeDiscovery(internal val implementations: Iterable<TypeDiscovery>) : TypeDiscovery {
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<TypeDiscovery.DiscoveredClass> =
        implementations.flatMap { it.getClassesToVisitFrom(typeDiscoveryServices, kClass) }
            .groupBy { it.kClass }.entries.map { (kClass, discovered) -> TypeDiscovery.DiscoveredClass(kClass, discovered.all { it.isHidden }) }
}

class FilteringTypeDiscovery(private val delegate: TypeDiscovery, val typeFilter: (KClass<*>) -> Boolean) : TypeDiscovery {
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<TypeDiscovery.DiscoveredClass> =
        if (typeFilter(kClass)) delegate.getClassesToVisitFrom(typeDiscoveryServices, kClass).filter { typeFilter(it.kClass) } else emptyList()
}


operator fun TypeDiscovery.plus(other: TypeDiscovery): CompositeTypeDiscovery = CompositeTypeDiscovery(buildList {
    fun include(typeDiscovery: TypeDiscovery) = when (typeDiscovery) {
        is CompositeTypeDiscovery -> addAll(typeDiscovery.implementations)
        else -> add(typeDiscovery)
    }
    include(this@plus)
    include(other)
})
