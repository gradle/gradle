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
import org.gradle.declarative.dsl.model.annotations.ValueFactories
import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.ParameterSemantics
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.analysis.ConfigureAccessorInternal
import org.gradle.internal.declarativedsl.analysis.DefaultDataBuilderFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataMemberFunction
import org.gradle.internal.declarativedsl.analysis.DefaultDataParameter
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty
import org.gradle.internal.declarativedsl.analysis.DefaultDataProperty.DefaultPropertyMode
import org.gradle.internal.declarativedsl.analysis.DefaultDataTopLevelFunction
import org.gradle.internal.declarativedsl.analysis.DefaultVarargParameter
import org.gradle.internal.declarativedsl.analysis.FunctionSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.ParameterSemanticsInternal
import org.gradle.internal.declarativedsl.analysis.SchemaItemMetadataInternal.SchemaMemberOriginInternal.DefaultConfigureFromGetterOrigin
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.typeOf


interface FunctionExtractor {
    fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> = emptyList()
    fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null
}


class CompositeFunctionExtractor(internal val extractors: Iterable<FunctionExtractor>) : FunctionExtractor {
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> =
        extractors.flatMapTo(mutableSetOf()) { it.memberFunctions(host, kClass, preIndex) }

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
) : FunctionExtractor {
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
        val functionsClaimedByProperties = preIndex.getClaimedFunctions(kClass)

        val functions = host.classMembers(kClass).potentiallyDeclarativeMembers
            .filter {
                it.kind == MemberKind.FUNCTION
                    // TODO replace with a generic schema member inclusion tracking mechanism
                    && it.kCallable !in functionsClaimedByProperties
            }

        return functions.map { function -> memberFunction(host, kClass, function, preIndex, configureLambdas) }
    }

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction =
        dataTopLevelFunction(host, function)

    private
    fun memberFunction(
        host: SchemaBuildingHost,
        inType: KClass<*>,
        function: SupportedCallable,
        preIndex: DataSchemaBuilder.PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): SchemaMemberFunction = host.inContextOfModelMember(function.kCallable) {
        val thisTypeRef = host.withTag(SchemaBuildingTags.receiverType(inType)) {
            host.containerTypeRef(inType)
        }

        val returnClassifier = function.returnType.classifier

        checkReturnType(host, function.returnType)

        val fnParams = function.parameters

        val semanticsFromSignature = inferFunctionSemanticsFromSignature(host, function, inType, configureLambdas)

        val params = fnParams
            .filterIndexed { index, _ ->
                // is value parameter, not a configuring block:
                val isNotLastParameter = index != fnParams.lastIndex
                val lastParamIsNotConfigureBlock = semanticsFromSignature !is FunctionSemantics.ConfigureSemantics || !semanticsFromSignature.configureBlockRequirement.allows
                isNotLastParameter || lastParamIsNotConfigureBlock
            }
            .map { fnParam -> dataParameter(host, function, fnParam, returnClassifier, semanticsFromSignature, preIndex) }

        val isDirectAccessOnly = function.kCallable.annotations.any { it is AccessFromCurrentReceiverOnly }

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
    fun dataTopLevelFunction(
        host: SchemaBuildingHost,
        function: KFunction<*>
    ): DataTopLevelFunction = host.inContextOfModelMember(function) {
        check(function.instanceParameter == null)

        val returnTypeClassifier = function.returnType
        val semanticsFromSignature = FunctionSemanticsInternal.DefaultPure(function.returnTypeToRefOrError(host))

        val fnParams = function.parameters
        val params = fnParams.filterIndexed { index, _ ->
            index != fnParams.lastIndex || configureLambdas.getTypeConfiguredByLambda(returnTypeClassifier) == null
        }.map { topLevelFunctionParameter(host, it) }

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
    fun topLevelFunctionParameter(
        host: SchemaBuildingHost,
        fnParam: KParameter,
    ): DataParameter {
        val paramSemantics = ParameterSemanticsInternal.DefaultUnknown

        return if (fnParam.isVararg) {
            DefaultVarargParameter(fnParam.name, fnParam.parameterTypeToRefOrError(host), fnParam.isOptional, paramSemantics)
        } else {
            DefaultDataParameter(fnParam.name, fnParam.parameterTypeToRefOrError(host), fnParam.isOptional, paramSemantics)
        }
    }

    private fun checkReturnType(host: SchemaBuildingHost, type: SupportedTypeProjection.SupportedType) {
        host.withTag(SchemaBuildingTags.returnValueType(type)) {
            if ((type.classifier as? KClass<*>)?.isSubclassOf(Map::class) == true) {
                host.schemaBuildingFailure("Illegal type '${type.toKType()}': functions returning Map types are not supported")
            }
            if (type.classifier == Pair::class) {
                host.schemaBuildingFailure("Illegal type '${type.toKType()}': functions returning Pair are not supported")
            }
        }
    }

    private
    fun dataParameter(
        host: SchemaBuildingHost,
        function: SupportedCallable,
        fnParam: SupportedKParameter,
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
        function: SupportedCallable,
        fnParam: SupportedKParameter,
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
        function: SupportedCallable,
        inType: KClass<*>?,
        configureLambdas: ConfigureLambdaHandler
    ): FunctionSemantics {
        val lastParam = function.parameters.lastOrNull()

        val configuredType = lastParam?.let { configureLambdas.getTypeConfiguredByLambda(lastParam.type.toKType()) }
        val blockRequirement = when {
            configuredType == null -> FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultNotAllowed
            lastParam.isOptional -> FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultOptional
            else -> FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
        }

        return when {
            function.kCallable.annotations.any { it is Builder } -> {
                check(inType != null)
                FunctionSemanticsInternal.DefaultBuilder(function.returnTypeToRefOrError(host))
            }

            function.kCallable.annotations.any { it is Adding } -> {
                check(inType != null)

                val lastParamOrNull = function.parameters.lastOrNull()
                check(function.returnType.classifier != Unit::class || lastParamOrNull == null || configureLambdas.getTypeConfiguredByLambda(lastParamOrNull.type.toKType()) == null) {
                    "an @Adding function with a Unit return type may not accept configuring lambdas"
                }

                val returnType = function.returnTypeToRefOrError(host)
                configuredType?.let(host::checkConfiguredType)
                FunctionSemanticsInternal.DefaultAddAndConfigure(returnType, blockRequirement)
            }

            configuredType != null -> {
                check(inType != null)

                host.checkConfiguredType(configuredType)

                val returnType = when (function.returnType.toKType()) {
                    typeOf<Unit>() -> FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit
                    configuredType -> FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultConfiguredObject
                    else -> error("cannot infer the return type of a configuring function $function; it must be Unit or the configured object type")
                }
                val accessor = ConfigureAccessorInternal.DefaultConfiguringLambdaArgument(host.withTag(SchemaBuildingTags.parameter(lastParam)) { host.modelTypeRef(configuredType) })

                // TODO: when "definitely existing" objects get properly implemented, ensure that functions configuring them do not accept parameters
                FunctionSemanticsInternal.DefaultAccessAndConfigure(
                    accessor,
                    returnType,
                    host.withTag(SchemaBuildingTags.configuredType(function.returnType)) {
                        host.modelTypeRef(configuredType)
                    },
                    blockRequirement
                )
            }

            else -> FunctionSemanticsInternal.DefaultPure(function.returnTypeToRefOrError(host))
        }
    }
}


class GetterBasedConfiguringFunctionExtractor(private val propertyTypePredicate: (SupportedTypeProjection.SupportedType) -> Boolean) : FunctionExtractor {

    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<SchemaMemberFunction> {
        return (configuringFunctionsFromKotlinProperties(host, kClass) + configuringFunctionsFromGetters(host, kClass)).distinctBy { it.simpleName }
    }

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): DataTopLevelFunction? = null

    private fun configuringFunctionsFromGetters(host: SchemaBuildingHost, kClass: KClass<*>): List<SchemaMemberFunction> {
        val functions = host.classMembers(kClass).potentiallyDeclarativeMembers.filter { it.kind == MemberKind.FUNCTION && isNotValueFactoriesGetter(it) }

        val functionsByName = functions.groupBy { it.name }
        val gettersWithoutSetter = functionsByName
            .filterKeys { it.startsWith("get") && it.substringAfter("get").let { propName -> propName.firstOrNull()?.isUpperCase() == true && "set$propName" !in functionsByName.keys } }
            .mapValues { (_, functions) -> functions.singleOrNull { fn -> fn.parameters.isEmpty() } }
            .filterValues { it != null && propertyTypePredicate(it.returnType) }

        return gettersWithoutSetter.mapNotNull { (name, getter) ->
            checkNotNull(getter)
            host.inContextOfModelMember(getter.kCallable) {
                val nameAfterGet = name.substringAfter("get")
                val propertyName = nameAfterGet.replaceFirstChar { it.lowercase(Locale.getDefault()) }

                val type = getter.returnTypeToRefOrError(host)
                val property = DefaultDataProperty(
                    propertyName,
                    type,
                    DefaultPropertyMode.DefaultReadOnly,
                    hasDefaultValue = true,
                    isHiddenInDsl = false,
                    isDirectAccessOnly = false
                )

                configuringFunction(host, kClass, getter.name, propertyName, property)
            }
        }
    }

    private fun configuringFunctionsFromKotlinProperties(host: SchemaBuildingHost, kClass: KClass<*>): List<SchemaMemberFunction> {
        val properties =
            host.classMembers(kClass).potentiallyDeclarativeMembers
                .filter { it.kind == MemberKind.READ_ONLY_PROPERTY && propertyTypePredicate(it.returnType) && isNotValueFactoriesGetter(it) }

        return properties.map { property ->
            host.inContextOfModelMember(property.kCallable) {
                val type = property.returnTypeToRefOrError(host)
                val property = DefaultDataProperty(
                    property.name,
                    type,
                    DefaultPropertyMode.DefaultReadOnly,
                    hasDefaultValue = true,
                    isHiddenInDsl = false,
                    isDirectAccessOnly = false
                )

                configuringFunction(host, kClass, property.name, property.name, property)
            }
        }
    }

    private fun isNotValueFactoriesGetter(callable: SupportedCallable): Boolean = callable.kCallable.annotationsWithGetters.none { it is ValueFactories }

    private fun configuringFunction(
        host: SchemaBuildingHost,
        kClass: KClass<*>,
        originMemberName: String,
        propertyName: String,
        property: DefaultDataProperty
    ): DefaultDataMemberFunction {
        val thisTypeRef = host.withTag(SchemaBuildingTags.receiverType(kClass)) {
            host.containerTypeRef(kClass)
        }

        return DefaultDataMemberFunction(
            thisTypeRef,
            propertyName,
            emptyList(),
            false,
            FunctionSemanticsInternal.DefaultAccessAndConfigure(
                ConfigureAccessorInternal.DefaultProperty(property),
                FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit,
                property.valueType,
                FunctionSemanticsInternal.DefaultConfigureBlockRequirement.DefaultRequired
            ),
            metadata = listOf(DefaultConfigureFromGetterOrigin(kClass.java.name, originMemberName))
        )
    }
}

fun isValidNestedModelType(type: SupportedTypeProjection.SupportedType): Boolean {
    val classifier = type.classifier
    return when {
        (classifier as? KClass<*>)?.javaPrimitiveType != null -> false
        classifier == Unit::class -> false
        else -> true
    }
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
