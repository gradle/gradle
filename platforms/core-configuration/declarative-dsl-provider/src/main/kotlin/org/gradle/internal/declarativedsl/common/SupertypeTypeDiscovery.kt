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

package org.gradle.internal.declarativedsl.common

import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import kotlin.reflect.KClass
import kotlin.reflect.KType


/**
 * Discovers all supertypes of a type that might be potentially declarative.
 * So, for `A : B` and `B : C`, if `A` is included in the schema, this component will also discover `B` and `C`.
 * This does not include the [Any] type.
 */
internal
class SupertypeTypeDiscovery : AnalysisSchemaComponent {
    override fun typeDiscovery(): List<TypeDiscovery> = listOf(
        object : TypeDiscovery {
            override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<KClass<*>> =
                withAllPotentiallyDeclarativeSupertypes(kClass)
        }
    )
}


internal
fun withAllPotentiallyDeclarativeSupertypes(kClass: KClass<*>) = buildSet<KClass<*>> {
    fun visit(type: KType) {
        val classifier = type.classifier
        if (classifier is KClass<*> && add(classifier)) {
            classifier.supertypes.forEach(::visit)
        }
    }
    add(kClass)
    kClass.supertypes.forEach(::visit)

    // No need to include Any, it only clutters the schema
    remove(Any::class)
}
