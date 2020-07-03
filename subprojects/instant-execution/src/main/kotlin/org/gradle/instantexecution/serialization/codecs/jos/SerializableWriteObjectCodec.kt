/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution.serialization.codecs.jos

import org.gradle.instantexecution.serialization.EncodingProvider
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.codecs.Decoding
import org.gradle.instantexecution.serialization.codecs.Encoding
import org.gradle.instantexecution.serialization.codecs.EncodingProducer
import org.gradle.instantexecution.serialization.decodePreservingIdentity
import org.gradle.instantexecution.serialization.encodePreservingIdentityOf
import org.gradle.instantexecution.serialization.withBeanTrace
import org.gradle.instantexecution.serialization.withImmediateMode

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

import java.lang.reflect.Method
import java.lang.reflect.Modifier


/**
 * Instant execution serialization for objects that support [Java serialization][java.io.Serializable]
 * via a custom `writeObject(ObjectOutputStream)` / `readObject(ObjectInputStream)` method pair.
 */
class SerializableWriteObjectCodec : EncodingProducer, Decoding {

    override fun encodingForType(type: Class<*>): Encoding? =
        type.takeIf { it.isSerializable() }?.let { serializableType ->
            writeObjectMethodHierarchyOf(serializableType)
                .takeIf { it.isNotEmpty() }
                ?.let(SerializableWriteObjectCodec::WriteObjectEncoding)
        }

    override suspend fun ReadContext.decode(): Any? =
        decodePreservingIdentity { id ->
            withImmediateMode {
                val beanType = readClass()
                withBeanTrace(beanType) {
                    val beanStateReader = beanStateReaderFor(beanType)
                    beanStateReader.run { newBeanWithId(false, id) }.also { bean ->
                        val objectInputStream = ObjectInputStreamAdapter(
                            bean,
                            beanStateReader,
                            this@decode
                        )
                        val readObject = readObjectMethodHierarchyOf(beanType)
                        when {
                            readObject.isNotEmpty() -> readObject.forEach { it.invoke(bean, objectInputStream) }
                            else -> objectInputStream.defaultReadObject()
                        }
                    }
                }
            }
        }

    private
    class WriteObjectEncoding(private val writeObject: List<Method>) : EncodingProvider<Any> {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {

                val beanType = value.javaClass

                val recordingObjectOutputStream = RecordingObjectOutputStream(beanType, value)
                writeObject.forEach {
                    it.invoke(value, recordingObjectOutputStream)
                }

                writeClass(beanType)
                recordingObjectOutputStream.run {
                    playback()
                }
            }
        }
    }

    private
    fun writeObjectMethodHierarchyOf(type: Class<*>) = type
        .serializationMethodHierarchy("writeObject", ObjectOutputStream::class.java)

    // TODO:instant-execution readObjectNoData
    private
    fun readObjectMethodHierarchyOf(type: Class<*>): List<Method> = type
        .serializationMethodHierarchy("readObject", ObjectInputStream::class.java)

    private
    fun Class<*>.serializationMethodHierarchy(methodName: String, parameterType: Class<*>): List<Method> =
        allMethods()
            .filter { method ->
                method.run {
                    Modifier.isPrivate(modifiers)
                        && parameterCount == 1
                        && returnType == Void.TYPE
                        && name == methodName
                        && parameterTypes[0].isAssignableFrom(parameterType)
                }
            }.onEach { serializationMethod ->
                serializationMethod.isAccessible = true
            }.reversed()
}


internal
fun Class<*>.isSerializable() =
    Serializable::class.java.isAssignableFrom(this)


internal
fun unsupported(feature: String): Nothing =
    throw UnsupportedOperationException("'$feature' is not supported by the Gradle configuration cache.")
