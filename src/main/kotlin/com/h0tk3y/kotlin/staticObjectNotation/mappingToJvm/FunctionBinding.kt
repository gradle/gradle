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

package com.h0tk3y.kotlin.staticObjectNotation.mappingToJvm

import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataParameter
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.ConfigureLambdaHandler
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.instanceParameter

object FunctionBinding {
    fun convertBinding(
        kFunction: KFunction<*>,
        receiver: Any,
        arguments: Map<DataParameter, Any?>,
        configureLambdaHandler: ConfigureLambdaHandler
    ): Map<KParameter, Any?>? =
        buildMap(arguments.size + 1) {
            val namedArguments = arguments.mapKeys { (param, _) -> param.name }
            var used = 0

            if (kFunction.instanceParameter != null && kFunction.extensionReceiverParameter != null)
                error("member-extension functions are not supported")

            kFunction.instanceParameter?.let { put(it, receiver) }
            kFunction.parameters.forEach { param ->
                val paramName = param.name
                when {
                    param == kFunction.instanceParameter -> put(param, receiver)
                    param == kFunction.extensionReceiverParameter -> put(param, receiver)

                    configureLambdaHandler.isConfigureLambda(param.type) -> put(param, configureLambdaHandler.produceNoopConfigureLambda(param.type))
                    paramName != null && paramName in namedArguments -> {
                        put(param, namedArguments.getValue(paramName))
                        used++
                    }

                    param.isOptional -> Unit
                    else -> return null
                }
            }

            if (used < namedArguments.size)
                return null
        }
}
