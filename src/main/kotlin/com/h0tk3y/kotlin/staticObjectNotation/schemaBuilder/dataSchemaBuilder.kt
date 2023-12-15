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

package com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataProperty
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataType
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ExternalObjectProviderKey
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import com.h0tk3y.kotlin.staticObjectNotation.analysis.fqName
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType

class DataSchemaBuilder(
    private val typeDiscovery: TypeDiscovery,
    private val propertyExtractor: PropertyExtractor,
    private val functionExtractor: FunctionExtractor
) {
    fun schemaFromTypes(
        topLevelReceiver: KClass<*>,
        types: List<KClass<*>>,
        externalFunctions: List<KFunction<*>> = emptyList(),
        externalObjects: Map<FqName, KClass<*>> = emptyMap(),
        defaultImports: List<FqName> = emptyList(),
        configureLambdas: ConfigureLambdaHandler = kotlinFunctionAsConfigureLambda,
    ): AnalysisSchema {
        val preIndex = createPreIndex(types)

        val dataTypes = preIndex.types.map { createDataType(it, preIndex) }

        val extFunctions = externalFunctions.mapNotNull { functionExtractor.topLevelFunction(it, preIndex) }.associateBy { it.fqName }
        val extObjects = externalObjects.map { (key, value) -> key to ExternalObjectProviderKey(value.toDataTypeRef()) }.toMap()

        val topLevelReceiverName = topLevelReceiver.fqName

        return AnalysisSchema(
            dataTypes.single { it.name == topLevelReceiverName },
            dataTypes.associateBy { it.name },
            extFunctions,
            extObjects,
            defaultImports.toSet(),
            configureLambdas
        )
    }

    private val KClass<*>.fqName get() = FqName.parse(qualifiedName!!)

    class PreIndex {
        private val properties = mutableMapOf<KClass<*>, MutableMap<String, DataProperty>>()
        private val propertyOriginalTypes = mutableMapOf<KClass<*>, MutableMap<String, KType>>()

        fun addType(kClass: KClass<*>) {
            properties.getOrPut(kClass) { mutableMapOf() }
            propertyOriginalTypes.getOrPut(kClass) { mutableMapOf() }
        }

        fun addProperty(kClass: KClass<*>, property: DataProperty, originalType: KType) {
            properties.getOrPut(kClass) { mutableMapOf() }[property.name] = property
            propertyOriginalTypes.getOrPut(kClass) { mutableMapOf() }[property.name] = originalType
        }

        val types: Iterable<KClass<*>> get() = properties.keys

        fun hasType(kClass: KClass<*>): Boolean = kClass in properties

        fun getAllProperties(kClass: KClass<*>): List<DataProperty> = properties[kClass]?.values.orEmpty().toList()

        fun getProperty(kClass: KClass<*>, name: String) = properties[kClass]?.get(name)
        fun getPropertyType(kClass: KClass<*>, name: String) = propertyOriginalTypes[kClass]?.get(name)
    }

    private fun createPreIndex(types: List<KClass<*>>): PreIndex {
        val allTypesToVisit = buildSet {
            fun visit(type: KClass<*>) {
                if (add(type)) {
                    typeDiscovery.getOtherClassesToVisitFrom(type).forEach(::visit)
                }
            }
            types.forEach(::visit)
        }

        return PreIndex().apply {
            allTypesToVisit.forEach { type ->
                addType(type)
                val properties = propertyExtractor.extractProperties(type)
                properties.forEach {
                    addProperty(
                        type,
                        DataProperty(it.name, it.returnType, it.isReadOnly, it.hasDefaultValue, it.isHiddenInRestrictedDsl, it.isDirectAccessOnly),
                        it.originalReturnType
                    )
                }
            }
        }
    }

    private fun createDataType(
        kClass: KClass<*>,
        preIndex: PreIndex,
    ): DataType.DataClass {
        val properties = preIndex.getAllProperties(kClass)

        val functions = functionExtractor.memberFunctions(kClass, preIndex)
        val constructors = functionExtractor.constructors(kClass, preIndex)
        val name = kClass.fqName
        return DataType.DataClass(name, supertypesOf(kClass), properties, functions.toList(), constructors.toList())
    }

    private fun supertypesOf(kClass: KClass<*>): Set<FqName> = buildSet {
        fun visit(supertype: KType) {
            val classifier = supertype.classifier as? KClass<*> ?: error("a supertype is not represented by KClass: ${supertype}")
            if (add(classifier.fqName)) {
                classifier.supertypes.forEach(::visit)
            }
        }
        kClass.supertypes.forEach(::visit)
    }
}
