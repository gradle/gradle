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

import org.gradle.internal.declarativedsl.analysis.DataParameter
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import kotlin.reflect.KFunction


interface RestrictedRuntimeFunction {
    fun callBy(receiver: Any, binding: Map<DataParameter, Any?>): InvocationResult

    data class InvocationResult(val result: Any?, val capturedValue: Any?)
}


internal
class ReflectionFunction(private val kFunction: KFunction<*>, private val configureLambdaHandler: ConfigureLambdaHandler) : RestrictedRuntimeFunction {
    override fun callBy(receiver: Any, binding: Map<DataParameter, Any?>): RestrictedRuntimeFunction.InvocationResult {
        val params = FunctionBinding.convertBinding(kFunction, receiver, binding, configureLambdaHandler)
            ?: error("signature of $kFunction does not match the arguments: $binding")
        val captor = params.valueCaptor
        return RestrictedRuntimeFunction.InvocationResult(kFunction.callBy(params.map), captor?.value)
    }
}
