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

package org.gradle.internal.serialize.codecs.core.jos

import org.gradle.internal.serialize.codecs.core.SerializedLambdaParametersCheckingCodec
import org.gradle.internal.serialize.graph.BeanStateReader
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.EncodingProvider
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.Decoding
import org.gradle.internal.serialize.graph.codecs.Encoding
import org.gradle.internal.serialize.graph.codecs.EncodingProducer
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodeBean
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.readEnum
import org.gradle.internal.serialize.graph.readNonNull
import org.gradle.internal.serialize.graph.withBeanTrace
import org.gradle.internal.serialize.graph.withImmediateMode
import org.gradle.internal.serialize.graph.writeEnum
import java.io.Externalizable
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.lang.invoke.SerializedLambda
import java.lang.reflect.Method
import java.lang.reflect.Modifier.isPrivate


/**
 * Allows objects that support the [Java Object Serialization](https://docs.oracle.com/javase/8/docs/platform/serialization/spec/serialTOC.html)
 * protocol to be stored in the configuration cache.
 *
 * The implementation is currently limited to serializable classes that
 * either implement the [java.io.Externalizable] interface, or implement the [java.io.Serializable] interface and define one of the following combinations of methods:
 * - a `writeObject` method combined with a `readObject` method to control exactly which information to store;
 * - a `writeObject` method with no corresponding `readObject`; `writeObject` must eventually call [ObjectOutputStream.defaultWriteObject];
 * - a `readObject` method with no corresponding `writeObject`; `readObject` must eventually call [ObjectInputStream.defaultReadObject];
 * - a `writeReplace` method to allow the class to nominate a replacement to be written;
 * - a `readResolve` method to allow the class to nominate a replacement for the object just read;
 *
 * The following _Java Object Serialization_ features are **not** supported:
 * - the `serialPersistentFields` member to explicitly declare which fields are serializable; the member, if present, is ignored; the configuration cache considers all but `transient` fields serializable;
 * - the following methods of [ObjectOutputStream] are not supported and will throw [UnsupportedOperationException]:
 *    - `reset()`, `writeFields()`, `putFields()`, `writeChars(String)`, `writeBytes(String)` and `writeUnshared(Any?)`.
 * - the following methods of [ObjectInputStream] are not supported and will throw [UnsupportedOperationException]:
 *    - `readLine()`, `readFully(ByteArray)`, `readFully(ByteArray, Int, Int)`, `readUnshared()`, `readFields()`, `transferTo(OutputStream)` and `readAllBytes()`.
 * - validations registered via [ObjectInputStream.registerValidation] are simply ignored;
 * - the `readObjectNoData` method, if present, is never invoked;
 */
class JavaObjectSerializationCodec(
    private val lookup: JavaSerializationEncodingLookup
) : EncodingProducer, Decoding {

    private
    val readResolveMethod = ReadResolveCache()

    private
    val readObjectHierarchy = HashMap<Class<*>, List<MethodHandle>>()

    override fun encodingForType(type: Class<*>): Encoding? =
        type.takeIf { Serializable::class.java.isAssignableFrom(it) }?.let { serializableType ->
            lookup.encodingFor(serializableType)
        }

    override suspend fun ReadContext.decode(): Any =
        decodePreservingIdentity { id ->
            when (readEnum<Format>()) {
                Format.WriteObject -> {
                    withImmediateMode {
                        decodingBeanWithId(id) { bean, beanType, beanStateReader ->
                            val objectInputStream = objectInputStreamAdapterFor(bean, beanStateReader)
                            val readObject = readObjectMethodHierarchyForDecoding(beanType)
                            when {
                                readObject.isNotEmpty() -> invokeAllExact(readObject, bean, objectInputStream)
                                else -> objectInputStream.defaultReadObject()
                            }
                        }
                    }
                }

                Format.ReadObject -> {
                    withImmediateMode {
                        decodingBeanWithId(id) { bean, beanType, beanStateReader ->
                            invokeAllExact(
                                readObjectMethodHierarchyForDecoding(beanType),
                                bean,
                                objectInputStreamAdapterFor(bean, beanStateReader)
                            )
                        }
                    }
                }

                Format.ReadResolveBean -> {
                    readResolve(decodeBean())
                        .also { putIdentity(id, it) }
                }

                Format.ReadResolveAny -> {
                    readResolve(readNonNull())
                        .also { putIdentity(id, it) }
                }

                Format.SerializedLambda -> {
                    readResolve(SerializedLambdaParametersCheckingCodec.run { decode() })
                        .also { putIdentity(id, it) }
                }
            }
        }

    private
    fun ReadContext.objectInputStreamAdapterFor(bean: Any, beanStateReader: BeanStateReader): ObjectInputStreamAdapter =
        ObjectInputStreamAdapter(bean, beanStateReader, this)

    internal
    class WriteObjectEncoding(private val writeObject: List<MethodHandle>) : EncodingProvider<Any> {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                val beanType = value.javaClass
                val record = recordWritingOf(beanType, value)
                writeEnum(Format.WriteObject)
                writeClass(beanType)
                record.run { playback() }
            }
        }

        private
        fun recordWritingOf(beanType: Class<Any>, value: Any): RecordingObjectOutputStream =
            RecordingObjectOutputStream(beanType, value).also { recordingObjectOutputStream: ObjectOutputStream ->
                writeObject.forEach { method ->
                    method.invokeExact(value, recordingObjectOutputStream)
                }
            }
    }

    internal
    object ReadObjectEncoding : Encoding {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                writeEnum(Format.ReadObject)
                encodeBean(value)
            }
        }
    }

    internal
    class WriteReplaceEncoding(writeReplace: Method) : EncodingProvider<Any> {

        private
        val writeReplaceHandle = MethodHandles.lookup()
            .unreflect(writeReplace)
            .asType(methodType(Any::class.java, Any::class.java))

        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                val replacement = writeReplaceHandle.invokeExact(value)
                when {
                    replacement is SerializedLambda -> {
                        writeEnum(Format.SerializedLambda)
                        SerializedLambdaParametersCheckingCodec.run {
                            encode(replacement)
                        }
                    }

                    replacement::class.java === value::class.java -> {
                        // Avoid a StackOverflowException when the replacement and value are of the same type.
                        // TODO:configuration-cache Skipping Java serialization for the replacement is likely incorrect when the class also supports the `writeObject` protocol
                        writeEnum(Format.ReadResolveBean)
                        encodeBean(value)
                    }

                    else -> {
                        writeEnum(Format.ReadResolveAny)
                        write(replacement)
                    }
                }
            }
        }
    }

    internal
    object ReadResolveEncoding : Encoding {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                writeEnum(Format.ReadResolveBean)
                encodeBean(value)
            }
        }
    }

    private
    enum class Format {
        ReadResolveBean,
        ReadResolveAny,
        WriteObject,
        ReadObject,
        SerializedLambda
    }

    private
    fun readResolve(bean: Any): Any =
        when (val readResolve = readResolveMethod.forObject(bean)) {
            null -> bean
            else -> readResolve.invokeExact(bean)
        }

    /**
     * Caches the computed `readObject` method hierarchies during decoding because [ReadContext.decode] might
     * be called multiple times for the same type.
     */
    private
    fun readObjectMethodHierarchyForDecoding(type: Class<*>): List<MethodHandle> =
        readObjectHierarchy.computeIfAbsent(type) {
            readObjectMethodHierarchyFrom(it.allMethods())
        }
}


internal
fun readObjectMethodHierarchyFrom(candidates: List<Method>): List<MethodHandle> = candidates
    .serializationMethodHierarchy("readObject", ObjectInputStream::class.java)


internal
fun Iterable<Method>.serializationMethodHierarchy(methodName: String, parameterType: Class<*>): List<MethodHandle> = asSequence()
    .filter { method ->
        method.run {
            isPrivate(modifiers)
                && parameterCount == 1
                && returnType == Void.TYPE
                && name == methodName
                && parameterTypes[0].isAssignableFrom(parameterType)
        }
    }.onEach { serializationMethod ->
        serializationMethod.isAccessible = true
    }.map {
        MethodHandles.lookup()
            .unreflect(it)
            .asType(methodType(Void.TYPE, Any::class.java, parameterType))
    }.toList().asReversed()


private
inline fun ReadContext.decodingBeanWithId(id: Int, decode: (Any, Class<*>, BeanStateReader) -> Unit): Any {
    val beanType = readClass()
    return withBeanTrace(beanType) {
        val beanStateReader = beanStateReaderFor(beanType)
        beanStateReader.run { newBeanWithId(id) }.also { bean ->
            decode(bean, beanType, beanStateReader)
        }
    }
}


private
fun ReadContext.putIdentity(id: Int, instance: Any) {
    isolate.identities.putInstance(id, instance)
}


object ExternalizableCodec : Codec<Externalizable> {
    override suspend fun WriteContext.encode(value: Externalizable) {
        encodePreservingIdentityOf(value) {
            val beanType = value.javaClass
            writeClass(beanType)
            val record = recordWritingOf(beanType, value)
            record.run { playback() }
        }
    }

    private
    fun recordWritingOf(beanType: Class<Externalizable>, value: Externalizable): RecordingObjectOutputStream =
        RecordingObjectOutputStream(beanType, value).also { recordingObjectOutputStream ->
            value.writeExternal(recordingObjectOutputStream)
        }

    override suspend fun ReadContext.decode(): Externalizable =
        decodePreservingIdentity { id ->
            decodingBeanWithId(id) { bean, _, beanStateReader ->
                val objectInputStream = ObjectInputStreamAdapter(bean, beanStateReader, this)
                (bean as Externalizable).readExternal(objectInputStream)
            } as Externalizable
        }
}


private
fun invokeAllExact(methods: List<MethodHandle>, bean: Any, stream: ObjectInputStream) {
    for (method in methods) {
        method.invokeExact(bean, stream)
    }
}
