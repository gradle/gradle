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

import org.gradle.instantexecution.serialization.EncodingProvider
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.decodePreservingIdentity
import org.gradle.instantexecution.serialization.encodePreservingIdentityOf
import org.gradle.instantexecution.serialization.readNonNull

import java.io.Serializable

import java.lang.reflect.Method


/**
 * Instant execution serialization for objects that support [Java serialization][java.io.Serializable]
 * via a custom `writeReplace()` / `readResolve()` method pair.
 */
class SerializableWriteReplaceCodec : EncodingProducer, Decoding {

    private
    val readResolveMethod = MethodCache {
        parameterCount == 0 && name == "readResolve"
    }

    override fun encodingForType(type: Class<*>): Encoding? =
        writeReplaceMethodOf(type)?.let(::WriteReplaceEncoding)

    /**
     * Reads a bean resulting from a `writeReplace` method invocation
     * honouring `readResolve` if it's present.
     */
    override suspend fun ReadContext.decode(): Any? =
        decodePreservingIdentity { id ->
            readResolve(readNonNull()).also { bean ->
                isolate.identities.putInstance(id, bean)
            }
        }

    private
    class WriteReplaceEncoding(private val writeReplace: Method) : EncodingProvider<Any> {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                write(writeReplace.invoke(value))
            }
        }
    }

    private
    fun readResolve(bean: Any): Any =
        when (val readResolve = readResolveMethod.forObject(bean)) {
            null -> bean
            else -> readResolve.invoke(bean)
        }

    private
    fun writeReplaceMethodOf(type: Class<*>) = type
        .takeIf { Serializable::class.java.isAssignableFrom(type) }
        ?.firstMatchingMethodOrNull {
            parameterCount == 0 && name == "writeReplace"
        }
}
