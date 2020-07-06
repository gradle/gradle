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

import org.gradle.instantexecution.problems.DocumentationSection.NotYetImplementedJavaSerialization
import org.gradle.instantexecution.problems.StructuredMessage
import org.gradle.instantexecution.serialization.EncodingProvider
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.beans.BeanStateReader
import org.gradle.instantexecution.serialization.codecs.BrokenValue
import org.gradle.instantexecution.serialization.codecs.Decoding
import org.gradle.instantexecution.serialization.codecs.Encoding
import org.gradle.instantexecution.serialization.codecs.EncodingProducer
import org.gradle.instantexecution.serialization.decodePreservingIdentity
import org.gradle.instantexecution.serialization.encodePreservingIdentityOf
import org.gradle.instantexecution.serialization.logPropertyProblem
import org.gradle.instantexecution.serialization.readEnum
import org.gradle.instantexecution.serialization.readNonNull
import org.gradle.instantexecution.serialization.withBeanTrace
import org.gradle.instantexecution.serialization.withImmediateMode
import org.gradle.instantexecution.serialization.writeEnum

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

import java.lang.reflect.Method
import java.lang.reflect.Modifier


/**
 * Instant execution serialization for objects that support [Java serialization][java.io.Serializable]
 * via a custom `writeObject(ObjectOutputStream)` / `readObject(ObjectInputStream)` method pair.
 */
class JavaObjectSerializationCodec : EncodingProducer, Decoding {

    private
    val readResolveMethod = MethodCache { isReadResolve() }

    override fun encodingForType(type: Class<*>): Encoding? =
        type.takeIf { Serializable::class.java.isAssignableFrom(it) }?.let { serializableType ->
            writeReplaceEncodingFor(serializableType)
                ?: readResolveEncodingFor(serializableType)
                ?: writeObjectEncodingFor(serializableType)
                ?: readObjectEncodingFor(serializableType)
        }

    private
    fun writeReplaceEncodingFor(serializableType: Class<*>) =
        writeReplaceMethodOf(serializableType)
            ?.let(::WriteReplaceEncoding)

    private
    fun readResolveEncodingFor(serializableType: Class<*>) =
        readResolveMethodOf(serializableType)
            ?.let { ReadResolveEncoding }

    private
    fun writeObjectEncodingFor(serializableType: Class<*>): Encoding? =
        writeObjectMethodHierarchyOf(serializableType)
            .takeIf { it.isNotEmpty() }
            ?.let(::WriteObjectEncoding)

    private
    fun readObjectEncodingFor(serializableType: Class<*>): Encoding? =
        readObjectMethodHierarchyOf(serializableType)
            .takeIf { it.isNotEmpty() }
            ?.let { ReadObjectEncoding }

    override suspend fun ReadContext.decode(): Any? =
        decodePreservingIdentity { id ->
            when (val format = readEnum<Format>()) {
                Format.WriteReplace -> {
                    readResolve(readNonNull())
                        .also { putIdentity(id, it) }
                }
                Format.ReadResolve -> {
                    readResolve(decodeBean())
                        .also { putIdentity(id, it) }
                }
                else -> {
                    withImmediateMode {
                        val beanType = readClass()
                        withBeanTrace(beanType) {
                            val beanStateReader = beanStateReaderFor(beanType)
                            beanStateReader.run { newBeanWithId(false, id) }.also { bean ->
                                when (format) {
                                    Format.ReadObject -> {
                                        invokeAll(
                                            readObjectMethodHierarchyOf(beanType),
                                            bean,
                                            objectInputStreamAdapterFor(bean, beanStateReader)
                                        )
                                    }
                                    Format.WriteObject -> {
                                        if (readBoolean()) {
                                            val objectInputStream = objectInputStreamAdapterFor(bean, beanStateReader)
                                            val readObject = readObjectMethodHierarchyOf(beanType)
                                            when {
                                                readObject.isNotEmpty() -> invokeAll(readObject, bean, objectInputStream)
                                                else -> objectInputStream.defaultReadObject()
                                            }
                                        } else {
                                            val brokenValue = readNonNull<BrokenValue>()
                                            logPropertyProblem("deserialize", brokenValue.failure, NotYetImplementedJavaSerialization) {
                                                failedJOS(bean)
                                            }
                                        }
                                    }
                                    else -> TODO("unreachable")
                                }
                            }
                        }
                    }
                }
            }
        }

    private
    fun ReadContext.putIdentity(id: Int, instance: Any) {
        isolate.identities.putInstance(id, instance)
    }

    private
    fun invokeAll(readObject: List<Method>, bean: Any, objectInputStream: ObjectInputStreamAdapter) {
        readObject.forEach { method ->
            method.invoke(bean, objectInputStream)
        }
    }

    private
    fun ReadContext.objectInputStreamAdapterFor(bean: Any, beanStateReader: BeanStateReader): ObjectInputStreamAdapter =
        ObjectInputStreamAdapter(bean, beanStateReader, this)

    private
    class WriteObjectEncoding(private val writeObject: List<Method>) : EncodingProvider<Any> {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {

                writeEnum(Format.WriteObject)

                val beanType = value.javaClass
                writeClass(beanType)

                runCatching {
                    // Exceptions during the recording phase can be safely recovered from
                    // because recording doesn't affect the WriteContext.
                    recordWritingOf(beanType, value)
                }.apply {
                    onSuccess { record ->
                        writeBoolean(true)
                        record.run { playback() }
                    }
                    onFailure { ex ->
                        logPropertyProblem("serialize", ex, NotYetImplementedJavaSerialization) {
                            failedJOS(value)
                        }
                        writeBoolean(false)
                        write(BrokenValue(ex))
                    }
                }
            }
        }

        private
        fun recordWritingOf(beanType: Class<Any>, value: Any): RecordingObjectOutputStream =
            RecordingObjectOutputStream(beanType, value).also { recordingObjectOutputStream ->
                writeObject.forEach { method ->
                    method.invoke(value, recordingObjectOutputStream)
                }
            }
    }

    private
    object ReadObjectEncoding : Encoding {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                writeEnum(Format.ReadObject)
                encodeBean(value)
            }
        }
    }

    private
    class WriteReplaceEncoding(private val writeReplace: Method) : EncodingProvider<Any> {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                val replacement = writeReplace.invoke(value)
                if (replacement === value) {
                    writeEnum(Format.ReadResolve)
                    encodeBean(value)
                } else {
                    writeEnum(Format.WriteReplace)
                    write(replacement)
                }
            }
        }
    }

    private
    object ReadResolveEncoding : Encoding {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                writeEnum(Format.ReadResolve)
                encodeBean(value)
            }
        }
    }

    private
    enum class Format {
        ReadObject,
        WriteObject,
        ReadResolve,
        WriteReplace
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
            parameterCount == 0 && name == "writeReplace" && returnType == java.lang.Object::class.java
        }

    private
    fun readResolveMethodOf(type: Class<*>) = type
        .firstMatchingMethodOrNull {
            isReadResolve()
        }

    private
    fun Method.isReadResolve() =
        parameterCount == 0 && name == "readResolve" && returnType == java.lang.Object::class.java

    private
    fun writeObjectMethodHierarchyOf(type: Class<*>) = type
        .serializationMethodHierarchy("writeObject", ObjectOutputStream::class.java)

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


private
fun StructuredMessage.Builder.failedJOS(value: Any) {
    text("value ")
    reference(value.toString())
    text(" failed Java Object Serialization")
}


internal
suspend fun WriteContext.encodeBean(value: Any) {
    val beanType = value.javaClass
    withBeanTrace(beanType) {
        writeClass(beanType)
        beanStateWriterFor(beanType).run {
            writeStateOf(value)
        }
    }
}
