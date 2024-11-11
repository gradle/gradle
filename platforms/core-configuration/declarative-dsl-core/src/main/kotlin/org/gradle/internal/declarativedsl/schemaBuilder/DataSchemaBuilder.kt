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

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultEnumClass
import org.gradle.internal.declarativedsl.analysis.DefaultExternalObjectProviderKey
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.fqName
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf


class DataSchemaBuilder(
    private val typeDiscovery: TypeDiscovery,
    private val propertyExtractor: PropertyExtractor,
    private val functionExtractor: FunctionExtractor
) {
    fun schemaFromTypes(
        topLevelReceiver: KClass<*>,
        types: Iterable<KClass<*>>,
        externalFunctions: List<KFunction<*>> = emptyList(),
        externalObjects: Map<FqName, KClass<*>> = emptyMap(),
        defaultImports: List<FqName> = emptyList(),
    ): AnalysisSchema {
        val preIndex = createPreIndex(types)

        val dataTypes = preIndex.types.map { createDataType(it, preIndex) }

        val extFunctions = externalFunctions.mapNotNull { functionExtractor.topLevelFunction(it, preIndex) }.associateBy { it.fqName }
        val extObjects = externalObjects.map { (key, value) -> key to DefaultExternalObjectProviderKey(value.toDataTypeRef()) }.toMap()

        val topLevelReceiverName = topLevelReceiver.fqName

        return DefaultAnalysisSchema(
            dataTypes.filterIsInstance<DataClass>().single { it.name == topLevelReceiverName },
            dataTypes.associateBy { it.name } + preIndex.syntheticTypes.associateBy { it.name },
            extFunctions,
            extObjects,
            defaultImports.toSet()
        )
    }

    private
    val KClass<*>.fqName
        get() = DefaultFqName.parse(qualifiedName!!)

    class PreIndex {
        private
        val properties = mutableMapOf<KClass<*>, MutableMap<String, DataProperty>>()

        private
        val propertyOriginalTypes = mutableMapOf<KClass<*>, MutableMap<String, KType>>()

        private
        val claimedFunctions = mutableMapOf<KClass<*>, MutableSet<KFunction<*>>>()

        private val mutableSyntheticTypes = mutableMapOf<String, DataClass>()

        fun getOrRegisterSyntheticType(id: String, produceType: () -> DataClass): DataClass =
            mutableSyntheticTypes.getOrPut(id, produceType)

        fun addType(kClass: KClass<*>) {
            properties.getOrPut(kClass) { mutableMapOf() }
            propertyOriginalTypes.getOrPut(kClass) { mutableMapOf() }
        }

        fun addProperty(kClass: KClass<*>, property: DataProperty, originalType: KType) {
            properties.getOrPut(kClass) { mutableMapOf() }[property.name] = property
            propertyOriginalTypes.getOrPut(kClass) { mutableMapOf() }[property.name] = originalType
        }

        fun claimFunction(kClass: KClass<*>, kFunction: KFunction<*>) {
            claimedFunctions.getOrPut(kClass) { mutableSetOf() }.add(kFunction)
        }

        val syntheticTypes: List<DataClass>
            get() = mutableSyntheticTypes.values.toList()

        val types: Iterable<KClass<*>>
            get() = properties.keys

        fun hasType(kClass: KClass<*>): Boolean = kClass in properties

        fun getAllProperties(kClass: KClass<*>): List<DataProperty> = properties[kClass]?.values.orEmpty().toList()

        fun getClaimedFunctions(kClass: KClass<*>): Set<KFunction<*>> = claimedFunctions[kClass].orEmpty()

        fun getProperty(kClass: KClass<*>, name: String) = properties[kClass]?.get(name)
        fun getPropertyType(kClass: KClass<*>, name: String) = propertyOriginalTypes[kClass]?.get(name)
    }

    @Suppress("NestedBlockDepth")
    private
    fun createPreIndex(types: Iterable<KClass<*>>): PreIndex {
        val typeDiscoveryServices = object : TypeDiscovery.TypeDiscoveryServices {
            override val propertyExtractor: PropertyExtractor
                get() = this@DataSchemaBuilder.propertyExtractor
        }

        val allTypesToVisit = buildSet {
            fun visit(type: KClass<*>) {
                if (add(type)) {
                    typeDiscovery.getClassesToVisitFrom(typeDiscoveryServices, type).forEach(::visit)
                }
            }
            types.forEach(::visit)
        }

        return PreIndex().apply {
            allTypesToVisit.forEach { type ->
                addType(type)
                val properties = propertyExtractor.extractProperties(type)
                properties.forEach {
                    it.claimedFunctions.forEach { f -> claimFunction(type, f) }
                    addProperty(
                        type,
                        DefaultDataProperty(it.name, it.returnType, it.propertyMode, it.hasDefaultValue, it.isHiddenInDeclarativeDsl, it.isDirectAccessOnly),
                        it.originalReturnType
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private
    fun createDataType(
        kClass: KClass<*>,
        preIndex: PreIndex,
    ): DataType.ClassDataType {
        return when {
            isEnum(kClass) -> {
                val entryNames = (kClass as KClass<Enum<*>>).java.enumConstants.map { it.name }
                DefaultEnumClass(kClass.fqName, kClass.java.name, entryNames)
            }
            else -> {
                val properties = preIndex.getAllProperties(kClass)
                val functions = functionExtractor.memberFunctions(kClass, preIndex).toList()
                val constructors = functionExtractor.constructors(kClass, preIndex).toList()
                DefaultDataClass(kClass.fqName, kClass.java.name, listOf(), supertypesOf(kClass), properties, functions, constructors)
            }
        }
    }

    private
    fun isEnum(kClass: KClass<*>): Boolean {
        return kClass.supertypes.any { it.isSubtypeOf(typeOf<Enum<*>>()) }
    }

    private
    fun supertypesOf(kClass: KClass<*>): Set<FqName> = buildSet {
        fun visit(supertype: KType) {
            val classifier = supertype.classifier as? KClass<*> ?: error("a supertype is not represented by KClass: $supertype")
            if (add(classifier.fqName)) {
                classifier.supertypes.forEach(::visit)
            }
        }
        kClass.supertypes.forEach(::visit)
    }
}
