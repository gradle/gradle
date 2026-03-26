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
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.schemaBuilder.ExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.MemberKind
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractionMetadata
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractionResult
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaBuildingHost
import org.gradle.internal.declarativedsl.schemaBuilder.SchemaResult
import org.gradle.internal.declarativedsl.schemaBuilder.SupportedTypeProjection
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery.DiscoveredClass.DiscoveryTag.PropertyType
import org.gradle.internal.declarativedsl.schemaBuilder.annotationsWithGetters
import org.gradle.internal.declarativedsl.schemaBuilder.asSupported
import org.gradle.internal.declarativedsl.schemaBuilder.inContextOfModelMember
import org.gradle.internal.declarativedsl.schemaBuilder.orFailWith
import org.gradle.internal.declarativedsl.schemaBuilder.returnTypeRef
import org.gradle.internal.declarativedsl.schemaBuilder.schemaResult
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
    override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<PropertyExtractionResult> =
        propertiesFromGettersOf(host, kClass, propertyNamePredicate) + memberPropertiesOf(host, kClass, propertyNamePredicate)

    private
    fun memberPropertiesOf(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<PropertyExtractionResult> =
        host.classMembers(kClass).declarativeMembers
            .filter { member -> member.kind == MemberKind.READ_ONLY_PROPERTY && isGradlePropertyType(member.returnType.classifier) }
            .mapNotNull { property ->
                if (!propertyNamePredicate(property.name))
                    return@mapNotNull null

                host.inContextOfModelMember(property.kCallable) {
                    val isDirectAccessOnly = property.kCallable.annotationsWithGetters.any { it is AccessFromCurrentReceiverOnly }
                    val meta = PropertyExtractionMetadata(listOf(property), property.returnType)
                    ExtractionResult.Extracted(
                        DefaultDataProperty(
                            property.name,
                            property.returnTypeRef(host) { propertyValueType(host, property.returnType) }
                                .orFailWith { return@mapNotNull ExtractionResult.of(it, meta) },
                            DefaultDataProperty.DefaultPropertyMode.DefaultWriteOnly,
                            hasDefaultValue = true,
                            isDirectAccessOnly = isDirectAccessOnly,
                        ),
                        metadata = meta
                    )
                }
            }

    private
    fun propertiesFromGettersOf(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<PropertyExtractionResult> {
        val functionsByName = host.classMembers(kClass).declarativeMembers
            .filter { it.kind == MemberKind.FUNCTION }
            .groupBy { it.name }

        val getters = functionsByName
            .filterKeys { it.startsWith("get") && it.substringAfter("get").firstOrNull()?.isUpperCase() == true }
            .mapValues { (_, functions) -> functions.singleOrNull { fn -> fn.parameters.none() } }
            .filterValues { it != null && isGradlePropertyType(it.returnType.classifier) }

        return getters.mapNotNull { (name, getter) ->
            checkNotNull(getter)
            host.inContextOfModelMember(getter.kCallable) {
                val nameAfterGet = name.substringAfter("get")
                val propertyName = nameAfterGet.replaceFirstChar { it.lowercase(Locale.getDefault()) }

                if (!propertyNamePredicate(propertyName)) {
                    return@mapNotNull null
                }

                val meta = PropertyExtractionMetadata(listOf(getter), getter.returnType)

                val type = getter.returnTypeRef(host) { propertyValueType(host, getter.returnType) }
                    .orFailWith { return@mapNotNull ExtractionResult.of(it, meta) }
                val isHidden = getter.kCallable.annotations.any { it is HiddenInDefinition }
                val isDirectAccessOnly = getter.kCallable.annotations.any { it is AccessFromCurrentReceiverOnly }
                ExtractionResult.Extracted(
                    DefaultDataProperty(
                        propertyName,
                        type,
                        DefaultDataProperty.DefaultPropertyMode.DefaultWriteOnly,
                        hasDefaultValue = true,
                        isHidden,
                        isDirectAccessOnly,
                    ),
                    metadata = PropertyExtractionMetadata(listOf(getter), getter.returnType)
                )
            }
        }
    }
}


private
class PropertyReturnTypeDiscovery : TypeDiscovery {
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<SchemaResult<TypeDiscovery.DiscoveredClass>> =
        typeDiscoveryServices.propertyExtractor.extractProperties(typeDiscoveryServices.host, kClass).flatMap { extractionResult ->
            when (extractionResult) {
                is ExtractionResult.Extracted<DataProperty, PropertyExtractionMetadata> ->
                    extractionResult.metadata.originalType?.let { originalType ->
                        propertyValueType(typeDiscoveryServices.host, originalType)
                            .let { propertyValueType -> TypeDiscovery.DiscoveredClass.classesOf(propertyValueType, PropertyType(kClass, extractionResult.result.name)).map(::schemaResult) }
                    } ?: emptyList()

                is ExtractionResult.Failure -> emptyList()
            }
        }
}


private
val handledPropertyTypes = setOf(Property::class, DirectoryProperty::class, RegularFileProperty::class, ListProperty::class, MapProperty::class)


private
fun isGradlePropertyType(type: KClassifier): Boolean = type in handledPropertyTypes


private
fun propertyValueType(host: SchemaBuildingHost, type: SupportedTypeProjection.SupportedType): SupportedTypeProjection.SupportedType {
    if (type.classifier == ListProperty::class) {
        return SupportedTypeProjection.SupportedType(List::class, isMarkedNullable = false, type.arguments)
    }

    if (type.classifier == MapProperty::class) {
        return SupportedTypeProjection.SupportedType(Map::class, isMarkedNullable = false, type.arguments)
    }

    fun searchClassHierarchyForPropertyType(type: SupportedTypeProjection.SupportedType): SupportedTypeProjection.SupportedType? {
        return when (val classifier = type.classifier) {
            Property::class -> type
            is KClass<*> -> classifier.allSupertypes.find { it.classifier == Property::class }?.asSupported(host)
                ?.orFailWith { return null }
            else -> null
        }
    }

    return when (val propertyType = searchClassHierarchyForPropertyType(type)) {
        is SupportedTypeProjection.SupportedType ->
            propertyType.arguments.getOrNull(0) as? SupportedTypeProjection.SupportedType ?: type
        else -> type
    }
}
