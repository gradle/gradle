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

import com.h0tk3y.kotlin.staticObjectNotation.Adding
import com.h0tk3y.kotlin.staticObjectNotation.Builder
import com.h0tk3y.kotlin.staticObjectNotation.Configuring
import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ConfigureAccessor
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataBuilderFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataConstructorSignature
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataMemberFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataParameter
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataProperty
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataTopLevelFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataType
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ExternalObjectProviderKey
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FunctionSemantics
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ParameterSemantics
import com.h0tk3y.kotlin.staticObjectNotation.analysis.SchemaMemberFunction
import com.h0tk3y.kotlin.staticObjectNotation.analysis.fqName
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.typeOf


class DataSchemaBuilder(private val dataClassSchemaProducer: DataClassSchemaProducer) {
    fun schemaFromTypes(
        topLevelReceiver: KClass<*>,
        types: List<KClass<*>>,
        externalFunctions: List<KFunction<*>> = emptyList(),
        externalObjects: Map<FqName, KClass<*>> = emptyMap(),
        defaultImports: List<FqName> = emptyList(),
        configureLambdas: ConfigureLambdaHandler = kotlinFunctionAsConfigureLambda,
    ): AnalysisSchema {
        val preIndex = createPreIndex(types)

        val dataTypes = preIndex.types.map { createDataType(it, preIndex, configureLambdas) }

        val extFunctions = externalFunctions.map { dataTopLevelFunction(it, preIndex, configureLambdas) }.associateBy { it.fqName }
        val extObjects = externalObjects.map { (key, value) -> key to ExternalObjectProviderKey(value.toDataTypeRef()) }.toMap()
        return AnalysisSchema(
            dataTypes.single { it.kClass == topLevelReceiver },
            dataTypes.associateBy { FqName.parse(it.kClass.qualifiedName!!) },
            extFunctions,
            extObjects,
            defaultImports.toSet(),
            configureLambdas
        )
    }

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
                    dataClassSchemaProducer.getOtherClassesToVisitFrom(type).forEach(::visit)
                }
            }
            types.forEach(::visit)
        }

        return PreIndex().apply {
            allTypesToVisit.forEach { type ->
                addType(type)
                val properties = dataClassSchemaProducer.extractPropertiesOf(type)
                properties.forEach { addProperty(type, DataProperty(it.name, it.returnType, it.isReadOnly, it.hasDefaultValue), it.originalReturnType) }
            }
        }
    }

    private fun createDataType(
        kClass: KClass<*>,
        preIndex: PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): DataType.DataClass<*> {
        val properties = preIndex.getAllProperties(kClass)

        val functions = dataClassSchemaProducer.getFunctionsToExtract(kClass)
            .filter { it.visibility == KVisibility.PUBLIC && !it.isIgnored }
            .map { function ->
                memberFunction(kClass, function, preIndex, configureLambdas)
            }
        return DataType.DataClass(kClass, properties, functions, constructors(kClass, preIndex))
    }

    private fun constructors(kClass: KClass<*>, preIndex: PreIndex): List<DataConstructorSignature> =
        dataClassSchemaProducer.getConstructorsToExtract(kClass).map { constructor ->
            val params = constructor.parameters
            val dataParams = params.map { param ->
                dataParameter(constructor, param, kClass, FunctionSemantics.Pure(kClass.toDataTypeRef()), preIndex)
            }
            DataConstructorSignature(dataParams)
        }

    private fun dataTopLevelFunction(
        function: KFunction<*>,
        preIndex: PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): DataTopLevelFunction {
        check(function.instanceParameter == null)

        val returnType = function.returnType
        checkInScope(returnType, preIndex)

        val returnTypeClassifier = function.returnType
        val semanticsFromSignature = FunctionSemantics.Pure(returnTypeClassifier.toDataTypeRefOrError())

        val fnParams = function.parameters
        val params = fnParams.filterIndexed { index, it ->
            index != fnParams.lastIndex || !configureLambdas.isConfigureLambda(returnTypeClassifier)
        }.map { dataParameter(function, it, function.returnType.toKClass(), semanticsFromSignature, preIndex) }

        return DataTopLevelFunction(
            function.javaMethod!!.declaringClass.packageName,
            function.name,
            params,
            semanticsFromSignature
        )
    }

    private fun inferFunctionSemanticsFromSignature(
        function: KFunction<*>,
        returnTypeClassifier: KType,
        inType: KClass<*>?,
        preIndex: PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): FunctionSemantics {
        return when {
            function.annotations.any { it is Builder } -> {
                check(inType != null)
                FunctionSemantics.Builder(returnTypeClassifier.toDataTypeRefOrError())
            }

            function.annotations.any { it is Adding } -> {
                check(inType != null)
                val hasConfigureLambda =
                    configureLambdas.isConfigureLambdaForType(function.returnType, function.parameters[function.parameters.lastIndex].type)
                FunctionSemantics.AddAndConfigure(returnTypeClassifier.toDataTypeRefOrError(), hasConfigureLambda)
            }
            function.annotations.any { it is Configuring } -> {
                check(inType != null)

                val annotation = function.annotations.filterIsInstance<Configuring>().singleOrNull()
                check(annotation != null)
                val propertyName = annotation.propertyName.ifEmpty { function.name }
                val kProperty = preIndex.getProperty(inType, propertyName)
                val originalType = preIndex.getPropertyType(inType, propertyName)
                check(kProperty != null)
                check(originalType != null)
                val property = preIndex.getProperty(inType, propertyName)
                check(property != null)

                val hasConfigureLambda =
                    configureLambdas.isConfigureLambdaForType(originalType, function.parameters.last().type)

                check(hasConfigureLambda)
                val returnType = when (function.returnType) {
                    typeOf<Unit>() -> FunctionSemantics.AccessAndConfigure.ReturnType.UNIT
                    originalType -> FunctionSemantics.AccessAndConfigure.ReturnType.CONFIGURED_OBJECT
                    else -> error("cannot infer the return type of a configuring function; it must be Unit or the configured object type")
                }
                FunctionSemantics.AccessAndConfigure(ConfigureAccessor.Property(inType.toDataTypeRef(), property), returnType)
            }

            else -> FunctionSemantics.Pure(returnTypeClassifier.toDataTypeRefOrError())
        }
    }

    private fun memberFunction(
        inType: KClass<*>,
        function: KFunction<*>,
        preIndex: PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): SchemaMemberFunction {
        val thisTypeRef = inType.toDataTypeRef()

        val returnType = function.returnType

        checkInScope(returnType, preIndex)
        val returnClass = function.returnType.classifier as KClass<*>
        val fnParams = function.parameters

        val semanticsFromSignature = inferFunctionSemanticsFromSignature(function, function.returnType, inType, preIndex, configureLambdas)
        val maybeConfigureType = if (semanticsFromSignature is FunctionSemantics.AccessAndConfigure) {
            function.annotations.filterIsInstance<Configuring>().singleOrNull()?.propertyName?.let { preIndex.getPropertyType(inType, it) }
                ?: preIndex.getPropertyType(inType, function.name)
        } else null

        val params = fnParams
            .filterIndexed { index, it ->
                it != function.instanceParameter && run {
                    index != fnParams.lastIndex || !configureLambdas.isConfigureLambdaForType(maybeConfigureType ?: function.returnType, it.type)
                }
            }
            .map { fnParam -> dataParameter(function, fnParam, returnClass, semanticsFromSignature, preIndex) }

        return if (semanticsFromSignature is FunctionSemantics.Builder) {
            DataBuilderFunction(
                thisTypeRef,
                function.name,
                params.single()
            )
        } else {
            DataMemberFunction(
                thisTypeRef,
                function.name,
                params,
                semanticsFromSignature
            )
        }
    }

    private fun dataParameter(
        function: KFunction<*>,
        fnParam: KParameter,
        ownerClass: KClass<*>,
        functionSemantics: FunctionSemantics,
        preIndex: PreIndex
    ): DataParameter {
        val paramType = fnParam.type
        checkInScope(paramType, preIndex)
        val paramSemantics = getParameterSemantics(functionSemantics, function, fnParam, ownerClass, preIndex)
        return DataParameter(fnParam.name, paramType.toDataTypeRefOrError(), fnParam.isOptional, paramSemantics)
    }

    private fun getParameterSemantics(
        functionSemantics: FunctionSemantics,
        function: KFunction<*>,
        fnParam: KParameter,
        ownerClass: KClass<*>,
        preIndex: PreIndex
    ): ParameterSemantics {
        val propertyNamesToCheck = buildList {
            if (functionSemantics is FunctionSemantics.Builder) add(function.name)
            if (functionSemantics is FunctionSemantics.NewObjectFunctionSemantics) fnParam.name?.let(::add)
        }
        propertyNamesToCheck.forEach { propertyName ->
            val isPropertyLike =
                ownerClass.memberProperties.any {
                    it.visibility == KVisibility.PUBLIC &&
                        it.name == propertyName &&
                        it.returnType == fnParam.type
                }
            if (isPropertyLike) {
                val storeProperty = checkNotNull(preIndex.getProperty(ownerClass, propertyName))
                return ParameterSemantics.StoreValueInProperty(storeProperty)
            }
        }
        return ParameterSemantics.Unknown
    }

    private fun checkInScope(
        type: KType,
        typeScope: PreIndex
    ) {
        if (type.classifier?.isInScope(typeScope) != true) {
            error("type $type used in a function is not in schema scope")
        }
    }

    fun KClassifier.isInScope(typeScope: PreIndex) =
        isBuiltInType || this is KClass<*> && typeScope.hasType(this)

    val KClassifier.isBuiltInType: Boolean
        get() = when (this) {
            Int::class, String::class, Boolean::class, Long::class, Unit::class -> true
            else -> false
        }

    val KFunction<*>.isIgnored: Boolean
        get() = when (this.name) {
            // TODO: match precisely
            Any::toString.name, Any::equals.name, Any::hashCode.name -> true
            else -> false
        }
}
