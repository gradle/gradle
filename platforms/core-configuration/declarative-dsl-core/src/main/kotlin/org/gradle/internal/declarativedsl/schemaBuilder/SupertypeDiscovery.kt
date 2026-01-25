/*
 * Copyright 2026 the original author or authors.
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

/**
 * Discovers all supertypes of a type that might be potentially declarative.
 * So, for `A : B` and `B : C`, if `A` is included in the schema, this component will also discover `B` and `C`.
 */
class SupertypeDiscovery(
    val isBuildModelType: (KClass<*>) -> Boolean = { false }
) : TypeDiscovery {
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<TypeDiscovery.DiscoveredClass> =
        withAllPotentiallyDeclarativeSupertypes(typeDiscoveryServices.host, kClass)
            .filter { !isBuildModelType(it.kClass) }
}

internal
fun withAllPotentiallyDeclarativeSupertypes(host: SchemaBuildingHost, kClass: KClass<*>): Iterable<TypeDiscovery.DiscoveredClass> =
    host.declarativeSupertypesHierarchy(kClass)
        // Include visible as well as hidden types, as we might still need explicitly exposed members of the hidden types. Just exclude incompatible types.
        .filter { it !is MaybeDeclarativeClassInHierarchy.NonDeclarativeSuperclassInHierarchy }
        .flatMap {
            val tag = TypeDiscovery.DiscoveredClass.DiscoveryTag.Supertype(
                kClass,
                isHidden = it is MaybeDeclarativeClassInHierarchy.HiddenSuperclassInHierarchy
            )

            listOf(TypeDiscovery.DiscoveredClass(it.superClass, listOf(tag))) +
                if (it is MaybeDeclarativeClassInHierarchy.VisibleSuperclassInHierarchy)
                    it.typeVariableAssignments.values.flatMap { TypeDiscovery.DiscoveredClass.classesOf(it, tag) }
                else emptyList()
        }
