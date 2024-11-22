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

package org.gradle.internal.declarativedsl.schemaBuilder

import org.gradle.internal.declarativedsl.analysis.interpretationCheck
import org.gradle.internal.declarativedsl.mappingToJvm.InstanceAndPublicType
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.typeOf


interface ConfigureLambdaHandler {
    fun getTypeConfiguredByLambda(type: KType): KType?
    fun isConfigureLambdaForType(configuredType: KType, maybeLambdaType: KType): Boolean
    fun produceValueCaptor(lambdaType: KType, typeConfiguredByLambda: KType): ValueCaptor

    class ValueCaptor(
        val lambda: Any,
        private val lazyValue: Lazy<InstanceAndPublicType>
    ) {
        val value: InstanceAndPublicType
            get() = lazyValue.value
    }
}


operator fun ConfigureLambdaHandler.plus(other: ConfigureLambdaHandler) =
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
    override fun getTypeConfiguredByLambda(type: KType): KType? = if (isConfigureLambdaType(type)) type.arguments[0].type else null
    override fun isConfigureLambdaForType(configuredType: KType, maybeLambdaType: KType): Boolean = isConfigureLambdaType(maybeLambdaType, configuredType)
    override fun produceValueCaptor(lambdaType: KType, typeConfiguredByLambda: KType): ConfigureLambdaHandler.ValueCaptor {
        lateinit var value: Any
        val lambda: Function1<Any, Unit> = { value = it }
        return ConfigureLambdaHandler.ValueCaptor(lambda, lazy { value to typeConfiguredByLambda.jvmErasure })
    }

    private
    fun isConfigureLambdaType(maybeLambdaType: KType) = maybeLambdaType.isSubtypeOf(typeOf<Function1<*, Unit>>())

    private
    fun isConfigureLambdaType(maybeLambdaType: KType, configuredType: KType) = maybeLambdaType.isSubtypeOf(configureLambdaTypeFor(configuredType))

    private
    fun configureLambdaTypeFor(configuredType: KType) =
        Function1::class.createType(
            listOf(
                KTypeProjection.contravariant(configuredType),
                KTypeProjection(KVariance.INVARIANT, Unit::class.createType())
            )
        )
}


class CompositeConfigureLambdas(internal val implementations: List<ConfigureLambdaHandler>) : ConfigureLambdaHandler {
    override fun getTypeConfiguredByLambda(type: KType): KType? =
        implementations.asSequence().mapNotNull { it.getTypeConfiguredByLambda(type) }.firstOrNull()

    override fun isConfigureLambdaForType(configuredType: KType, maybeLambdaType: KType): Boolean =
        implementations.any { it.isConfigureLambdaForType(configuredType, maybeLambdaType) }

    override fun produceValueCaptor(lambdaType: KType, typeConfiguredByLambda: KType): ConfigureLambdaHandler.ValueCaptor {
        val implementation = implementations.firstOrNull { it.getTypeConfiguredByLambda(lambdaType) != null } // TODO: can this be simplified now, that we know the type configured by the lambda?
        when (implementation) {
            null -> throw IllegalAccessException("none of the configure lambda handlers could produce an instance")
            else -> return implementation.produceValueCaptor(lambdaType, typeConfiguredByLambda)
        }
    }
}


fun treatInterfaceAsConfigureLambda(functionalInterface: KClass<*>): ConfigureLambdaHandler = object : ConfigureLambdaHandler {
    private
    val typeParameters = functionalInterface.typeParameters
    private
    val starProjectedType = functionalInterface.createType(typeParameters.map { KTypeProjection.STAR })
    private
    val staticallyKnownConfiguredType = if (functionalInterface.typeParameters.isEmpty())
        functionalInterface.declaredMemberFunctions.singleOrNull()?.let { fn -> fn.parameters.takeIf { it.size == 2 && it[0] == fn.instanceParameter }?.last()?.type } else null

    private
    fun interfaceTypeWithArgument(typeArgument: KType): KType {
        val inTypeProjection = KTypeProjection.contravariant(typeArgument)
        return functionalInterface.createType(functionalInterface.typeParameters.map { inTypeProjection })
    }

    init {
        check(functionalInterface.java.isInterface)
        interpretationCheck(typeParameters.size <= 1) {
            "${functionalInterface.simpleName} interpreted as a configure lambda, but generic types with more than one type parameter are not supported"
        }
    }

    override fun getTypeConfiguredByLambda(type: KType): KType? =
        if (type.isSubtypeOf(starProjectedType)) type.arguments.firstOrNull()?.type ?: staticallyKnownConfiguredType else null

    override fun isConfigureLambdaForType(configuredType: KType, maybeLambdaType: KType) =
        maybeLambdaType.isSubtypeOf(interfaceTypeWithArgument(configuredType))

    override fun produceValueCaptor(lambdaType: KType, typeConfiguredByLambda: KType): ConfigureLambdaHandler.ValueCaptor {
        require(lambdaType.isSubtypeOf(starProjectedType)) { "requested lambda type $lambdaType is not a subtype of the interface $starProjectedType" }
        return valueCaptor(typeConfiguredByLambda)
    }

    private
    fun valueCaptor(typeConfiguredByLambda: KType): ConfigureLambdaHandler.ValueCaptor {
        var value: Any? = null
        val lambda = Proxy.newProxyInstance(
            functionalInterface.java.classLoader,
            arrayOf(functionalInterface.java)
        ) { _, _, args ->
            value = args[0]
            return@newProxyInstance null
        }
        return ConfigureLambdaHandler.ValueCaptor(lambda, lazy { value to typeConfiguredByLambda.jvmErasure})
    }
}
