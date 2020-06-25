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
import org.gradle.instantexecution.serialization.readEnum
import org.gradle.instantexecution.serialization.readNonNull
import org.gradle.instantexecution.serialization.withBeanTrace
import org.gradle.instantexecution.serialization.writeEnum
import java.lang.reflect.Method


/**
 * Instant execution serialization for objects that support [Java serialization][java.io.Serializable]
 * via a custom `writeReplace()` / `readResolve()` method pair.
 */
class SerializableWriteReplaceCodec : EncodingProducer, Decoding {

    private
    val readResolveMethod = MethodCache { isReadResolve() }

    private
    val readResolveEncoding = ReadResolveEncoding()

    override fun encodingForType(type: Class<*>): Encoding? =
        type.takeIf { it.isSerializable() }?.let { serializableType ->
            writeReplaceMethodOf(serializableType)?.let(::WriteReplaceEncoding)
                ?: readResolveMethodOf(serializableType)?.let { readResolveEncoding }
        }

    /**
     * Reads a bean resulting from a `writeReplace` method invocation
     * honouring `readResolve` if it's present.
     */
    override suspend fun ReadContext.decode(): Any? =
        decodePreservingIdentity { id ->
            readResolve(
                when (readEnum<Format>()) {
                    Format.Inline -> decodeBean()
                    Format.Replaced -> readNonNull()
                }
            ).also { bean ->
                isolate.identities.putInstance(id, bean)
            }
        }

    private
    enum class Format {
        Inline,
        Replaced
    }

    private
    inner class WriteReplaceEncoding(private val writeReplace: Method) : EncodingProvider<Any> {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                val replacement = writeReplace.invoke(value)
                if (replacement === value) {
                    writeEnum(Format.Inline)
                    encodeBean(value)
                } else {
                    writeEnum(Format.Replaced)
                    write(replacement)
                }
            }
        }
    }

    private
    inner class ReadResolveEncoding : Encoding {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                writeEnum(Format.Inline)
                encodeBean(value)
            }
        }
    }

    private
    suspend fun WriteContext.encodeBean(value: Any) {
        val beanType = value.javaClass
        withBeanTrace(beanType) {
            writeClass(beanType)
            beanStateWriterFor(beanType).run {
                writeStateOf(value)
            }
        }
    }

    private
    suspend fun ReadContext.decodeBean(): Any {
        val beanType = readClass()
        return withBeanTrace(beanType) {
            beanStateReaderFor(beanType).run {
                newBean(false).also {
                    readStateOf(it)
                }
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
        .firstAccessibleMatchingMethodOrNull {
            parameterCount == 0 && name == "writeReplace"
        }

    private
    fun readResolveMethodOf(type: Class<*>) = type
        .firstMatchingMethodOrNull {
            isReadResolve()
        }

    private
    fun Method.isReadResolve() =
        parameterCount == 0 && name == "readResolve"
}
