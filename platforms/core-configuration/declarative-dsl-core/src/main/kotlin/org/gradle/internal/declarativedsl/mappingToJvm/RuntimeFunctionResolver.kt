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

import org.gradle.internal.declarativedsl.analysis.ParameterValueBinding
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions


interface RuntimeFunctionResolver {
    fun resolve(receiverClass: KClass<*>, name: String, parameterValueBinding: ParameterValueBinding): Resolution

    sealed interface Resolution {
        data class Resolved(val function: DeclarativeRuntimeFunction) : Resolution
        data object Unresolved : Resolution
    }
}


class MemberFunctionResolver(private val configureLambdaHandler: ConfigureLambdaHandler) : RuntimeFunctionResolver {
    override fun resolve(receiverClass: KClass<*>, name: String, parameterValueBinding: ParameterValueBinding): RuntimeFunctionResolver.Resolution {
        receiverClass.memberFunctions.forEach { function ->
            // TODO: `convertBinding` is invoked here with the receiverClass passed as the receiver and non-resolved argument origins; this probably needs a different API shape
            if (function.name == name && FunctionBinding.convertBinding(function, receiverClass, parameterValueBinding.bindingMap, parameterValueBinding.providesConfigureBlock, configureLambdaHandler) != null) {
                return RuntimeFunctionResolver.Resolution.Resolved(ReflectionFunction(function, configureLambdaHandler))
            }
        }

        return RuntimeFunctionResolver.Resolution.Unresolved
    }
}


class CompositeFunctionResolver(private val resolvers: List<RuntimeFunctionResolver>) : RuntimeFunctionResolver {
    override fun resolve(receiverClass: KClass<*>, name: String, parameterValueBinding: ParameterValueBinding): RuntimeFunctionResolver.Resolution {
        resolvers.forEach {
            val resolution = it.resolve(receiverClass, name, parameterValueBinding)
            if (resolution is RuntimeFunctionResolver.Resolution.Resolved)
                return resolution
        }
        return RuntimeFunctionResolver.Resolution.Unresolved
    }
}
