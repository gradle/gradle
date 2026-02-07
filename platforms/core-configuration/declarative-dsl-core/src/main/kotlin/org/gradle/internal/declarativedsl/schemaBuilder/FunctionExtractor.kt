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
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.typeOf


interface FunctionExtractor {
    interface MemberFunctionExtractionHandler {
        fun onExtractedFunction(function: SchemaMemberFunction, claimedMembers: Iterable<SupportedCallable>)
        fun onFailedFunctionExtraction(failure: SchemaResult.Failure, member: SupportedCallable)
    }

    fun memberFunctions(
        host: SchemaBuildingHost,
        kClass: KClass<*>,
        preIndex: DataSchemaBuilder.PreIndex,
    ): Iterable<FunctionExtractionResult> =
        emptyList()

    fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): SchemaResult<DataTopLevelFunction>? =
        null
}

fun FunctionExtractor.MemberFunctionExtractionHandler.accept(from: SupportedCallable, functionResult: SchemaResult<SchemaMemberFunction>) {
    when (functionResult) {
        is SchemaResult.Failure -> onFailedFunctionExtraction(functionResult, from)
        is SchemaResult.Result -> onExtractedFunction(functionResult.result, listOf(from))
    }
}

typealias FunctionExtractionResult = ExtractionResult<SchemaMemberFunction, FunctionExtractionMetadata>

data class FunctionExtractionMetadata(val fromMembers: List<SupportedCallable>)

class CompositeFunctionExtractor(internal val extractors: Iterable<FunctionExtractor>) : FunctionExtractor {
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): Iterable<FunctionExtractionResult> =
        extractors.flatMap { it.memberFunctions(host, kClass, preIndex) }
            /**
             * It is possible that two declarations produce the exact same resulting function, e.g.:
             * * fun foo(action: Action<in Foo>)
             * * fun foo(action: Foo.() -> Unit)
             *
             * For now, we merge the results into a single schema function and postpone the ambiguity resolution to runtime (where it is going to fail).
             * TODO: investigate if we can simply import the ambiguous functions into the schema
             */
            .combineGroupsByResult { FunctionExtractionMetadata(it.flatMap { it.fromMembers }) }

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): SchemaResult<DataTopLevelFunction>? =
        extractors.firstNotNullOfOrNull { it.topLevelFunction(host, function, preIndex) }
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
    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): List<FunctionExtractionResult> =
        host.classMembers(kClass).declarativeMembers
            .filter { it.kind == MemberKind.FUNCTION && !it.isJavaBeanGetter }
            .map { ExtractionResult.of(memberFunction(host, kClass, it, preIndex, configureLambdas), FunctionExtractionMetadata(listOf(it))) }

    override fun topLevelFunction(host: SchemaBuildingHost, function: KFunction<*>, preIndex: DataSchemaBuilder.PreIndex): SchemaResult<DataTopLevelFunction> =
        dataTopLevelFunction(host, function)

    private
    fun memberFunction(
        host: SchemaBuildingHost,
        inType: KClass<*>,
        function: SupportedCallable,
        preIndex: DataSchemaBuilder.PreIndex,
        configureLambdas: ConfigureLambdaHandler
    ): SchemaResult<SchemaMemberFunction> = host.inContextOfModelMember(function.kCallable) {
        val thisTypeRef = host.withTag(SchemaBuildingTags.receiverType(inType)) {
            @OptIn(LossySchemaBuildingOperation::class) // the receiver should be a valid type to get to this point
            host.containerTypeRef(inType).orError()
        }

        val returnClassifier = function.returnType.classifier

        checkReturnType(host, function.returnType)
            .orFailWith { return it }

        val fnParams = function.parameters

        val semanticsFromSignature = inferFunctionSemanticsFromSignature(host, function, inType, configureLambdas)
            .orFailWith { return it }

        val params = fnParams
            .filterIndexed { index, _ ->
                // is value parameter, not a configuring block:
                val isNotLastParameter = index != fnParams.lastIndex
                val lastParamIsNotConfigureBlock = semanticsFromSignature !is FunctionSemantics.ConfigureSemantics || !semanticsFromSignature.configureBlockRequirement.allows
                isNotLastParameter || lastParamIsNotConfigureBlock
            }
            .map { fnParam -> dataParameter(host, function, fnParam, returnClassifier, semanticsFromSignature, preIndex) }
            .getAllOrFailWith {
                return it.first() // TODO: report all issues found in parameters as a "multi-failure"?
            }

        val isDirectAccessOnly = function.kCallable.annotations.any { it is AccessFromCurrentReceiverOnly }

        schemaResult(
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
        )
    }

    private
    fun dataTopLevelFunction(
        host: SchemaBuildingHost,
        function: KFunction<*>
    ): SchemaResult<DataTopLevelFunction> = host.inContextOfModelMember(function) {
        check(function.instanceParameter == null)

        val returnTypeClassifier = function.returnType
        val semanticsFromSignature = FunctionSemanticsInternal.DefaultPure(
            function.returnTypeRef(host)
                .orFailWith { return it }
        )

        val fnParams = function.parameters
        val params = fnParams
            .filterIndexed { index, _ -> index != fnParams.lastIndex || configureLambdas.getTypeConfiguredByLambda(returnTypeClassifier) == null }
            .map { topLevelFunctionParameter(host, it) }
            .getAllOrFailWith { return it.first() }

        val javaDeclaringClass = function.javaMethod!!.declaringClass

        DefaultDataTopLevelFunction(
            javaDeclaringClass.`package`.name,
            javaDeclaringClass.name,
            function.name,
            params,
            semanticsFromSignature
        ).let(::schemaResult)
    }

    private
    fun topLevelFunctionParameter(
        host: SchemaBuildingHost,
        fnParam: KParameter,
    ): SchemaResult<DataParameter> {
        val paramSemantics = ParameterSemanticsInternal.DefaultUnknown

        @Suppress("DuplicatedCode") // detects a duplicate that uses SupportedKParameter instead of KParameter
        return schemaResult(
            if (fnParam.isVararg) {
                DefaultVarargParameter(fnParam.name, fnParam.parameterTypeToRef(host).orFailWith { return it }, fnParam.isOptional, paramSemantics)
        } else {
                DefaultDataParameter(fnParam.name, fnParam.parameterTypeToRef(host).orFailWith { return it }, fnParam.isOptional, paramSemantics)
            }
        )
    }

    private fun checkReturnType(host: SchemaBuildingHost, type: SupportedTypeProjection.SupportedType): SchemaResult<Unit> =
        host.withTag(SchemaBuildingTags.returnValueType(type)) {
            when {
                (type.classifier as? KClass<*>)?.isSubclassOf(Map::class) == true -> {
                    host.schemaBuildingFailure(SchemaBuildingIssue.UnsupportedMapFactory(type))
                }

                type.classifier == Pair::class -> {
                    host.schemaBuildingFailure(SchemaBuildingIssue.UnsupportedPairFactory(type))
                }

                else -> schemaResult(Unit)
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
    ): SchemaResult<DataParameter> {
        val paramSemantics = getParameterSemantics(functionSemantics, function, fnParam, returnClassifier, preIndex)

        @Suppress("DuplicatedCode") // detects a duplicate that uses KParameter instead of SupportedKParameter
        return schemaResult(
            if (fnParam.isVararg) {
                DefaultVarargParameter(fnParam.name, fnParam.parameterTypeToRef(host).orFailWith { return it }, fnParam.isOptional, paramSemantics)
            } else {
                DefaultDataParameter(fnParam.name, fnParam.parameterTypeToRef(host).orFailWith { return it }, fnParam.isOptional, paramSemantics)
            }
        )
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
    ): SchemaResult<FunctionSemantics> {
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
                function.returnTypeRef(host).map(FunctionSemanticsInternal::DefaultBuilder)
            }

            function.kCallable.annotations.any { it is Adding } -> {
                check(inType != null)

                val lastParamOrNull = function.parameters.lastOrNull()
                check(function.returnType.classifier != Unit::class || lastParamOrNull == null || configureLambdas.getTypeConfiguredByLambda(lastParamOrNull.type.toKType()) == null) {
                    return host.schemaBuildingFailure(
                        SchemaBuildingIssue.UnitAddingFunctionWithLambda
                    )
                }

                val returnType = function.returnTypeRef(host).orFailWith { return it }
                if (configuredType != null) {
                    host.withTag(SchemaBuildingTags.configuredType(configuredType)) { host.containerTypeRef(configuredType) }
                        .orFailWith { return it }
                }
                schemaResult(FunctionSemanticsInternal.DefaultAddAndConfigure(returnType, blockRequirement))
            }

            configuredType != null -> {
                check(inType != null)

                val returnType = when (function.returnType.toKType()) {
                    typeOf<Unit>() -> FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultUnit
                    configuredType -> FunctionSemanticsInternal.DefaultAccessAndConfigure.DefaultReturnType.DefaultConfiguredObject
                    else -> error("cannot infer the return type of a configuring function $function; it must be Unit or the configured object type")
                }
                val configuredTypeRef = host.withTag(SchemaBuildingTags.configuredType(configuredType)) { host.containerTypeRef(configuredType) }
                    .orFailWith { return it }

                FunctionSemanticsInternal.DefaultAccessAndConfigure(
                    accessor = ConfigureAccessorInternal.DefaultConfiguringLambdaArgument(configuredTypeRef),
                    returnType,
                    configuredTypeRef,
                    blockRequirement
                ).let(::schemaResult)
            }

            else -> function.returnTypeRef(host).map(FunctionSemanticsInternal::DefaultPure)
        }
    }
}


class GetterBasedConfiguringFunctionExtractor(private val propertyTypePredicate: (SupportedTypeProjection.SupportedType) -> Boolean) : FunctionExtractor {

    override fun memberFunctions(host: SchemaBuildingHost, kClass: KClass<*>, preIndex: DataSchemaBuilder.PreIndex): List<FunctionExtractionResult> =
        listOf(
            configuringFunctionsFromKotlinProperties(host, kClass),
            configuringFunctionsFromGetters(host, kClass)
        ).flatten()

    private fun configuringFunctionsFromGetters(host: SchemaBuildingHost, kClass: KClass<*>): List<FunctionExtractionResult> {
        val functions = host.classMembers(kClass).declarativeMembers.filter {
            it.kind == MemberKind.FUNCTION &&
                memberIsInSchema(host, kClass, it) && // Only produce configuring functions for members that are already used as properties. TODO: express this in a more explicit way
                isNotValueFactoriesGetter(it)
        }

        val functionsByName = functions.groupBy { it.name }
        val gettersWithoutSetter = functionsByName
            .filterKeys { it.startsWith("get") && it.substringAfter("get").let { propName -> propName.firstOrNull()?.isUpperCase() == true && "set$propName" !in functionsByName.keys } }
            .mapValues { (_, functions) -> functions.singleOrNull { fn -> fn.parameters.isEmpty() } }
            .filterValues { it != null && propertyTypePredicate(it.returnType) }

        return gettersWithoutSetter.map { (name, getter) ->
            checkNotNull(getter)
            host.inContextOfModelMember(getter.kCallable) {
                val nameAfterGet = name.substringAfter("get")
                val propertyName = nameAfterGet.replaceFirstChar { it.lowercase(Locale.getDefault()) }

                val type = getter.returnTypeRef(host).orFailWith {
                    return@map ExtractionResult.of(it, FunctionExtractionMetadata(listOf(getter)))
                }
                val property = DefaultDataProperty(propertyName, type, DefaultPropertyMode.DefaultReadOnly, hasDefaultValue = true, isHiddenInDsl = false, isDirectAccessOnly = false)
                ExtractionResult.Extracted(configuringFunction(host, kClass, getter.name, propertyName, property), FunctionExtractionMetadata(listOf(getter)))
            }
        }
    }

    private fun configuringFunctionsFromKotlinProperties(host: SchemaBuildingHost, kClass: KClass<*>): List<FunctionExtractionResult> {
        val properties = host.classMembers(kClass).declarativeMembers
            .filter {
                it.kind == MemberKind.READ_ONLY_PROPERTY && propertyTypePredicate(it.returnType) &&
                    memberIsInSchema(host, kClass, it) && // Only produce configuring functions for members that are already used as properties. TODO: express this in a more explicit way
                    isNotValueFactoriesGetter(it)
            }

        return properties.map { property ->
            host.inContextOfModelMember(property.kCallable) {
                val type = property.returnTypeRef(host).orFailWith {
                    return@map ExtractionResult.of(it, FunctionExtractionMetadata(listOf(property)))
                }
                val dataProperty = DefaultDataProperty(
                    property.name,
                    type,
                    DefaultPropertyMode.DefaultReadOnly,
                    hasDefaultValue = true,
                    isHiddenInDsl = false,
                    isDirectAccessOnly = false
                )

                ExtractionResult.of(
                    schemaResult(configuringFunction(host, kClass, dataProperty.name, dataProperty.name, dataProperty)),
                    FunctionExtractionMetadata(listOf(property))
                )
            }
        }
    }

    private fun memberIsInSchema(
        host: SchemaBuildingHost,
        kClass: KClass<*>,
        callable: SupportedCallable
    ): Boolean = !host.isUnusedMember(kClass, callable) && callable !in host.membersWithFailures(kClass)

    private fun isNotValueFactoriesGetter(callable: SupportedCallable): Boolean = callable.kCallable.annotationsWithGetters.none { it is ValueFactories }

    private fun configuringFunction(
        host: SchemaBuildingHost,
        kClass: KClass<*>,
        originMemberName: String,
        propertyName: String,
        property: DefaultDataProperty
    ): DefaultDataMemberFunction {
        val thisTypeRef = host.withTag(SchemaBuildingTags.receiverType(kClass)) {
            @OptIn(LossySchemaBuildingOperation::class) // the receiver should be a valid type to get to this point
            host.containerTypeRef(kClass).orError()
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
