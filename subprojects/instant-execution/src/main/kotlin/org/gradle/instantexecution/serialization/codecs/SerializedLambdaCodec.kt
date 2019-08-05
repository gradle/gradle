/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext

import java.lang.invoke.SerializedLambda


object SerializedLambdaCodec : Codec<SerializedLambda> {

    override suspend fun WriteContext.encode(value: SerializedLambda) {
        value.apply {
            writeString(capturingClass)
            writeString(functionalInterfaceClass)
            writeString(functionalInterfaceMethodName)
            writeString(functionalInterfaceMethodSignature)
            writeSmallInt(implMethodKind)
            writeString(implClass)
            writeString(implMethodName)
            writeString(implMethodSignature)
            writeString(instantiatedMethodType)
            writeSmallInt(capturedArgCount)
            for (i in 0 until capturedArgCount) {
                write(getCapturedArg(i))
            }
        }
    }

    override suspend fun ReadContext.decode(): SerializedLambda? = SerializedLambda(
        readString().let { classLoader.loadClass(it.replace('/', '.')) },
        readString(),
        readString(),
        readString(),
        readSmallInt(),
        readString(),
        readString(),
        readString(),
        readString(),
        Array(readSmallInt()) {
            read()
        }
    )
}
