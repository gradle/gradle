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

import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass
import kotlin.reflect.KCallable
import kotlin.reflect.KClass


interface TypeDiscovery {
    /**
     * Given a [kClass] that is included in the schema, produces [DiscoveredClass] entries for other classes reachable from the [kClass] that should be
     * considered for inclusionn into the schema. The returned [DiscoveredClass] entries might have non-unique [DiscoveredClass.kClass] values.
     */
    fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscoveryServices, kClass: KClass<*>): Iterable<SchemaResult<DiscoveredClass>>

    interface TypeDiscoveryServices {
        val host: SchemaBuildingHost
        val propertyExtractor: PropertyExtractor
    }

    data class DiscoveredClass(
        val kClass: KClass<*>,
        val discoveryTag: DiscoveryTag
    ) {
        val isHidden: Boolean
            get() = discoveryTag.isHidden

        sealed interface DiscoveryTag {
            val isHidden: Boolean

            data class Supertype(val ofType: KClass<*>, override val isHidden: Boolean) : DiscoveryTag
            data class ContainerElement(val containerMember: KCallable<*>) : DiscoveryTag {
                override val isHidden = false
            }

            data class UsedInMember(val member: KCallable<*>) : DiscoveryTag {
                override val isHidden = false
            }

            data class PropertyType(val kClass: KClass<*>, val propertyName: String) : DiscoveryTag {
                override val isHidden = false
            }

            data class Special(val description: String) : DiscoveryTag {
                override val isHidden = false
            }
        }

        companion object {
            fun classesOf(kType: SupportedTypeProjection.SupportedType, discoveryTag: DiscoveryTag): Iterable<DiscoveredClass> =
                buildSet {
                    fun visitType(type: SupportedTypeProjection.SupportedType) { // safe to recurse without the visited set: the types are deconstructed to smaller types
                        (type.classifier as? KClass<*>)?.let(::add)
                        type.arguments.forEach {
                            when (it) {
                                SupportedTypeProjection.StarProjection -> Unit
                                is SupportedTypeProjection.ProjectedType -> visitType(it.type)
                                is SupportedTypeProjection.SupportedType -> visitType(it)
                            }
                        }
                    }
                    visitType(kType)
                }.map { DiscoveredClass(it, discoveryTag) }
        }
    }

    companion object {
        val none = object : TypeDiscovery {
            override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscoveryServices, kClass: KClass<*>): Iterable<SchemaResult<DiscoveredClass>> = emptyList()
        }
    }
}


class CompositeTypeDiscovery(internal val implementations: Iterable<TypeDiscovery>) : TypeDiscovery {
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<SchemaResult<DiscoveredClass>> =
        implementations.flatMap { it.getClassesToVisitFrom(typeDiscoveryServices, kClass) }
}

class FilteringTypeDiscovery(private val delegate: TypeDiscovery, val typeFilter: (KClass<*>) -> Boolean) : TypeDiscovery {
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<SchemaResult<DiscoveredClass>> =
        if (typeFilter(kClass)) delegate.getClassesToVisitFrom(typeDiscoveryServices, kClass).filter { it !is SchemaResult.Result || typeFilter(it.result.kClass) } else emptyList()
}


operator fun TypeDiscovery.plus(other: TypeDiscovery): CompositeTypeDiscovery = CompositeTypeDiscovery(buildList {
    fun include(typeDiscovery: TypeDiscovery) = when (typeDiscovery) {
        is CompositeTypeDiscovery -> addAll(typeDiscovery.implementations)
        else -> add(typeDiscovery)
    }
    include(this@plus)
    include(other)
})
