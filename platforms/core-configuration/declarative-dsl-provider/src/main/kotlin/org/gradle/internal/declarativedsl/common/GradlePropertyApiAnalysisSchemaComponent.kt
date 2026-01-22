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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.declarative.dsl.model.annotations.AccessFromCurrentReceiverOnly
import org.gradle.declarative.dsl.model.annotations.HiddenInDefinition
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.schemaBuilder.CollectedPropertyInformation
import org.gradle.internal.declarativedsl.schemaBuilder.MemberKind
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.PropertyType
import org.gradle.internal.declarativedsl.schemaBuilder.annotationsWithGetters
import org.gradle.internal.declarativedsl.schemaBuilder.asSupported
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelMember
import org.gradle.internal.declarativedsl.schemaBuilder.returnTypeToRefOrError
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.full.allSupertypes


/**
 * Extracts schema properties from Kotlin properties and Java getters returning the type [Property].
 * Ensures that the return types of these properties get discovered during type discovery.
 */
internal
class GradlePropertyApiAnalysisSchemaComponent : AnalysisSchemaComponent {

    override fun propertyExtractors(): List<PropertyExtractor> = listOf(GradlePropertyApiPropertyExtractor())

    override fun typeDiscovery(): List<TypeDiscovery> = listOf(PropertyReturnTypeDiscovery())
}


private
class GradlePropertyApiPropertyExtractor : PropertyExtractor {
    override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<CollectedPropertyInformation> =
        propertiesFromGettersOf(host, kClass, propertyNamePredicate) + memberPropertiesOf(host, kClass, propertyNamePredicate)

    private
    fun memberPropertiesOf(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<CollectedPropertyInformation> =
        host.classMembers(kClass).potentiallyDeclarativeMembers
            .filter { member -> member.kind == MemberKind.READ_ONLY_PROPERTY && isGradlePropertyType(member.returnType.classifier) }.map { property ->
                host.inContextOfModelMember(property.kCallable) {
                    val isDirectAccessOnly = property.kCallable.annotationsWithGetters.any { it is AccessFromCurrentReceiverOnly }
                    CollectedPropertyInformation(
                        property.name,
                        property.returnType,
                        property.returnTypeToRefOrError(host) { propertyValueType(property.returnType) },
                        DefaultDataProperty.DefaultPropertyMode.DefaultWriteOnly,
                        hasDefaultValue = true,
                        isHiddenInDefinition = false,
                        isDirectAccessOnly = isDirectAccessOnly,
                        claimedFunctions = emptyList()
                    )
                }
            }.filter { propertyNamePredicate(it.name) }

    private
    fun propertiesFromGettersOf(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<CollectedPropertyInformation> {
        val functionsByName = host.classMembers(kClass).potentiallyDeclarativeMembers
            .filter { it.kind == MemberKind.FUNCTION }
            .groupBy { it.name }

        val getters = functionsByName
            .filterKeys { it.startsWith("get") && it.substringAfter("get").firstOrNull()?.isUpperCase() == true }
            .mapValues { (_, functions) -> functions.singleOrNull { fn -> fn.parameters.none() } }
            .filterValues { it != null && isGradlePropertyType(it.returnType.classifier) }

        return getters.map { (name, getter) ->
            checkNotNull(getter)
            host.inContextOfModelMember(getter.kCallable) {
                val nameAfterGet = name.substringAfter("get")
                val propertyName = nameAfterGet.replaceFirstChar { it.lowercase(Locale.getDefault()) }
                val type = getter.returnTypeToRefOrError(host) { propertyValueType(getter.returnType) }
                val isHidden = getter.kCallable.annotations.any { it is HiddenInDefinition }
                val isDirectAccessOnly = getter.kCallable.annotations.any { it is AccessFromCurrentReceiverOnly }
                CollectedPropertyInformation(
                    propertyName,
                    getter.returnType,
                    type,
                    DefaultDataProperty.DefaultPropertyMode.DefaultWriteOnly,
                    hasDefaultValue = true,
                    isHidden,
                    isDirectAccessOnly,
                    claimedFunctions = listOf(getter.kCallable)
                )
            }
        }.filter { propertyNamePredicate(it.name) }
    }
}


private
class PropertyReturnTypeDiscovery : TypeDiscovery {
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<TypeDiscovery.DiscoveredClass> =
        typeDiscoveryServices.propertyExtractor.extractProperties(typeDiscoveryServices.host, kClass).flatMap {
            propertyValueType(it.originalReturnType)
                .let { propertyValueType -> TypeDiscovery.DiscoveredClass.classesOf(propertyValueType, PropertyType(kClass, it.name)) }
        }
}


private
val handledPropertyTypes = setOf(Property::class, DirectoryProperty::class, RegularFileProperty::class, ListProperty::class, MapProperty::class)


private
fun isGradlePropertyType(type: KClassifier): Boolean = type in handledPropertyTypes


private
fun propertyValueType(type: SupportedTypeProjection.SupportedType): SupportedTypeProjection.SupportedType {
    if (type.classifier == ListProperty::class) {
        return SupportedTypeProjection.SupportedType(List::class, type.arguments)
    }

    if (type.classifier == MapProperty::class) {
        return SupportedTypeProjection.SupportedType(Map::class, type.arguments)
    }

    fun searchClassHierarchyForPropertyType(type: SupportedTypeProjection.SupportedType): SupportedTypeProjection.SupportedType? {
        return when (val classifier = type.classifier) {
            Property::class -> type
            is KClass<*> -> classifier.allSupertypes.find { it.classifier == Property::class }?.asSupported()
            else -> null
        }
    }

    val propertyType = searchClassHierarchyForPropertyType(type)

    return when (propertyType) {
        is SupportedTypeProjection.SupportedType ->
            propertyType.arguments.getOrNull(0) as? SupportedTypeProjection.SupportedType ?: type
        else -> type
    }
}
