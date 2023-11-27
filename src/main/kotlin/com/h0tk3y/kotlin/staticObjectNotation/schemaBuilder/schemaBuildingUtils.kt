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

package com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder

import com.h0tk3y.kotlin.staticObjectNotation.types.isConfigureLambdaType
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf

interface ConfigureLambdaHandler {
    fun isConfigureLambda(type: KType): Boolean
    fun isConfigureLambdaForType(configuredType: KType, maybeLambdaType: KType): Boolean
    fun produceNoopConfigureLambda(lambdaType: KType): Any

    companion object {
    }
}

fun ConfigureLambdaHandler.plus(other: ConfigureLambdaHandler) =
    CompositeConfigureLambdas(buildList {
        when (this@plus) {
            is CompositeConfigureLambdas -> addAll(implementations)
            else -> add(this@plus)
        }
        when (other) {
            is CompositeConfigureLambdas -> addAll(other.implementations)
            else -> add(other)
        }
    })

val kotlinFunctionAsConfigureLambda: ConfigureLambdaHandler = object : ConfigureLambdaHandler {
    override fun isConfigureLambda(type: KType): Boolean = isConfigureLambdaType(type)
    override fun isConfigureLambdaForType(configuredType: KType, maybeLambdaType: KType): Boolean = isConfigureLambdaType(configuredType, maybeLambdaType)
    override fun produceNoopConfigureLambda(lambdaType: KType): Function1<Any, Unit> = {}
}

class CompositeConfigureLambdas(internal val implementations: List<ConfigureLambdaHandler>) : ConfigureLambdaHandler {
    override fun isConfigureLambda(type: KType): Boolean =
        implementations.any { it.isConfigureLambda(type) }

    override fun isConfigureLambdaForType(configuredType: KType, maybeLambdaType: KType): Boolean =
        implementations.any { it.isConfigureLambdaForType(configuredType, maybeLambdaType) }

    override fun produceNoopConfigureLambda(lambdaType: KType): Any {
        val implementation = implementations.find { it.isConfigureLambda(lambdaType) }
        when (implementation) {
            null -> throw IllegalAccessException("none of the configure lambda handlers could produce an instance")
            else -> return implementation.produceNoopConfigureLambda(lambdaType)
        }
    }

}

fun treatInterfaceAsConfigureLambda(functionalInterface: KClass<*>): ConfigureLambdaHandler = object : ConfigureLambdaHandler {
    private val typeParameters = functionalInterface.typeParameters
    private val starProjectedType = functionalInterface.createType(typeParameters.map { KTypeProjection.STAR })

    private fun interfaceTypeWithArgument(typeArgument: KType): KType {
        val inTypeProjection = KTypeProjection.contravariant(typeArgument)
        return functionalInterface.createType(functionalInterface.typeParameters.map { inTypeProjection })
    }

    init {
        check(functionalInterface.java.isInterface)
        check(typeParameters.size <= 1) { "generic types with more than one type parameter are not supported" }
    }

    override fun isConfigureLambda(type: KType): Boolean =
        type.isSubtypeOf(starProjectedType)

    override fun isConfigureLambdaForType(configuredType: KType, maybeLambdaType: KType) =
        maybeLambdaType.isSubtypeOf(interfaceTypeWithArgument(configuredType))

    override fun produceNoopConfigureLambda(lambdaType: KType): Any =
        if (lambdaType.isSubtypeOf(starProjectedType)) {
            noopConfigureLambdaInstance
        } else throw IllegalAccessException("requested lambda type $lambdaType is not a subtype of the interface $starProjectedType")

    private val noopConfigureLambdaInstance: Any by lazy {
        Proxy.newProxyInstance(
            functionalInterface.java.classLoader,
            arrayOf(functionalInterface.java)
        ) { _, _, _ -> Unit }
    }
}
