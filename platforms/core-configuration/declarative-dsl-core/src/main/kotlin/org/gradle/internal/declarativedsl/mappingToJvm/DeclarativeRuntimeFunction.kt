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
import org.gradle.internal.declarativedsl.InstanceAndPublicType
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KTypeParameter
import kotlin.reflect.jvm.jvmErasure


interface DeclarativeRuntimeFunction {
    fun callBy(receiver: Any?, binding: Map<DataParameter, Any?>, hasLambda: Boolean): InvocationResult

    fun callByWithErrorHandling(receiver: Any?, binding: Map<DataParameter, Any?>, hasLambda: Boolean): InvocationResult {
        try {
            return callBy(receiver, binding, hasLambda)
        } catch (ite: InvocationTargetException) {
            throw ite.cause ?: ite
        }
    }

    data class InvocationResult(val result: InstanceAndPublicType, val capturedValue: InstanceAndPublicType)
}


internal
class ReflectionFunction(private val kFunction: KFunction<*>, private val configureLambdaHandler: ConfigureLambdaHandler) : DeclarativeRuntimeFunction {
    override fun callBy(receiver: Any?, binding: Map<DataParameter, Any?>, hasLambda: Boolean): DeclarativeRuntimeFunction.InvocationResult {
        val params = FunctionBinding.convertBinding(kFunction, receiver, binding, hasLambda, configureLambdaHandler)
            ?: error("signature of $kFunction does not match the arguments: $binding")
        val captor = params.valueCaptor
        val returnedValue = kFunction.callBy(adaptVarargs(params.map))
        val returnedPublicType = kFunction.returnType.jvmErasure
        val capturedValue = captor?.value ?: InstanceAndPublicType.NULL
        return DeclarativeRuntimeFunction.InvocationResult(InstanceAndPublicType.of(returnedValue, returnedPublicType), capturedValue)
    }

    /**
     * The DCL type inference makes no difference between primitive and boxed arrays, and when the inferred parameter type is a "vararg of ints", it
     * will always be the primitive array, e.g. [IntArray].
     * However, the type might have been inferred that way because the generic type specification had a type parameter substitution like
     * T := [Int].
     * In that case, the JVM method is generic and expects an object-typed [Array], so we need to convert the
     * argument array back from unboxed primitives to boxed values.
     */
    private fun adaptVarargs(bindingMap: Map<KParameter, Any?>): Map<KParameter, Any?> {
        fun isVarargWithGenericTypeArgument(kParameter: KParameter) =
            kParameter.isVararg && kParameter.type.arguments.singleOrNull()?.type?.classifier is KTypeParameter

        if (bindingMap.keys.none(::isVarargWithGenericTypeArgument))
            return bindingMap

        fun adaptVarargValueArrayToGenerics(valueArray: Any?) = when (valueArray) {
            is IntArray -> valueArray.toTypedArray()
            is ShortArray -> valueArray.toTypedArray()
            is ByteArray -> valueArray.toTypedArray()
            is LongArray -> valueArray.toTypedArray()
            is BooleanArray -> valueArray.toTypedArray()
            is CharArray -> valueArray.toTypedArray()
            is FloatArray -> valueArray.toTypedArray()
            is DoubleArray -> valueArray.toTypedArray()
            else -> valueArray
        }

        return bindingMap.mapValues {
            if (isVarargWithGenericTypeArgument(it.key))
                adaptVarargValueArrayToGenerics(it.value)
            else it.value
        }
    }
}
