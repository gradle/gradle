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
import org.gradle.declarative.dsl.model.annotations.HiddenInDeclarativeDsl
import org.gradle.declarative.dsl.schema.DataProperty.PropertyMode
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty.DefaultPropertyMode
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor


interface PropertyExtractor {
    fun extractProperties(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean = { true }): Iterable<CollectedPropertyInformation>

    companion object {
        val none = object : PropertyExtractor {
            override fun extractProperties(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<CollectedPropertyInformation> = emptyList()
        }
    }
}


class CompositePropertyExtractor(internal val extractors: Iterable<PropertyExtractor>) : PropertyExtractor {
    override fun extractProperties(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<CollectedPropertyInformation> = buildList {
        val nameSet = mutableSetOf<String>()
        val predicateWithNamesFiltered: (String) -> Boolean = { propertyNamePredicate(it) && it !in nameSet }
        extractors.forEach { extractor ->
            val properties = extractor.extractProperties(kClass, predicateWithNamesFiltered)
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
    val isHiddenInDeclarativeDsl: Boolean,
    val isDirectAccessOnly: Boolean,
    val claimedFunctions: List<KFunction<*>>
)


class DefaultPropertyExtractor(private val includeMemberFilter: MemberFilter = isPublicAndRestricted) : PropertyExtractor {
    override fun extractProperties(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean) =
        (propertiesFromAccessorsOf(kClass, propertyNamePredicate) + memberPropertiesOf(kClass, propertyNamePredicate)).distinctBy { it }

    private
    fun propertiesFromAccessorsOf(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<CollectedPropertyInformation> {
        val functionsByName = kClass.memberFunctions.groupBy { it.name }
        val getters = functionsByName
            .filterKeys { it.startsWith("get") && it.substringAfter("get").firstOrNull()?.isUpperCase() == true }
            .mapValues { (_, functions) -> functions.singleOrNull { fn -> fn.parameters.all { it == fn.instanceParameter } } }
            .filterValues { it != null && includeMemberFilter.shouldIncludeMember(it) }
        return getters.mapNotNull { (name, getter) ->
            checkNotNull(getter)
            val nameAfterGet = name.substringAfter("get")
            val propertyName = nameAfterGet.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            if (!propertyNamePredicate(propertyName))
                return@mapNotNull null

            val type = getter.returnTypeToRefOrError(kClass)
            val isHidden = getter.annotations.any { it is HiddenInDeclarativeDsl }
            val isDirectAccessOnly = getter.annotations.any { it is AccessFromCurrentReceiverOnly }
            val setter = functionsByName["set$nameAfterGet"]?.find { fn -> fn.parameters.singleOrNull { it != fn.instanceParameter }?.type == getter.returnType }
            val mode = run {
                if (setter != null) DefaultPropertyMode.DefaultReadWrite else DefaultPropertyMode.DefaultReadOnly
            }
            CollectedPropertyInformation(propertyName, getter.returnType, type, mode, true, isHidden, isDirectAccessOnly, listOfNotNull(getter, setter))
        }
    }

    private
    fun memberPropertiesOf(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<CollectedPropertyInformation> = kClass.memberProperties
        .filter { property ->
            (includeMemberFilter.shouldIncludeMember(property) ||
                kClass.primaryConstructor?.parameters.orEmpty().any { it.name == property.name && it.type == property.returnType })
                && property.visibility == KVisibility.PUBLIC
                && propertyNamePredicate(property.name)
        }.map { property -> kPropertyInformation(kClass, property) }

    private
    fun kPropertyInformation(kClass: KClass<*>, property: KProperty<*>): CollectedPropertyInformation {
        val isReadOnly = property !is KMutableProperty<*>
        val isHidden = property.annotationsWithGetters.any { it is HiddenInDeclarativeDsl }
        val isDirectAccessOnly = property.annotationsWithGetters.any { it is AccessFromCurrentReceiverOnly }
        return CollectedPropertyInformation(
            property.name,
            property.returnType,
            property.returnTypeToRefOrError(kClass),
            if (isReadOnly) DefaultPropertyMode.DefaultReadOnly else DefaultPropertyMode.DefaultReadWrite,
            hasDefaultValue = run {
                isReadOnly || property.annotationsWithGetters.any { it is HasDefaultValue }
            },
            isHiddenInDeclarativeDsl = isHidden,
            isDirectAccessOnly = isDirectAccessOnly,
            claimedFunctions = emptyList()
        )
    }
}
