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
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.jvmErasure


interface DeclarativeRuntimeFunction {
    fun callBy(receiver: Any, binding: Map<DataParameter, Any?>, hasLambda: Boolean): InvocationResult

    fun callByWithErrorHandling(receiver: Any, binding: Map<DataParameter, Any?>, hasLambda: Boolean): InvocationResult {
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
    override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>, hasLambda: Boolean): DeclarativeRuntimeFunction.InvocationResult {
        val params = FunctionBinding.convertBinding(kFunction, receiver, binding, hasLambda, configureLambdaHandler)
            ?: error("signature of $kFunction does not match the arguments: $binding")
        val captor = params.valueCaptor
        val returnedValue = kFunction.callBy(params.map)
        val returnedPublicType = kFunction.returnType.jvmErasure
        val capturedValue = captor?.value ?: nullInstanceAndPublicType
        return DeclarativeRuntimeFunction.InvocationResult(returnedValue to returnedPublicType, capturedValue)
    }
}
