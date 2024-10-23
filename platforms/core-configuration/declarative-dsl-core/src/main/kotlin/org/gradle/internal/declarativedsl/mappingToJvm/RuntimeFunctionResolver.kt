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

import org.gradle.declarative.dsl.schema.FunctionSemantics
import org.gradle.declarative.dsl.schema.SchemaFunction
import org.gradle.internal.declarativedsl.schemaBuilder.ConfigureLambdaHandler
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions


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

        receiverClass.memberFunctions.forEach { function ->
            if (function.name == schemaFunction.simpleName && FunctionBinding.convertBinding(function, Any(), parameterBindingStub, hasConfigureLambda, configureLambdaHandler) != null) {
                return RuntimeFunctionResolver.Resolution.Resolved(ReflectionFunction(function, configureLambdaHandler))
            }
        }

        return RuntimeFunctionResolver.Resolution.Unresolved
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
