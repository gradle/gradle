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

import org.gradle.declarative.dsl.model.annotations.AccessFromCurrentReceiverOnly
import org.gradle.declarative.dsl.model.annotations.HasDefaultValue
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty.DefaultPropertyMode
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.full.allSuperclasses


interface PropertyExtractor {
    fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean = { true }): Iterable<PropertyExtractionResult>

    companion object {
        val none = object : PropertyExtractor {
            override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<PropertyExtractionResult> =
                emptyList()
        }
    }
}

typealias PropertyExtractionResult = ExtractionResult<DataProperty, PropertyExtractionMetadata>

data class PropertyExtractionMetadata(val fromMembers: List<SupportedCallable>, val originalType: SupportedTypeProjection.SupportedType?)

class CompositePropertyExtractor(internal val extractors: Iterable<PropertyExtractor>) : PropertyExtractor {
    override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<PropertyExtractionResult> = buildList {
        val nameSet = mutableSetOf<String>()
        val predicateWithNamesFiltered: (String) -> Boolean = { propertyNamePredicate(it) && it !in nameSet }
        extractors.forEach { extractor ->
            val properties = extractor.extractProperties(host, kClass, predicateWithNamesFiltered)
            addAll(properties)
            nameSet.addAll(properties.filterIsInstance<ExtractionResult.Extracted<DataProperty, PropertyExtractionMetadata>>().map { it.result.name })
        }
    }
}


operator fun PropertyExtractor.plus(other: PropertyExtractor): CompositePropertyExtractor = CompositePropertyExtractor(buildList {
    fun include(propertyExtractor: PropertyExtractor) = when (propertyExtractor) {
        is CompositePropertyExtractor -> addAll(propertyExtractor.extractors)
        else -> add(propertyExtractor)
    }
    include(this@plus)
    include(other)
})

class DefaultPropertyExtractor(
    val includePredicate: (owner: KClass<*>, callable: SupportedCallable) -> Boolean = { _, _ -> true }
) : PropertyExtractor {
    override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<PropertyExtractionResult> =
        propertiesFromAccessorsOf(host, kClass, propertyNamePredicate) + memberPropertiesOf(host, kClass, propertyNamePredicate)

    private
    fun propertiesFromAccessorsOf(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<PropertyExtractionResult> {
        val functions = host.classMembers(kClass).declarativeMembers.filter { it.kind == MemberKind.FUNCTION && includePredicate(kClass, it) }

        val functionsByName = functions.groupBy { it.name }

        val getters = functions
            .filter { it.isJavaBeanGetter }
            .groupBy { it.name }
            .mapValues { (_, functions) -> functions.singleOrNull { fn -> fn.parameters.isEmpty() } }
            .filterValues { it != null && it.returnType.classifier.isValidPropertyType }
        return getters.mapNotNull { (name, getter) ->
            checkNotNull(getter)
            host.inContextOfModelMember(getter.kCallable) {
                val nameAfterGet = name.substringAfter("get")
                val propertyName = nameAfterGet.replaceFirstChar { it.lowercase(Locale.getDefault()) }
                if (!propertyNamePredicate(propertyName)) {
                    return@inContextOfModelMember null
                }
                val setter = functionsByName["set$nameAfterGet"]?.find { fn -> fn.parameters.singleOrNull()?.type == getter.returnType }

                val canWrite = setter != null

                val typeInfo = getter.propertyTypeOrError(host)
                    .orFailWith {
                        host.recordMemberWithFailure(kClass, getter, it)
                        return@mapNotNull null
                    }

                checkPropertyModeAndNullability(host, canWrite, typeInfo)
                    .orFailWith {
                        host.recordMemberWithFailure(kClass, getter, it)
                        return@mapNotNull null
                    }

                val canRead = !typeInfo.isNullable

                val isDirectAccessOnly = getter.kCallable.annotations.any { it is AccessFromCurrentReceiverOnly }
                ExtractionResult.Extracted(
                    DefaultDataProperty(
                        propertyName,
                        typeInfo.typeRef,
                        DefaultPropertyMode.of(canRead, canWrite),
                        hasDefaultValue = true,
                        isDirectAccessOnly = isDirectAccessOnly,
                        isHiddenInDsl = false
                    ),
                    metadata = PropertyExtractionMetadata(listOfNotNull(getter, setter), getter.returnType)
                )
            }
        }
    }

    private
    fun memberPropertiesOf(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<PropertyExtractionResult> =
        host.classMembers(kClass).declarativeMembers.filter { it.kind.isProperty }.filter { property ->
            propertyNamePredicate(property.name) && includePredicate(kClass, property) && property.returnType.classifier.isValidPropertyType
        }.map { property ->
            host.inContextOfModelMember(property.kCallable) { kPropertyInformation(host, property) }
        }

    private
    fun kPropertyInformation(host: SchemaBuildingHost, property: SupportedCallable): PropertyExtractionResult {
        val annotationsWithGetters = property.kCallable.annotationsWithGetters
        val isDirectAccessOnly = annotationsWithGetters.any { it is AccessFromCurrentReceiverOnly }

        val canWrite = property.kind == MemberKind.MUTABLE_PROPERTY

        val typeInfo = property.propertyTypeOrError(host)
            .orFailWith { return ExtractionResult.of(it, PropertyExtractionMetadata(listOf(property), property.returnType)) }

        checkPropertyModeAndNullability(host, canWrite, typeInfo)
            .orFailWith { return ExtractionResult.of(it, PropertyExtractionMetadata(listOf(property), property.returnType)) }

        val canRead = !typeInfo.isNullable

        return ExtractionResult.Extracted(
            DefaultDataProperty(
                property.name,
                typeInfo.typeRef,
                DefaultPropertyMode.of(canRead, canWrite),
                hasDefaultValue = run {
                    !canWrite || annotationsWithGetters.none { it is HasDefaultValue && !it.value }
                },
                isHiddenInDsl = false,
                isDirectAccessOnly = isDirectAccessOnly,
            ),
            metadata = PropertyExtractionMetadata(listOf(property), property.returnType)
        )
    }

    private val KClassifier.isValidPropertyType: Boolean // FIXME: Property API gets in the way, we don't want to import Provider-typed properties
        get() = this is KClass<*> && allSuperclasses.none { it.java.name == "org.gradle.api.provider.Provider" }

    private fun checkPropertyModeAndNullability(host: SchemaBuildingHost, isWritable: Boolean, type: CollectedPropertyType): SchemaResult<Unit> {
        if (!isWritable && type.isNullable)
            return host.schemaBuildingFailure(SchemaBuildingIssue.UnsupportedNullableReadOnlyProperty)
        return schemaResult(Unit)
    }
}

/**
 * Provides the [typeRef] information of the imported property type.
 *
 * In addition, [isNullable] tells if the type of the property is originally declared as nullable.
 * If it is, it cannot be used as readable property, for example, in grouped value factory calls (`foo.bar()`)
 */
data class CollectedPropertyType(val originalType: KType, val typeRef: DataTypeRef, val isNullable: Boolean)

private fun SupportedCallable.propertyTypeOrError(host: SchemaBuildingHost): SchemaResult<CollectedPropertyType> = returnType.toKType().let { kType ->
    CollectedPropertyType(
        kType,
        host.withTag(SchemaBuildingTags.returnValueType(kType)) {
            host.typeRef(kType)
                .orFailWith { return it }
        },
        returnType.isMarkedNullable
    ).let(::schemaResult)
}
