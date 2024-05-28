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
import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Builder
import org.gradle.declarative.dsl.model.annotations.Configuring
import org.gradle.declarative.dsl.schema.DataConstructor
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DefaultDataBuilderFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataConstructor
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultDataTopLevelFunction
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.typeOf


interface FunctionExtractor {
    fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction>
    fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor>
    fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction?
}


class CompositeFunctionExtractor(internal val extractors: Iterable<FunctionExtractor>) : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        extractors.flatMapTo(mutableSetOf()) { it.memberFunctions(kClass, preIndex) }

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> =
        extractors.flatMapTo(mutableSetOf()) { it.constructors(kClass, preIndex) }

    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? =
        extractors.asSequence().mapNotNull { it.topLevelFunction(function, preIndex) }.firstOrNull()
}


operator fun FunctionExtractor.plus(other: FunctionExtractor): CompositeFunctionExtractor = CompositeFunctionExtractor(buildList {
    fun include(functionExtractor: FunctionExtractor) = when (functionExtractor) {
        is CompositeFunctionExtractor -> addAll(functionExtractor.extractors)
        else -> add(functionExtractor)
    }
    include(this@plus)
    include(other)
})


class DefaultFunctionExtractor(
    private val configureLambdas: ConfigureLambdaHandler,
    private val includeFilter: MemberFilter = isPublicAndRestricted,
) : FunctionExtractor {
    override fun memberFunctions(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
        val functionsClaimedByProperties = preIndex.getClaimedFunctions(kClass)
        return kClass.memberFunctions.filter {
            it.visibility == KVisibility.PUBLIC &&
                includeFilter.shouldIncludeMember(it) &&
                it !in functionsClaimedByProperties
        }.map { function -> memberFunction(kClass, function, preIndex, configureLambdas) }
    }

    override fun constructors(kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> =
        kClass.constructors.filter { it.visibility == KVisibility.PUBLIC && includeFilter.shouldIncludeMember(it) }
            .map { constructor ->
                constructor(constructor, kClass, preIndex)
            }

    override fun topLevelFunction(function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction =
        dataTopLevelFunction(function, preIndex)

    private
    fun memberFunction(
        inType: KClass<*>,
        function: KFunction<*>,
        preIndex: DataSchemaBuilder.PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): SchemaMemberFunction {
        val thisTypeRef = inType.toDataTypeRef()

        val returnType = function.returnType

        returnType.checkInScope(preIndex, inType, function)
        val returnClass = function.returnType.classifier as KClass<*>
        val fnParams = function.parameters

        val semanticsFromSignature = inferFunctionSemanticsFromSignature(function, inType, preIndex, configureLambdas)
        val maybeConfigureTypeRef = when (semanticsFromSignature) { // there is not necessarily a lambda parameter of this type: it might be an adding function with no lambda
            is FunctionSemantics.ConfigureSemantics -> semanticsFromSignature.configuredType
            else -> null
        }

        val params = fnParams
            .filterIndexed { index, param ->
                param != function.instanceParameter && run {
                    // is value parameter, not a configuring block:
                    val isNotLastParameter = index != fnParams.lastIndex
                    val isNotConfigureLambda = configureLambdas.getTypeConfiguredByLambda(param.type)?.let {
                            typeConfiguredByLambda -> param.parameterTypeToRefOrError(inType, function) { typeConfiguredByLambda } != maybeConfigureTypeRef
                    } ?: true
                    isNotLastParameter || isNotConfigureLambda
                }
            }
            .map { fnParam -> dataParameter(inType, function, fnParam, returnClass, semanticsFromSignature, preIndex) }

        val isDirectAccessOnly = function.annotations.any { it is AccessFromCurrentReceiverOnly }

        return if (semanticsFromSignature is FunctionSemantics.Builder) {
            DefaultDataBuilderFunction(
                thisTypeRef,
                function.name,
                isDirectAccessOnly,
                params.single(),
            )
        } else {
            DefaultDataMemberFunction(
                thisTypeRef,
                function.name,
                params,
                isDirectAccessOnly,
                semanticsFromSignature
            )
        }
    }

    private
    fun constructor(
        constructor: KFunction<Any>,
        kClass: KClass<*>,
        preIndex: DataSchemaBuilder.PreIndex
    ): DataConstructor {
        val params = constructor.parameters
        val dataParams = params.map { param ->
            dataParameter(kClass, constructor, param, kClass, FunctionSemanticsInternal.DefaultPure(kClass.toDataTypeRef()), preIndex)
        }
        return DefaultDataConstructor(dataParams, kClass.toDataTypeRef())
    }

    private
    fun dataTopLevelFunction(
        function: KFunction<*>,
        preIndex: DataSchemaBuilder.PreIndex
    ): DataTopLevelFunction {
        check(function.instanceParameter == null)

        val returnType = function.returnType
        returnType.checkInScope(preIndex, function = function)

        val returnTypeClassifier = function.returnType
        val semanticsFromSignature = FunctionSemanticsInternal.DefaultPure(function.returnTypeToRefOrError(null))

        val fnParams = function.parameters
        val params = fnParams.filterIndexed { index, _ ->
            index != fnParams.lastIndex || configureLambdas.getTypeConfiguredByLambda(returnTypeClassifier) == null
        }.map { dataParameter(null, function, it, function.returnType.toKClass(), semanticsFromSignature, preIndex) }

        return DefaultDataTopLevelFunction(
            function.javaMethod!!.declaringClass.`package`.name,
            function.name,
            params,
            semanticsFromSignature
        )
    }

    private
    fun dataParameter(
        receiver: KClass<*>?,
        function: KFunction<*>,
        fnParam: KParameter,
        returnClass: KClass<*>,
        functionSemantics: FunctionSemantics,
        preIndex: DataSchemaBuilder.PreIndex
    ): DataParameter {
        val paramType = fnParam.type
        paramType.checkInScope(preIndex, receiver, function)
        val paramSemantics = getParameterSemantics(functionSemantics, function, fnParam, returnClass, preIndex)
        return DefaultDataParameter(fnParam.name, fnParam.parameterTypeToRefOrError(receiver, function), fnParam.isOptional, paramSemantics)
    }

    private
    fun getParameterSemantics(
        functionSemantics: FunctionSemantics,
        function: KFunction<*>,
        fnParam: KParameter,
        returnClass: KClass<*>,
        preIndex: DataSchemaBuilder.PreIndex
    ): ParameterSemantics {
        val propertyNamesToCheck = buildList {
            if (functionSemantics is FunctionSemantics.Builder) add(function.name)
            if (functionSemantics is FunctionSemantics.NewObjectFunctionSemantics) fnParam.name?.let(::add)
        }
        propertyNamesToCheck.forEach { propertyName ->
            val isPropertyLike =
                preIndex.getAllProperties(returnClass).any { it.name == propertyName }
            if (isPropertyLike) {
                val storeProperty = checkNotNull(preIndex.getProperty(returnClass, propertyName))
                return ParameterSemanticsInternal.DefaultStoreValueInProperty(storeProperty)
            }
        }
        return ParameterSemanticsInternal.DefaultUnknown
    }

    private
    fun inferFunctionSemanticsFromSignature(
        function: KFunction<*>,
        inType: KClass<*>?,
        preIndex: DataSchemaBuilder.PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): FunctionSemantics {
        val lastParam = function.parameters.last()
        val configuredType = configureLambdas.getTypeConfiguredByLambda(lastParam.type)
        val blockRequirement = when {
            configuredType == null -> FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultNotAllowed
            lastParam.isOptional -> FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultOptional
            else -> FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
        }

        return when {
            function.annotations.any { it is Builder } -> {
                check(inType != null)
                FunctionSemanticsInternal.DefaultBuilder(function.returnTypeToRefOrError(inType))
            }

            function.annotations.any { it is Adding } -> {
                check(inType != null)

                check(function.returnType != typeOf<Unit>() || configureLambdas.getTypeConfiguredByLambda(function.parameters.last().type) == null) {
                    "an @Adding function with a Unit return type may not accept configuring lambdas"
                }

                FunctionSemanticsInternal.DefaultAddAndConfigure(function.returnTypeToRefOrError(inType), blockRequirement)
            }

            function.annotations.any { it is Configuring } -> {
                check(inType != null)

                val annotation = function.annotations.filterIsInstance<Configuring>().singleOrNull()
                check(annotation != null)
                val annotationPropertyName = annotation.propertyName
                val propertyName = annotationPropertyName.ifEmpty { function.name }

                check(configuredType != null) { "a @Configuring function must accept a configuring lambda" }

                val propertyType = preIndex.getPropertyType(inType, propertyName)
                check(propertyType == null || propertyType.isSubtypeOf(configuredType)) { "configure lambda type is inconsistent with property type" }

                val property = preIndex.getProperty(inType, propertyName)
                check(annotationPropertyName.isEmpty() || propertyType != null) { "a property name '$annotationPropertyName' is specified for @Configuring function but no such property was found" }

                val returnType = when (function.returnType) {
                    typeOf<Unit>() -> FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit
                    propertyType, configuredType -> FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultConfiguredObject
                    else -> error("cannot infer the return type of a configuring function; it must be Unit or the configured object type")
                }
                check(function.parameters.filter { it != function.instanceParameter }.size == 1) { "a configuring function may not accept any other parameters" }
                val accessor =
                    if (property != null) ConfigureAccessorInternal.DefaultProperty(property)
                    else ConfigureAccessorInternal.DefaultConfiguringLambdaArgument(lastParam.parameterTypeToRefOrError(inType, function) { configuredType })
                FunctionSemanticsInternal.DefaultAccessAndConfigure(accessor, returnType, blockRequirement)
            }

            else -> FunctionSemanticsInternal.DefaultPure(function.returnTypeToRefOrError(inType))
        }
    }

    private
    fun KType.toKClass() = (classifier ?: error("unclassifiable type $this is used in the schema")) as? KClass<*>
        ?: error("type $this classified as a non-class is used in the schema")
}
