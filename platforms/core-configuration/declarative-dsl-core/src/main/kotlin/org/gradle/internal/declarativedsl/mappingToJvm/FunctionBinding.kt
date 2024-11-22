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
import org.gradle.internal.declarativedsl.analysis.interpretationCheck
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.extensionReceiverParameter
import kotlin.reflect.full.instanceParameter


object FunctionBinding {
    fun convertBinding(
        kFunction: KFunction<*>,
        receiver: Any,
        arguments: Map<DataParameter, Any?>,
        hasLambda: Boolean,
        configureLambdaHandler: ConfigureLambdaHandler
    ): Binding? {
        var captor: ConfigureLambdaHandler.ValueCaptor? = null
        val map = buildMap(arguments.size + 1) {
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

                    (hasLambda || param.isOptional) && configureLambdaHandler.getTypeConfiguredByLambda(param.type) != null -> {
                        val newCaptor = configureLambdaHandler.produceValueCaptor(param.type, configureLambdaHandler.getTypeConfiguredByLambda(param.type)!!)
                        interpretationCheck(captor == null) { "multiple lambda argument captors are not supported" }
                        captor = newCaptor
                        put(param, newCaptor.lambda)
                    }
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

            if (hasLambda && captor == null)
                return null
        }
        return Binding(map, captor)
    }

    data class Binding(
        val map: Map<KParameter, Any?>,
        val valueCaptor: ConfigureLambdaHandler.ValueCaptor?
    )
}
