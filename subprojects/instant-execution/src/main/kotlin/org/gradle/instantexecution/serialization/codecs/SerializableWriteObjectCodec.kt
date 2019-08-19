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

import org.gradle.instantexecution.runToCompletion
import org.gradle.instantexecution.serialization.EncodingProvider
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.internal.reflect.ClassInspector
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.reflect.Method


/**
 * Instant execution serialization for objects that support [Java serialization][java.io.Serializable]
 * via a custom `writeObject(ObjectOutputStream)` method.
 */
class SerializableWriteObjectCodec : EncodingProducer, Decoding {

    // TODO:instant-execution readObjectNoData
    private
    val readObjectCache = MethodCache {
        name == "readObject"
            && parameterCount == 1
            && parameterTypes[0].isAssignableFrom(ObjectInputStream::class.java)
    }

    override fun invoke(type: Class<*>): Encoding? =
        writeObjectMethodOf(type)?.let(::WriteObjectEncoding)

    override suspend fun ReadContext.decode(): Any? {
        val decoder: Decoder = this
        val type = readClass()
        return beanStateReaderFor(type).run {
            newBean().also {
                readObjectCache.forClass(type)!!.invoke(it, object : ObjectInputStream() {
                    // TODO:instant-execution implement all methods
                    override fun defaultReadObject() {
                        runToCompletion {
                            readStateOf(it)
                        }
                    }

                    override fun readInt(): Int =
                        decoder.readInt()

                    // TODO:instant-execution implement on top of ReadContext.read()
                    override fun readObjectOverride(): Any =
                        TODO("readObjectOverride")
                })
            }
        }
    }

    private
    class WriteObjectEncoding(
        private val writeObject: Method
    ) : EncodingProvider<Any> {
        override suspend fun WriteContext.encode(value: Any) {
            val encoder: Encoder = this
            val beanType = value.javaClass
            writeClass(beanType)
            writeObject.invoke(value, object : ObjectOutputStream() {

                // TODO:instant-execution only record method calls
                override fun defaultWriteObject() {
                    runToCompletion {
                        beanStateWriterFor(beanType).run {
                            writeStateOf(value)
                        }
                    }
                }

                override fun writeInt(`val`: Int) {
                    encoder.writeInt(`val`)
                }

                override fun writeObjectOverride(obj: Any?) {
                    TODO("writeObjectOverride")
                }
            })
        }
    }

    private
    fun writeObjectMethodOf(type: Class<*>) = type
        .takeIf { Serializable::class.java.isAssignableFrom(type) }
        ?.firstMatchingMethodOrNull {
            name == "writeObject"
                && parameterCount == 1
                && parameterTypes[0].isAssignableFrom(ObjectOutputStream::class.java)
        }
}


internal
class MethodCache(

    private
    val predicate: Method.() -> Boolean

) {
    private
    val methodCache = hashMapOf<Class<*>, Method?>()

    fun forObject(value: Any) =
        forClass(value.javaClass)

    fun forClass(type: Class<*>) =
        methodCache.computeIfAbsent(type) { it.firstMatchingMethodOrNull(predicate) }
}


internal
fun Class<*>.firstMatchingMethodOrNull(predicate: Method.() -> Boolean): Method? =
    ClassInspector.inspect(this)
        .allMethods
        .find(predicate)
        ?.apply { isAccessible = true }
