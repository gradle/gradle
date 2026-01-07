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
import org.gradle.declarative.dsl.schema.DataProperty.PropertyMode
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty.DefaultPropertyMode
import java.util.Locale
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KType
import kotlin.reflect.full.allSuperclasses


interface PropertyExtractor {
    fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean = { true }): Iterable<CollectedPropertyInformation>

    companion object {
        val none = object : PropertyExtractor {
            override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<CollectedPropertyInformation> = emptyList()
        }
    }
}

class CompositePropertyExtractor(internal val extractors: Iterable<PropertyExtractor>) : PropertyExtractor {
    override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<CollectedPropertyInformation> = buildList {
        val nameSet = mutableSetOf<String>()
        val predicateWithNamesFiltered: (String) -> Boolean = { propertyNamePredicate(it) && it !in nameSet }
        extractors.forEach { extractor ->
            val properties = extractor.extractProperties(host, kClass, predicateWithNamesFiltered)
            addAll(properties)
            nameSet.addAll(properties.map { it.name })
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


data class CollectedPropertyInformation(
    val name: String,
    val originalReturnType: KType,
    val returnType: DataTypeRef,
    val propertyMode: PropertyMode,
    val hasDefaultValue: Boolean,
    val isHiddenInDefinition: Boolean,
    val isDirectAccessOnly: Boolean,
    val claimedFunctions: List<KCallable<*>>
)

class DefaultPropertyExtractor : PropertyExtractor {
    override fun extractProperties(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean) =
        (propertiesFromAccessorsOf(host, kClass, propertyNamePredicate) + memberPropertiesOf(host, kClass, propertyNamePredicate)).distinctBy { it }

    private
    fun propertiesFromAccessorsOf(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<CollectedPropertyInformation> {
        val functions = host.classMembers(kClass).potentiallyDeclarativeMembers.filter { it.kind == MemberKind.FUNCTION }
        val functionsByName = functions.groupBy { it.name }

        val getters = functionsByName
            .filterKeys { it.startsWith("get") && it.substringAfter("get").firstOrNull()?.isUpperCase() == true }
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

                val type = getter.returnTypeToRefOrError(host)
                val isDirectAccessOnly = getter.kCallable.annotations.any { it is AccessFromCurrentReceiverOnly }
                CollectedPropertyInformation(
                    propertyName,
                    getter.returnType.toKType(),
                    type,
                    if (setter != null) DefaultPropertyMode.DefaultReadWrite else DefaultPropertyMode.DefaultReadOnly,
                    hasDefaultValue = true,
                    isHiddenInDefinition = false,
                    isDirectAccessOnly = isDirectAccessOnly,
                    claimedFunctions = listOfNotNull(getter.kCallable, setter?.kCallable)
                )
            }
        }
    }

    private
    fun memberPropertiesOf(host: SchemaBuildingHost, kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<CollectedPropertyInformation> =
        host.classMembers(kClass).potentiallyDeclarativeMembers.filter { it.kind.isProperty }.filter { property ->
            propertyNamePredicate(property.name) && property.returnType.classifier.isValidPropertyType
        }.map { property ->
            host.inContextOfModelMember(property.kCallable) {
                kPropertyInformation(host, property)
            }
        }

    private
    fun kPropertyInformation(host: SchemaBuildingHost, property: SupportedCallable): CollectedPropertyInformation {
        val isReadOnly = property.kind == MemberKind.READ_ONLY_PROPERTY
        val annotationsWithGetters = property.kCallable.annotationsWithGetters
        val isDirectAccessOnly = annotationsWithGetters.any { it is AccessFromCurrentReceiverOnly }
        return CollectedPropertyInformation(
            property.name,
            property.returnType.toKType(),
            property.returnTypeToRefOrError(host),
            if (isReadOnly) DefaultPropertyMode.DefaultReadOnly else DefaultPropertyMode.DefaultReadWrite,
            hasDefaultValue = run {
                isReadOnly || annotationsWithGetters.none { it is HasDefaultValue && !it.value }
            },
            isHiddenInDefinition = false,
            isDirectAccessOnly = isDirectAccessOnly,
            claimedFunctions = emptyList()
        )
    }

    private val KClassifier.isValidPropertyType: Boolean // FIXME: Property API gets in the way, we don't want to import Provider-typed properties
        get() = this is KClass<*> && allSuperclasses.none { it.java.name == "org.gradle.api.provider.Provider" }
}
