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

import org.gradle.internal.reflect.ClassInspector
import org.gradle.internal.reflection.access.ObjectOpener
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
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.io.Externalizable
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier.isPrivate
import java.lang.reflect.Modifier.isStatic


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
 *
 * The codec is stateless and shared by all builds, so the per-type reflection work is done once per type.
 */
@ServiceScope(Scope.Global::class)
class JavaObjectSerializationCodec(
    private val objectOpener: ObjectOpener
) : EncodingProducer, Decoding {

    // The cached values are attached to classes that may outlive any build,
    // so they must only reach objects that live at least as long as the class they're attached to.

    private
    val encodings = object : ClassValue<Encoding?>() {
        override fun computeValue(type: Class<*>): Encoding? = calculateEncoding(type)
    }

    private
    val readResolveMethods = object : ClassValue<MethodHandle?>() {
        override fun computeValue(type: Class<*>): MethodHandle? =
            type.allMethods().find { it.isReadResolve() }?.let(::objectToObjectHandle)
    }

    private
    val readObjectMethodHierarchies = object : ClassValue<List<MethodHandle>>() {
        override fun computeValue(type: Class<*>): List<MethodHandle> =
            type.allMethods().readObjectMethodHierarchy().map { serializationMethodHandle(it, ObjectInputStream::class.java) }
    }

    override fun encodingForType(type: Class<*>): Encoding? =
        type.takeIf { Serializable::class.java.isAssignableFrom(it) }?.let { serializableType ->
            encodings.get(serializableType)
        }

    override suspend fun ReadContext.decode(): Any =
        decodePreservingIdentity { id ->
            when (readEnum<Format>()) {
                Format.WriteObject -> {
                    withImmediateMode {
                        decodingBeanWithId(id) { bean, beanType, beanStateReader ->
                            val objectInputStream = objectInputStreamAdapterFor(bean, beanStateReader)
                            val readObject = readObjectMethodHierarchies.get(beanType)
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
                                readObjectMethodHierarchies.get(beanType),
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
            }
        }

    private
    fun ReadContext.objectInputStreamAdapterFor(bean: Any, beanStateReader: BeanStateReader): ObjectInputStreamAdapter =
        ObjectInputStreamAdapter(bean, beanStateReader, this)

    private
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
    class WriteReplaceEncoding(private val writeReplaceHandle: MethodHandle) : EncodingProvider<Any> {

        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                val replacement = writeReplaceHandle.invokeExact(value)
                when {
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

    private
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
        ReadObject
    }

    private
    fun readResolve(bean: Any): Any =
        when (val readResolve = readResolveMethods.get(bean.javaClass)) {
            null -> bean
            else -> readResolve.invokeExact(bean)
        }

    private
    fun calculateEncoding(type: Class<*>): Encoding? {
        val candidates = type.allMethods()
        return candidates.find { it.isWriteReplace() }
                ?.let { WriteReplaceEncoding(objectToObjectHandle(it)) }
            ?: candidates.writeObjectMethodHierarchy()
                .takeIf { it.isNotEmpty() }
                ?.let { methods -> WriteObjectEncoding(methods.map { serializationMethodHandle(it, ObjectOutputStream::class.java) }) }
            // Only the presence of the reading methods matters here, they are made accessible when reading
            ?: ReadResolveEncoding.takeIf { candidates.any { it.isReadResolve() } }
            ?: ReadObjectEncoding.takeIf { candidates.readObjectMethodHierarchy().isNotEmpty() }
    }

    /**
     * Returns a handle of type `(Object) -> Object` for the given method.
     */
    private
    fun objectToObjectHandle(method: Method): MethodHandle =
        accessibleHandle(method).asType(methodType(Any::class.java, Any::class.java))

    /**
     * Returns a handle of type `(Object, stream) -> void` for the given `writeObject` or `readObject` method.
     */
    private
    fun serializationMethodHandle(method: Method, streamType: Class<*>): MethodHandle =
        accessibleHandle(method).asType(methodType(Void.TYPE, Any::class.java, streamType))

    private
    fun accessibleHandle(method: Method): MethodHandle {
        objectOpener.makeAccessible(method)
        return MethodHandles.lookup().unreflect(method)
    }
}


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


private
fun Class<*>.allMethods(): List<Method> =
    ClassInspector.inspectMethods(this)


private
fun Method.isWriteReplace() =
    !isStatic(modifiers)
        && parameterCount == 0
        && returnType == Any::class.java
        && name == "writeReplace"


private
fun Method.isReadResolve() =
    !isStatic(modifiers)
        && parameterCount == 0
        && returnType == Any::class.java
        && name == "readResolve"


private
fun List<Method>.writeObjectMethodHierarchy() =
    serializationMethodHierarchy("writeObject", ObjectOutputStream::class.java)


private
fun List<Method>.readObjectMethodHierarchy() =
    serializationMethodHierarchy("readObject", ObjectInputStream::class.java)


/**
 * Returns the private serialization methods with the given name of the type hierarchy, starting from the topmost class.
 */
private
fun List<Method>.serializationMethodHierarchy(methodName: String, streamType: Class<*>): List<Method> =
    filter { method ->
        method.run {
            isPrivate(modifiers)
                && parameterCount == 1
                && returnType == Void.TYPE
                && name == methodName
                && parameterTypes[0].isAssignableFrom(streamType)
        }
    }.asReversed()
