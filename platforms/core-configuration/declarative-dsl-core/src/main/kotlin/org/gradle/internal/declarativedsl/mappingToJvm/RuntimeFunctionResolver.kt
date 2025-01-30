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

import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.hasDeclarativeAnnotation
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.javaType


interface RuntimeFunctionResolver {
    fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction): Resolution

    sealed interface Resolution {
        data class Resolved(val function: DeclarativeRuntimeFunction) : Resolution
        data object Unresolved : Resolution
    }
}


class MemberFunctionResolver(private val configureLambdaHandler: ConfigureLambdaHandler) : RuntimeFunctionResolver {
    override fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction): RuntimeFunctionResolver.Resolution {
        // TODO: `convertBinding` is invoked here without actual arguments or receiver instance; the actual function repeats the binding conversion afterwards; this probably needs reshaping
        val parameterBindingStub = schemaFunction.parameters.associateWith { Any() }
        val hasConfigureLambda = (schemaFunction.semantics as? FunctionSemantics.ConfigureSemantics)?.configureBlockRequirement?.allows ?: false

        fun signature(function: KFunction<*>): String {
            return function::class.memberProperties.first { it.name == "signature" }.apply { isAccessible = true }.call(function) as String
            // It is not very nice that we rely on this internal signature, but we don't have much of a choice...
            // We have also tried using the "descriptor" field and the "overriddenDescriptors" and/or "overriddenFunctions" from that,
            // but it's even uglier with a lot of weird lazy types involved and the code for doing so becomes very brittle.
        }

        fun matchesAnnotationsRecursively(function: KFunction<*>, receiverClass: KClass<*>, predicate: (Annotation) -> Boolean): Boolean =
            if (function.annotations.any(predicate)) {
                true
            } else {
                val functionSignature = signature(function)
                receiverClass.allSuperclasses.any { parentClass ->
                    val parentFunction = parentClass.memberFunctions.firstOrNull { signature(it) == functionSignature }
                    parentFunction?.annotations?.any(predicate) ?: false
                }
            }

        val resolutions = receiverClass.memberFunctions
            .filter { function -> function.name == schemaFunction.simpleName && FunctionBinding.convertBinding(function, Any(), parameterBindingStub, hasConfigureLambda, configureLambdaHandler) != null }
            .filter { f -> matchesAnnotationsRecursively(f, receiverClass, hasDeclarativeAnnotation) }
            .toList()

        return when {
            resolutions.isEmpty() -> RuntimeFunctionResolver.Resolution.Unresolved
            resolutions.size == 1 -> RuntimeFunctionResolver.Resolution.Resolved(ReflectionFunction(resolutions[0], configureLambdaHandler))
            else -> {
                val refinedResolutions = resolutions.filter { function -> parametersMatch(function, schemaFunction) }
                val finalResolution = if (refinedResolutions.size == 1) refinedResolutions[0] else
                    error("Failed disambiguating between following functions (matches ${refinedResolutions.size}): ${resolutions.joinToString(prefix = "\n\t", separator = "\n\t") { f -> f.toString() }}")
                return RuntimeFunctionResolver.Resolution.Resolved(ReflectionFunction(finalResolution, configureLambdaHandler))
            }
        }

    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun parametersMatch(function: KFunction<*>, schemaFunction: SchemaFunction): Boolean {
        val actualParameters = function.parameters.subList(if (function.instanceParameter == null) 0 else 1, function.parameters.size)
        return if (actualParameters.size == schemaFunction.parameters.size) {
            actualParameters.zip(schemaFunction.parameters).all { (kp, dp) ->
                kp.type.javaType.typeName == dp.type.typeName()
            }
        } else {
            false
        }
    }

    private fun DataTypeRef.typeName(): String = when (this) {
        is DataTypeRef.Name -> fqName.qualifiedName
        is DataTypeRef.Type -> when (dataType) {
            is DataType.ClassDataType -> (dataType as DataType.ClassDataType).javaTypeName
            is DataType.ConstantType<*> -> (dataType as DataType.ConstantType<*>).constantType.name
            is DataType.NullType -> error("function parameter type should never be NULL")
            is DataType.UnitType -> error("function parameter type should never be UNIT")
        }
    }
}


class CompositeFunctionResolver(private val resolvers: List<RuntimeFunctionResolver>) : RuntimeFunctionResolver {
    override fun resolve(receiverClass: KClass<*>, schemaFunction: SchemaFunction): RuntimeFunctionResolver.Resolution {
        resolvers.forEach {
            val resolution = it.resolve(receiverClass, schemaFunction)
            if (resolution is RuntimeFunctionResolver.Resolution.Resolved)
                return resolution
        }
        return RuntimeFunctionResolver.Resolution.Unresolved
    }
}
