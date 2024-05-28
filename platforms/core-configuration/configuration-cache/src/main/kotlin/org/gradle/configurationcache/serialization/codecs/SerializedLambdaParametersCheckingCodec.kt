/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.configurationcache.serialization.beans.unsupportedFieldDeclaredTypes
import org.gradle.internal.serialize.graph.encodeBean
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.logUnsupported
import org.objectweb.asm.Type
import org.objectweb.asm.Type.getArgumentTypes
import java.lang.invoke.SerializedLambda
import kotlin.reflect.KClass


/**
 * In encoding a [SerializedLambda], this codec checks if the lambda parameter types are allowed in the configuration cache.
 * If some of them are not, it logs a problem.
 *
 * The lambda is encoded straightforwardly as a bean, and, upon decoding, the bean is expected to be the [SerializedLambda].
 * Beside the compliance checks, the values are encoded or decoded as beans without any special handling.
 *
 * @see [org.gradle.configurationcache.serialization.beans.unsupportedFieldDeclaredTypes]
 */
object SerializedLambdaParametersCheckingCodec : Codec<SerializedLambda> {
    override suspend fun ReadContext.decode(): SerializedLambda {
        return decodeBean() as SerializedLambda
    }

    override suspend fun WriteContext.encode(value: SerializedLambda) {
        checkLambdaCapturedArgTypesAreSupported(value)
        encodeBean(value)
    }

    private
    fun IsolateContext.checkLambdaCapturedArgTypesAreSupported(value: SerializedLambda) {
        val signature = value.implMethodSignature
        val paramTypes: Array<Type> = getArgumentTypes(signature)

        // Treat all parameters equally, regardless of whether they are implicit captured parameters or the lambda signature ones.
        // If any of them is of an unsupported type, a build that runs from the serialized state won't be able to provide an instance anyway.
        paramTypes.forEach { paramType ->
            unsupportedTypes[paramType]?.let { unsupportedKClass ->
                logUnsupportedLambdaParameterType(unsupportedKClass)
            }
        }
    }

    private
    fun IsolateContext.logUnsupportedLambdaParameterType(
        baseType: KClass<*>
    ) {
        logUnsupported(
            "serialize",
            // TODO: maybe introduce a separate section for the lambda serialization contract
            DocumentationSection.RequirementsDisallowedTypes
        ) {
            text(" a lambda that captures or accepts a parameter of type ")
            reference(baseType)
        }
    }

    private
    val unsupportedTypes: Map<Type, KClass<*>> =
        unsupportedFieldDeclaredTypes.associateBy { Type.getType(it.java) }
}
