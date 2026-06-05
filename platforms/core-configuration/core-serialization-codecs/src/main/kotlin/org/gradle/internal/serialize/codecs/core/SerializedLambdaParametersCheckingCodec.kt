/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core

import org.gradle.internal.configuration.problems.DocumentationSection
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.serialize.beans.services.unsupportedFieldDeclaredTypes
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.logUnsupported
import org.gradle.internal.serialize.graph.withPropertyTrace
import org.gradle.internal.serialize.graph.writePropertyValue
import org.objectweb.asm.Type
import org.objectweb.asm.Type.getArgumentTypes
import java.lang.invoke.SerializedLambda
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import kotlin.reflect.KClass


/**
 * In encoding a [SerializedLambda], this codec checks if the lambda parameter types are allowed in the configuration cache.
 * If some of them are not, it logs a problem.
 *
 * The lambda is encoded straightforwardly as a bean, and, upon decoding, the bean is expected to be the [SerializedLambda].
 * Beside the compliance checks, the values are encoded or decoded as beans without any special handling.
 *
 * @see [org.gradle.internal.serialize.beans.services.unsupportedFieldDeclaredTypes]
 */
object SerializedLambdaParametersCheckingCodec : Codec<SerializedLambda> {
    override suspend fun ReadContext.decode(): SerializedLambda {
        return decodeBean() as SerializedLambda
    }

    override suspend fun WriteContext.encode(value: SerializedLambda) {
        checkLambdaCapturedArgTypesAreSupported(value)
        val lambdaTrace = PropertyTrace.SerializedLambda(
            implClass = value.implClass.replace('/', '.'),
            implMethodName = value.implMethodName,
            functionalInterfaceClass = value.functionalInterfaceClass.replace('/', '.'),
            instantiatedReturnType = Type.getReturnType(value.instantiatedMethodType).className,
            trace = trace
        )
        withPropertyTrace(lambdaTrace) {
            writeClass(SerializedLambda::class.java)
            for (field in SERIALIZED_LAMBDA_FIELDS) {
                val fieldValue = field.get(value)
                if (field.name == CAPTURED_ARGS_FIELD) {
                    withPropertyTrace(lambdaTrace.toCapturedArguments()) {
                        write(fieldValue)
                    }
                } else {
                    writePropertyValue(PropertyKind.Field, field.name, fieldValue)
                }
            }
        }
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

    private
    const val CAPTURED_ARGS_FIELD = "capturedArgs"

    private
    val SERIALIZED_LAMBDA_FIELDS: List<Field> =
        SerializedLambda::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) }
            .sortedBy { it.name }
            .onEach { it.isAccessible = true }

    /**
     * Javac names the synthetic method backing a lambda body `lambda$<enclosingMethod>$<n>` —
     * e.g. a lambda defined in `Foo.bar()` becomes `lambda$bar$0` (verified for Java 8 through 25).
     * This regex extracts the enclosing method name back from that synthetic name.
     */
    private
    val syntheticLambdaMethodName = Regex("""lambda\$(.+)\$\d+""")

    private
    fun PropertyTrace.SerializedLambda.toCapturedArguments(): PropertyTrace.CapturedLambdaArguments {
        val synthetic = syntheticLambdaMethodName.matchEntire(implMethodName)
        return if (synthetic != null) {
            PropertyTrace.CapturedLambdaArguments(
                subkind = PropertyTrace.CapturedLambdaArguments.Subkind.LambdaBody,
                owningClass = implClass,
                owningMethod = synthetic.groupValues[1],
                trace = this
            )
        } else {
            PropertyTrace.CapturedLambdaArguments(
                subkind = PropertyTrace.CapturedLambdaArguments.Subkind.BoundReceiver,
                owningClass = implClass,
                owningMethod = implMethodName,
                trace = this
            )
        }
    }
}
