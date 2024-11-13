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

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.declarative.dsl.model.annotations.AccessFromCurrentReceiverOnly
import org.gradle.declarative.dsl.model.annotations.HiddenInDeclarativeDsl
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.evaluationSchema.AnalysisSchemaComponent
import org.gradle.internal.declarativedsl.schemaBuilder.CollectedPropertyInformation
import org.gradle.internal.declarativedsl.schemaBuilder.MemberFilter
import org.gradle.internal.declarativedsl.schemaBuilder.PropertyExtractor
import org.gradle.internal.declarativedsl.schemaBuilder.TypeDiscovery
import org.gradle.internal.declarativedsl.schemaBuilder.annotationsWithGetters
import org.gradle.internal.declarativedsl.schemaBuilder.isPublicAndRestricted
import org.gradle.internal.declarativedsl.schemaBuilder.returnTypeToRefOrError
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.typeOf


/**
 * Extracts schema properties from Kotlin properties and Java getters returning the type [Property].
 * Ensures that the return types of these properties get discovered during type discovery.
 */
internal
class GradlePropertyApiAnalysisSchemaComponent : AnalysisSchemaComponent {
    private
    val propertyExtractor = GradlePropertyApiPropertyExtractor(isPublicAndRestricted)

    override fun propertyExtractors(): List<PropertyExtractor> = listOf(propertyExtractor)

    override fun typeDiscovery(): List<TypeDiscovery> = listOf(PropertyReturnTypeDiscovery())
}


private
class GradlePropertyApiPropertyExtractor(
    private val includeMemberFilter: MemberFilter
) : PropertyExtractor {
    override fun extractProperties(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): Iterable<CollectedPropertyInformation> =
        propertiesFromGettersOf(kClass, propertyNamePredicate) + memberPropertiesOf(kClass, propertyNamePredicate)

    private
    fun memberPropertiesOf(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<CollectedPropertyInformation> = kClass.memberProperties
        .filter { property ->
            (includeMemberFilter.shouldIncludeMember(property) ||
                kClass.primaryConstructor?.parameters.orEmpty().any { it.name == property.name && it.type == property.returnType }) &&
                property.visibility == KVisibility.PUBLIC &&
                isGradlePropertyType(property.returnType)
        }.map { property ->
            val isHidden = property.annotationsWithGetters.any { it is HiddenInDeclarativeDsl }
            val isDirectAccessOnly = property.annotationsWithGetters.any { it is AccessFromCurrentReceiverOnly }
            CollectedPropertyInformation(
                property.name,
                property.returnType,
                property.returnTypeToRefOrError(kClass) { propertyValueType(property.returnType) },
                DefaultDataProperty.DefaultPropertyMode.DefaultWriteOnly,
                hasDefaultValue = false,
                isHiddenInDeclarativeDsl = isHidden,
                isDirectAccessOnly = isDirectAccessOnly,
                claimedFunctions = emptyList()
            )
        }.filter { propertyNamePredicate(it.name) }

    private
    fun propertiesFromGettersOf(kClass: KClass<*>, propertyNamePredicate: (String) -> Boolean): List<CollectedPropertyInformation> {
        val functionsByName = kClass.memberFunctions.groupBy { it.name }
        val getters = functionsByName
            .filterKeys { it.startsWith("get") && it.substringAfter("get").firstOrNull()?.isUpperCase() == true }
            .mapValues { (_, functions) -> functions.singleOrNull { fn -> fn.parameters.all { it == fn.instanceParameter } } }
            .filterValues { it != null && includeMemberFilter.shouldIncludeMember(it) && isGradlePropertyType(it.returnType) }
        return getters.map { (name, getter) ->
            checkNotNull(getter)
            val nameAfterGet = name.substringAfter("get")
            val propertyName = nameAfterGet.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            val type = getter.returnTypeToRefOrError(kClass) { propertyValueType(getter.returnType) }
            val isHidden = getter.annotations.any { it is HiddenInDeclarativeDsl }
            val isDirectAccessOnly = getter.annotations.any { it is AccessFromCurrentReceiverOnly }
            CollectedPropertyInformation(
                propertyName, getter.returnType, type, DefaultDataProperty.DefaultPropertyMode.DefaultWriteOnly, false, isHidden, isDirectAccessOnly,
                claimedFunctions = listOf(getter)
            )
        }.filter { propertyNamePredicate(it.name) }
    }
}


private
class PropertyReturnTypeDiscovery : TypeDiscovery {
    override fun getClassesToVisitFrom(typeDiscoveryServices: TypeDiscovery.TypeDiscoveryServices, kClass: KClass<*>): Iterable<KClass<*>> =
        typeDiscoveryServices.propertyExtractor.extractProperties(kClass).mapNotNullTo(mutableSetOf()) {
            propertyValueType(it.originalReturnType).classifier as? KClass<*>
        }
}


private
val handledPropertyTypes = setOf(Property::class, DirectoryProperty::class) // TODO: add RegularFileProperty?


private
fun isGradlePropertyType(type: KType): Boolean = type.classifier in handledPropertyTypes


private
fun propertyValueType(type: KType): KType {
    return when (type.classifier) {
        Property::class -> type.arguments[0].type ?: error("expected a declared property type")
        DirectoryProperty::class -> typeOf<Directory>() // TODO: we can have a generic solution by recursively going through supertypes until we find what Property<T> it actually is
        else -> type
    }
}
