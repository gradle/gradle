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
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.annotationsWithGetters
import org.gradle.internal.declarativedsl.schemaBuilder.asSupported
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelMember
import org.gradle.internal.declarativedsl.schemaBuilder.returnTypeToRefOrError
import org.gradle.internal.declarativedsl.schemaBuilder.schemaBuildingFailure
import org.gradle.internal.declarativedsl.schemaBuilder.toKType
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.full.createType


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
                        property.returnType.toKType(),
                        property.returnTypeToRefOrError(host) {
                            propertyValueType(property.returnType.toKType()).asSupported()
                                ?: host.schemaBuildingFailure("Unsupported property type: ${property.returnType.toKType()}")
                        },
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
                val type = getter.returnTypeToRefOrError(host) {
                    propertyValueType(getter.returnType.toKType()).asSupported()
                        ?: host.schemaBuildingFailure("Unsupported property type: ${getter.returnType.toKType()}")
                }
                val isHidden = getter.kCallable.annotations.any { it is HiddenInDefinition }
                val isDirectAccessOnly = getter.kCallable.annotations.any { it is AccessFromCurrentReceiverOnly }
                CollectedPropertyInformation(
                    propertyName,
                    getter.returnType.toKType(),
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
        typeDiscoveryServices.propertyExtractor.extractProperties(typeDiscoveryServices.host, kClass).mapNotNullTo(mutableSetOf()) {
            (propertyValueType(it.originalReturnType).classifier as? KClass<*>)
                ?.let { kClass -> TypeDiscovery.DiscoveredClass(kClass, false) }
        }
}


private
val handledPropertyTypes = setOf(Property::class, DirectoryProperty::class, RegularFileProperty::class, ListProperty::class, MapProperty::class)


private
fun isGradlePropertyType(type: KClassifier): Boolean = type in handledPropertyTypes


private
fun propertyValueType(type: KType): KType {
    if (type.classifier == ListProperty::class) {
        return List::class.createType(type.arguments)
    }

    if (type.classifier == MapProperty::class) {
        return Map::class.createType(type.arguments)
    }

    fun searchClassHierarchyForPropertyType(type: KType): KType? {
        return when (val classifier = type.classifier) {
            Property::class -> type
            is KClass<*> -> classifier.supertypes.firstOrNull { superType -> searchClassHierarchyForPropertyType(superType) != null }
            else -> null
        }
    }

    val propertyType = searchClassHierarchyForPropertyType(type)
    return propertyType?.arguments?.get(0)?.type ?: type
}
