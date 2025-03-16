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

package org.gradle.internal.declarativedsl.mappingToJvm

import org.gradle.declarative.dsl.schema.DataParameter
import org.gradle.declarative.dsl.schema.DataTopLevelFunction
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.declarative.dsl.schema.SchemaMemberFunction
import org.gradle.internal.declarativedsl.Workarounds
import org.gradle.internal.declarativedsl.hasDeclarativeAnnotation
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.kotlinFunction


interface RuntimeFunctionResolver {
    fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction, scopeClassLoader: ClassLoader): Resolution

    sealed interface Resolution {
        data class Resolved(val function: DeclarativeRuntimeFunction) : Resolution
        data object Unresolved : Resolution
    }
}


interface RuntimeFunctionCandidatesProvider {
    fun candidatesForMember(ownerKClass: KClass<*>, schemaFunction: SchemaMemberFunction): List<KFunction<*>> = emptyList()
    fun candidatesForTopLevelFunction(schemaFunction: DataTopLevelFunction, scopeClassLoader: ClassLoader): List<KFunction<*>> = emptyList()
}


object DefaultRuntimeFunctionCandidatesProvider : RuntimeFunctionCandidatesProvider {
    override fun candidatesForMember(
        ownerKClass: KClass<*>,
        schemaFunction: SchemaMemberFunction
    ): List<KFunction<*>> = ownerKClass.memberFunctions.filter { it.name == schemaFunction.simpleName }

    override fun candidatesForTopLevelFunction(schemaFunction: DataTopLevelFunction, scopeClassLoader: ClassLoader): List<KFunction<*>> {
        val ownerClass = try {
            scopeClassLoader.loadClass(schemaFunction.ownerJvmTypeName)
        } catch (_: ClassNotFoundException) {
            return emptyList()
        }

        return ownerClass.methods.mapNotNull {
            if (it.name == schemaFunction.simpleName)
                it.kotlinFunction?.takeIf { kotlinFunction -> kotlinFunction.name == schemaFunction.simpleName }
            else null
        }
    }
}


class DefaultRuntimeFunctionResolver(
    private val configureLambdaHandler: ConfigureLambdaHandler,
    private val candidatesProvider: RuntimeFunctionCandidatesProvider = DefaultRuntimeFunctionCandidatesProvider
) : RuntimeFunctionResolver {
    override fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction, scopeClassLoader: ClassLoader): RuntimeFunctionResolver.Resolution {
        val parameterBindingStub = schemaFunction.parameters.associateWith { Any() }
        val hasConfigureLambda = (schemaFunction.semantics as? FunctionSemantics.ConfigureSemantics)?.configureBlockRequirement?.allows ?: false

        val matchingCandidates = run {
            val initialCandidates = when (schemaFunction) {
                is SchemaMemberFunction -> candidatesProvider.candidatesForMember(receiverClass, schemaFunction)
                is DataTopLevelFunction -> candidatesProvider.candidatesForTopLevelFunction(schemaFunction, scopeClassLoader)
                else -> emptyList()
            }
            initialCandidates.filter { isMatchBySignature(it, parameterBindingStub, hasConfigureLambda) }
        }

        return when (matchingCandidates.size) {
            0 -> RuntimeFunctionResolver.Resolution.Unresolved
            1 -> RuntimeFunctionResolver.Resolution.Resolved(ReflectionFunction(matchingCandidates[0], configureLambdaHandler))
            else -> {
                val refinedResolutions = buildList {
                    addAll(matchingCandidates)
                    removeIf { !parametersMatch(it, schemaFunction) }
                    if (size > 1) {
                        removeIf { !matchesAnnotationsRecursively(it, receiverClass, hasDeclarativeAnnotation) }
                    }
                }
                val finalResolution = if (refinedResolutions.size == 1) refinedResolutions[0] else
                    error(
                        "Failed disambiguating between following functions (matches ${refinedResolutions.size}): ${
                            matchingCandidates.joinToString(
                                prefix = "\n\t",
                                separator = "\n\t"
                            ) { f -> f.toString() }
                        }"
                    )
                return RuntimeFunctionResolver.Resolution.Resolved(ReflectionFunction(finalResolution, configureLambdaHandler))
            }
        }

    }

    private fun matchesAnnotationsRecursively(function: KFunction<*>, receiverClass: KClass<*>, predicate: (Annotation) -> Boolean): Boolean =
        if (function.annotations.any(predicate)) {
            true
        } else {
            val functionSignature = Workarounds.kFunctionSignature(function)
            receiverClass.allSuperclasses.any { parentClass ->
                val parentFunction = parentClass.memberFunctions.firstOrNull { Workarounds.kFunctionSignature(it) == functionSignature }
                parentFunction?.annotations?.any(predicate) ?: false
            }
        }

    private fun isMatchBySignature(
        kFunction: KFunction<*>,
        parameterBindingStub: Map<DataParameter, Any>,
        hasConfigureLambda: Boolean
    ): Boolean = FunctionBinding.convertBinding(kFunction, Any(), parameterBindingStub, hasConfigureLambda, configureLambdaHandler) != null

    private fun parametersMatch(function: KFunction<*>, schemaFunction: SchemaFunction): Boolean {
        val actualParameters = function.parameters.subList(if (function.instanceParameter == null) 0 else 1, function.parameters.size).filter { configureLambdaHandler.getTypeConfiguredByLambda(it.type) == null }
        return if (actualParameters.size == schemaFunction.parameters.size) {
            actualParameters.zip(schemaFunction.parameters).all { (kp, dp) ->
                when (val classifier = kp.type.classifier) {
                    is KTypeParameter -> (dp.type as? DataTypeRef.Type)?.dataType is DataType.TypeVariableUsage
                    // Just matching the classifier might be enough as overloads that only differ in generics are not allowed in Java
                    // TODO: they are allowed in Kotlin
                    is KClass<*> -> classifier.qualifiedName == dp.type.typeName()
                    else -> false
                }
            }
        } else {
            false
        }
    }

    private fun DataTypeRef.typeName(): String = when (this) {
        is DataTypeRef.Name -> fqName.qualifiedName
        is DataTypeRef.NameWithArgs -> fqName.qualifiedName
        is DataTypeRef.Type -> when (dataType) {
            is DataType.ClassDataType -> (dataType as DataType.ClassDataType).name.qualifiedName
            is DataType.ConstantType<*> -> (dataType as DataType.ConstantType<*>).constantType.kotlin.qualifiedName ?: "<no name>"
            is DataType.NullType -> error("function parameter type should never be NULL")
            is DataType.UnitType -> error("function parameter type should never be UNIT")
            is DataType.TypeVariableUsage -> TODO()
        }
    }
}


class CompositeFunctionResolver(private val resolvers: List<RuntimeFunctionResolver>) : RuntimeFunctionResolver {
    override fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction, scopeClassLoader: ClassLoader): RuntimeFunctionResolver.Resolution {
        resolvers.forEach {
            val resolution = it.resolve(receiverClass, schemaFunction, scopeClassLoader)
            if (resolution is RuntimeFunctionResolver.Resolution.Resolved)
                return resolution
        }
        return RuntimeFunctionResolver.Resolution.Unresolved
    }
}
