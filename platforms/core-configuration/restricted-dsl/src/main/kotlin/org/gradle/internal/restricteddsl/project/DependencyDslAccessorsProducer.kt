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

package org.gradle.internal.restricteddsl.project

import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.CollectedPropertyInformation
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.DataClassSchemaProducer
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.PropertyExtractor
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType


internal
class DependencyDslAccessorsProducer : DataClassSchemaProducer {
    private
    val dependencyGetters = PropertyExtractor { property ->
        (property.returnType.classifier as? KClass<*>)?.isGeneratedAccessors() == true
    }

    override fun getOtherClassesToVisitFrom(kClass: KClass<*>): Iterable<KClass<*>> {
        return if (kClass.isGeneratedAccessors()) {
            allClassesReachableFromGetters(kClass).flatMapTo(mutableSetOf(), ::allSupertypes)
        } else {
            emptyList()
        }
    }

    private
    fun allClassesReachableFromGetters(kClass: KClass<*>) = buildSet<KClass<*>> {
        fun visit(kClass: KClass<*>) {
            if (add(kClass)) {
                val properties = dependencyGetters.extractProperties(kClass)
                val typesFromGetters = properties.mapNotNull { it.originalReturnType.classifier as? KClass<*> }
                typesFromGetters.forEach(::visit)
            }
        }
        visit(kClass)
    }

    private
    fun allSupertypes(kClass: KClass<*>) = buildSet<KClass<*>> {
        fun visit(type: KType) {
            val classifier = type.classifier
            if (classifier is KClass<*> && add(classifier)) {
                classifier.supertypes.forEach(::visit)
            }
        }
        add(kClass)
        kClass.supertypes.forEach(::visit)
    }

    override fun extractPropertiesOf(kClass: KClass<*>): Iterable<CollectedPropertyInformation> =
        if (kClass.isGeneratedAccessors()) {
            dependencyGetters.extractProperties(kClass)
        } else emptyList()

    private
    fun KClass<*>.isGeneratedAccessors() =
        // TODO: find a better way to filter the accessor types
        qualifiedName.orEmpty().startsWith("org.gradle.accessors.dm.")

    override fun getFunctionsToExtract(kClass: KClass<*>): Iterable<KFunction<*>> = emptyList()

    override fun getConstructorsToExtract(kClass: KClass<*>): Iterable<KFunction<*>> = emptyList()
}
