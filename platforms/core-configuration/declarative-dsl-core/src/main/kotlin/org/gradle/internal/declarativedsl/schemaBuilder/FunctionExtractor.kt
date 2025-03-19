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
import org.gradle.internal.declarativedsl.analysis.DefaultVarargParameter
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.typeOf


interface FunctionExtractor {
    fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> = emptyList()
    fun constructors(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> = emptyList()
    fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null
}


class CompositeFunctionExtractor(internal val extractors: Iterable<FunctionExtractor>) : FunctionExtractor {
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        extractors.flatMapTo(mutableSetOf()) { it.memberFunctions(host, kClass, preIndex) }

    override fun constructors(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> =
        extractors.flatMapTo(mutableSetOf()) { it.constructors(host, kClass, preIndex) }

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? =
        extractors.asSequence().mapNotNull { it.topLevelFunction(host, function, preIndex) }.firstOrNull()
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
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
        val functionsClaimedByProperties = preIndex.getClaimedFunctions(kClass)
        return kClass.memberFunctions.filter {
            it.visibility == KVisibility.PUBLIC &&
                includeFilter.shouldIncludeMember(it) &&
                it !in functionsClaimedByProperties
        }.map { function -> memberFunction(host, kClass, function, preIndex, configureLambdas) }
    }

    override fun constructors(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<DataConstructor> =
        kClass.constructors.filter { it.visibility == KVisibility.PUBLIC && includeFilter.shouldIncludeMember(it) }
            .map { constructor ->
                constructor(host, constructor, kClass, preIndex)
            }

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction =
        dataTopLevelFunction(host, function, preIndex)

    private
    fun memberFunction(
        host: SchemaBuildingHost,
        inType: KClass<*>,
        function: KFunction<*>,
        preIndex: DataSchemaBuilder.PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): SchemaMemberFunction = host.inContextOfModelMember(function) {
        val thisTypeRef = host.withTag(SchemaBuildingTags.receiverType(inType)) {
            host.containerTypeRef(inType)
        }

        val returnClassifier = function.returnType.classifier ?: error("return type must have a classifier")
        val fnParams = function.parameters

        val semanticsFromSignature = inferFunctionSemanticsFromSignature(host, function, inType, preIndex, configureLambdas)
        val maybeConfigureTypeRef = when (semanticsFromSignature) { // there is not necessarily a lambda parameter of this type: it might be an adding function with no lambda
            is FunctionSemantics.ConfigureSemantics -> semanticsFromSignature.configuredType
            else -> null
        }

        val params = fnParams
            .filterIndexed { index, param ->
                param != function.instanceParameter && run {
                    // is value parameter, not a configuring block:
                    val isNotLastParameter = index != fnParams.lastIndex
                    isNotLastParameter || run isNotAConfigureLambda@{
                        configureLambdas.getTypeConfiguredByLambda(param.type)?.let { typeConfiguredByLambda ->
                            param.parameterTypeToRefOrError(host) { typeConfiguredByLambda } != maybeConfigureTypeRef
                        } ?: true
                    }
                }
            }
            .map { fnParam -> dataParameter(host, function, fnParam, returnClassifier, semanticsFromSignature, preIndex) }

        val isDirectAccessOnly = function.annotations.any { it is AccessFromCurrentReceiverOnly }

        if (semanticsFromSignature is FunctionSemantics.Builder) {
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
        host: SchemaBuildingHost,
        constructor: KFunction<Any>,
        kClass: KClass<*>,
        preIndex: DataSchemaBuilder.PreIndex
    ): DataConstructor = host.inContextOfModelMember(constructor) {
        val params = constructor.parameters
        val typeRef = host.containerTypeRef(kClass)
        val semantics = FunctionSemanticsInternal.DefaultPure(typeRef)
        val dataParams = params.map { param ->
            dataParameter(host, constructor, param, kClass, semantics, preIndex)
        }
        DefaultDataConstructor(dataParams, typeRef)
    }

    private
    fun dataTopLevelFunction(
        host: SchemaBuildingHost,
        function: KFunction<*>,
        preIndex: DataSchemaBuilder.PreIndex
    ): DataTopLevelFunction = host.inContextOfModelMember(function) {
        check(function.instanceParameter == null)

        val returnTypeClassifier = function.returnType
        val semanticsFromSignature = FunctionSemanticsInternal.DefaultPure(function.returnTypeToRefOrError(host))

        val fnParams = function.parameters
        val params = fnParams.filterIndexed { index, _ ->
            index != fnParams.lastIndex || configureLambdas.getTypeConfiguredByLambda(returnTypeClassifier) == null
        }.map { dataParameter(host, function, it, function.returnType.toKClass(), semanticsFromSignature, preIndex) }

        val javaDeclaringClass = function.javaMethod!!.declaringClass

        DefaultDataTopLevelFunction(
            javaDeclaringClass.`package`.name,
            javaDeclaringClass.name,
            function.name,
            params,
            semanticsFromSignature
        )
    }

    private
    fun dataParameter(
        host: SchemaBuildingHost,
        function: KFunction<*>,
        fnParam: KParameter,
        returnClassifier: KClassifier,
        functionSemantics: FunctionSemantics,
        preIndex: DataSchemaBuilder.PreIndex
    ): DataParameter {
        val paramSemantics = getParameterSemantics(functionSemantics, function, fnParam, returnClassifier, preIndex)

        return if (fnParam.isVararg) {
            DefaultVarargParameter(fnParam.name, fnParam.parameterTypeToRefOrError(host), fnParam.isOptional, paramSemantics)
        } else {
            DefaultDataParameter(fnParam.name, fnParam.parameterTypeToRefOrError(host), fnParam.isOptional, paramSemantics)
        }
    }

    private
    fun getParameterSemantics(
        functionSemantics: FunctionSemantics,
        function: KFunction<*>,
        fnParam: KParameter,
        returnClass: KClassifier,
        preIndex: DataSchemaBuilder.PreIndex
    ): ParameterSemantics {
        val propertyNamesToCheck = buildList {
            if (functionSemantics is FunctionSemantics.Builder) add(function.name)
            if (functionSemantics is FunctionSemantics.NewObjectFunctionSemantics) fnParam.name?.let(::add)
        }
        propertyNamesToCheck.forEach { propertyName ->
            val propertyMatchedByName = if (returnClass is KClass<*>) preIndex.getAllProperties(returnClass).find { it.name == propertyName } else null
            if (functionSemantics is FunctionSemantics.AccessAndConfigure) {
                return ParameterSemanticsInternal.DefaultIdentityKey(propertyMatchedByName)
            } else if (returnClass is KClass<*> && propertyMatchedByName != null) {
                val storeProperty = checkNotNull(preIndex.getProperty(returnClass, propertyName))
                return ParameterSemanticsInternal.DefaultStoreValueInProperty(storeProperty)
            } else return ParameterSemanticsInternal.DefaultUnknown
        }
        return if (functionSemantics is FunctionSemantics.AccessAndConfigure)
            ParameterSemanticsInternal.DefaultIdentityKey(null)
        else ParameterSemanticsInternal.DefaultUnknown
    }

    private
    fun inferFunctionSemanticsFromSignature(
        host: SchemaBuildingHost,
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
                FunctionSemanticsInternal.DefaultBuilder(function.returnTypeToRefOrError(host))
            }

            function.annotations.any { it is Adding } -> {
                check(inType != null)

                check(function.returnType != typeOf<Unit>() || configureLambdas.getTypeConfiguredByLambda(function.parameters.last().type) == null) {
                    "an @Adding function with a Unit return type may not accept configuring lambdas"
                }

                val returnType = function.returnTypeToRefOrError(host)
                configuredType?.let(host::checkConfiguredType)
                FunctionSemanticsInternal.DefaultAddAndConfigure(returnType, blockRequirement)
            }

            function.annotations.any { it is Configuring } -> {
                check(inType != null)

                val annotation = function.annotations.filterIsInstance<Configuring>().singleOrNull()
                check(annotation != null)
                val annotationPropertyName = annotation.propertyName
                val propertyName = annotationPropertyName.ifEmpty { function.name }

                check(configuredType != null) { "@Configuring function $function must accept a configuring lambda" }
                host.checkConfiguredType(configuredType)

                val propertyType = preIndex.getPropertyType(inType, propertyName)
                check(propertyType == null || propertyType.isSubtypeOf(configuredType)) {
                    "configure lambda type ($configuredType) is inconsistent with property type ($propertyType) in function $function"
                }

                val property = preIndex.getProperty(inType, propertyName)
                check(annotationPropertyName.isEmpty() || propertyType != null) {
                    "a property name ($annotationPropertyName) is specified for @Configuring function $function but no such property was found"
                }

                val returnType = when (function.returnType) {
                    typeOf<Unit>() -> FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit
                    propertyType, configuredType -> FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultConfiguredObject
                    else -> error("cannot infer the return type of a configuring function $function; it must be Unit or the configured object type")
                }
                val accessor =
                    if (property != null) ConfigureAccessorInternal.DefaultProperty(property)
                    else ConfigureAccessorInternal.DefaultConfiguringLambdaArgument(lastParam.parameterTypeToRefOrError(host) { configuredType })

                // TODO: when "definitely existing" objects get properly implemented, ensure that functions configuring them do not accept parameters
                FunctionSemanticsInternal.DefaultAccessAndConfigure(
                    accessor,
                    returnType,
                    blockRequirement
                )
            }

            else -> FunctionSemanticsInternal.DefaultPure(function.returnTypeToRefOrError(host))
        }
    }

    private
    fun KType.toKClass() = (classifier ?: error("unclassifiable type $this is used in the schema")) as? KClass<*>
        ?: error("type $this classified as a non-class is used in the schema")
}

private fun SchemaBuildingHost.checkConfiguredType(configuredType: KType) {
    withTag(SchemaBuildingTags.configuredType(configuredType)) {
        when (val classifier = configuredType.classifier) {
            is KClass<*> -> containerTypeRef(classifier)
            is KTypeParameter -> schemaBuildingFailure("Illegal usage of a type parameter")
            else -> Unit
        }
    }
}
