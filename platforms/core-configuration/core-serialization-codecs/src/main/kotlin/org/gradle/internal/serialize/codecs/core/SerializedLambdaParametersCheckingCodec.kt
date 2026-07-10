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
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.reflection.access.ObjectOpener
import org.gradle.internal.serialize.beans.services.unsupportedFieldDeclaredTypes
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateContext
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.logUnsupported
import org.gradle.internal.serialize.graph.withPropertyTrace
import org.objectweb.asm.Type
import org.objectweb.asm.Type.getArgumentTypes
import java.lang.invoke.SerializedLambda
import java.lang.reflect.Field
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
class SerializedLambdaParametersCheckingCodec(
    private val objectOpener: ObjectOpener
) : Codec<SerializedLambda> {
    override suspend fun ReadContext.decode(): SerializedLambda {
        val capturingClass = readClass()
        val functionalInterfaceClass = readString()
        val functionalInterfaceMethodName = readString()
        val functionalInterfaceMethodSignature = readString()
        val implMethodKind = readSmallInt()
        val implClass = readString()
        val implMethodName = readString()
        val implMethodSignature = readString()
        val instantiatedMethodType = readString()
        val capturedArgCount = readSmallInt()
        val capturedArgs = Array<Any?>(capturedArgCount) { read() }
        return SerializedLambda(
            capturingClass,
            functionalInterfaceClass,
            functionalInterfaceMethodName,
            functionalInterfaceMethodSignature,
            implMethodKind,
            implClass,
            implMethodName,
            implMethodSignature,
            instantiatedMethodType,
            capturedArgs
        )
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
            writeClass(capturingClassField.get(value) as Class<*>)
            writeString(value.functionalInterfaceClass)
            writeString(value.functionalInterfaceMethodName)
            writeString(value.functionalInterfaceMethodSignature)
            writeSmallInt(value.implMethodKind)
            writeString(value.implClass)
            writeString(value.implMethodName)
            writeString(value.implMethodSignature)
            writeString(value.instantiatedMethodType)
            withPropertyTrace(lambdaTrace.toCapturedArguments()) {
                val capturedArgCount = value.capturedArgCount
                writeSmallInt(capturedArgCount)
                for (i in 0 until capturedArgCount) {
                    write(value.getCapturedArg(i))
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
    val capturingClassField: Field by lazy {
        SerializedLambda::class.java.getDeclaredField("capturingClass").also { objectOpener.makeAccessible(it) }
    }

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
