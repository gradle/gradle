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

package org.gradle.configurationcache.serialization.codecs.jos

import org.gradle.configurationcache.problems.DocumentationSection.NotYetImplementedJavaSerialization
import org.gradle.configurationcache.problems.StructuredMessage
import org.gradle.configurationcache.serialization.EncodingProvider
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.beans.BeanStateReader
import org.gradle.configurationcache.serialization.codecs.BrokenValue
import org.gradle.configurationcache.serialization.codecs.Decoding
import org.gradle.configurationcache.serialization.codecs.Encoding
import org.gradle.configurationcache.serialization.codecs.EncodingProducer
import org.gradle.configurationcache.serialization.decodePreservingIdentity
import org.gradle.configurationcache.serialization.encodePreservingIdentityOf
import org.gradle.configurationcache.serialization.logPropertyProblem
import org.gradle.configurationcache.serialization.readEnum
import org.gradle.configurationcache.serialization.readNonNull
import org.gradle.configurationcache.serialization.withBeanTrace
import org.gradle.configurationcache.serialization.withImmediateMode
import org.gradle.configurationcache.serialization.writeEnum
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.reflect.Method
import java.lang.reflect.Modifier.isPrivate
import java.lang.reflect.Modifier.isStatic


/**
 * Allows objects that support the [Java Object Serialization](https://docs.oracle.com/javase/8/docs/platform/serialization/spec/serialTOC.html)
 * protocol to be stored in the configuration cache.
 *
 * The implementation is currently limited to serializable classes that implement the [java.io.Serializable] interface
 * and define one of the following combination of methods:
 * - a `writeObject` method combined with a `readObject` method to control exactly which information to store;
 * - a `writeObject` method with no corresponding `readObject`; `writeObject` must eventually call [ObjectOutputStream.defaultWriteObject];
 * - a `readObject` method with no corresponding `writeObject`; `readObject` must eventually call [ObjectInputStream.defaultReadObject];
 * - a `writeReplace` method to allow the class to nominate a replacement to be written;
 * - a `readResolve` method to allow the class to nominate a replacement for the object just read;
 *
 * The following _Java Object Serialization_ features are **not** supported:
 * - serializable classes implementing the [java.io.Externalizable] interface; objects of such classes are discarded by the configuration cache during serialization and reported as problems;
 * - the `serialPersistentFields` member to explicitly declare which fields are serializable; the member, if present, is ignored; the configuration cache considers all but `transient` fields serializable;
 * - the following methods of [ObjectOutputStream] are not supported and will throw [UnsupportedOperationException]:
 *    - `reset()`, `writeFields()`, `putFields()`, `writeChars(String)`, `writeBytes(String)` and `writeUnshared(Any?)`.
 * - the following methods of [ObjectInputStream] are not supported and will throw [UnsupportedOperationException]:
 *    - `readLine()`, `readFully(ByteArray)`, `readFully(ByteArray, Int, Int)`, `readUnshared()`, `readFields()`, `transferTo(OutputStream)` and `readAllBytes()`.
 * - validations registered via [ObjectInputStream.registerValidation] are simply ignored;
 * - the `readObjectNoData` method, if present, is never invoked;
 */
class JavaObjectSerializationCodec : EncodingProducer, Decoding {

    private
    val readResolveMethod = MethodCache { isReadResolve() }

    private
    val readObjectHierarchy = HashMap<Class<*>, List<Method>>()

    override fun encodingForType(type: Class<*>): Encoding? =
        type.takeIf { Serializable::class.java.isAssignableFrom(it) }?.let { serializableType ->
            val candidates = serializableType.allMethods()
            writeReplaceEncodingFor(candidates)
                ?: readResolveEncodingFor(candidates)
                ?: writeObjectEncodingFor(candidates)
                ?: readObjectEncodingFor(candidates)
        }

    private
    fun writeReplaceEncodingFor(candidates: List<Method>) =
        writeReplaceMethodFrom(candidates)
            ?.let(::WriteReplaceEncoding)

    private
    fun readResolveEncodingFor(candidates: List<Method>) =
        readResolveMethodFrom(candidates)
            ?.let { ReadResolveEncoding }

    private
    fun writeObjectEncodingFor(candidates: List<Method>): Encoding? =
        writeObjectMethodHierarchyFrom(candidates)
            .takeIf { it.isNotEmpty() }
            ?.let(::WriteObjectEncoding)

    private
    fun readObjectEncodingFor(candidates: List<Method>): Encoding? =
        readObjectMethodHierarchyFrom(candidates)
            .takeIf { it.isNotEmpty() }
            ?.let { ReadObjectEncoding }

    override suspend fun ReadContext.decode(): Any? =
        decodePreservingIdentity { id ->
            when (readEnum<Format>()) {
                Format.WriteObject -> {
                    withImmediateMode {
                        decodingBeanWithId(id) { bean, beanType, beanStateReader ->
                            val objectInputStream = objectInputStreamAdapterFor(bean, beanStateReader)
                            val readObject = readObjectMethodHierarchyForDecoding(beanType)
                            when {
                                readObject.isNotEmpty() -> invokeAll(readObject, bean, objectInputStream)
                                else -> objectInputStream.defaultReadObject()
                            }
                        }
                    }
                }
                Format.ReadObject -> {
                    withImmediateMode {
                        decodingBeanWithId(id) { bean, beanType, beanStateReader ->
                            invokeAll(
                                readObjectMethodHierarchyForDecoding(beanType),
                                bean,
                                objectInputStreamAdapterFor(bean, beanStateReader)
                            )
                        }
                    }
                }
                Format.WriteReplace -> {
                    readResolve(readNonNull())
                        .also { putIdentity(id, it) }
                }
                Format.ReadResolve -> {
                    readResolve(decodeBean())
                        .also { putIdentity(id, it) }
                }
                Format.Broken -> {
                    decodingBeanWithId(id) { bean, _, _ ->
                        val brokenValue = readNonNull<BrokenValue>()
                        logPropertyProblem("deserialize", brokenValue.failure, NotYetImplementedJavaSerialization) {
                            failedJOS(bean)
                        }
                    }
                }
            }
        }

    private
    fun ReadContext.objectInputStreamAdapterFor(bean: Any, beanStateReader: BeanStateReader): ObjectInputStreamAdapter =
        ObjectInputStreamAdapter(bean, beanStateReader, this)

    private
    class WriteObjectEncoding(private val writeObject: List<Method>) : EncodingProvider<Any> {
        override suspend fun WriteContext.encode(value: Any) {
            encodePreservingIdentityOf(value) {
                val beanType = value.javaClass
                runCatching {
                    // Exceptions during the recording phase can be safely recovered from
                    // because recording doesn't affect the WriteContext.
                    recordWritingOf(beanType, value)
                }.apply {
                    onSuccess { record ->
                        writeEnum(Format.WriteObject)
                        writeClass(beanType)
                        record.run { playback() }
                    }
                    onFailure { ex ->
                        logPropertyProblem("serialize", ex, NotYetImplementedJavaSerialization) {
                            failedJOS(value)
                        }
                        writeEnum(Format.Broken)
                        writeClass(beanType)
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
        WriteReplace,
        ReadResolve,
        WriteObject,
        ReadObject,
        Broken
    }

    private
    fun readResolve(bean: Any): Any =
        when (val readResolve = readResolveMethod.forObject(bean)) {
            null -> bean
            else -> readResolve.invoke(bean)
        }

    private
    fun readResolveMethodFrom(candidates: List<Method>) =
        candidates.find {
            it.isReadResolve()
        }

    private
    fun writeReplaceMethodFrom(candidates: List<Method>) =
        candidates.firstAccessibleMatchingMethodOrNull {
            !isStatic(modifiers)
                && parameterCount == 0
                && returnType == java.lang.Object::class.java
                && name == "writeReplace"
        }

    private
    fun Method.isReadResolve() =
        !isStatic(modifiers)
            && parameterCount == 0
            && returnType == java.lang.Object::class.java
            && name == "readResolve"

    private
    fun writeObjectMethodHierarchyFrom(candidates: List<Method>) = candidates
        .serializationMethodHierarchy("writeObject", ObjectOutputStream::class.java)

    /**
     * Caches the computed `readObject` method hierarchies during decoding because [ReadContext.decode] might
     * be called multiple times for the same type.
     */
    private
    fun readObjectMethodHierarchyForDecoding(type: Class<*>): List<Method> =
        readObjectHierarchy.computeIfAbsent(type) {
            readObjectMethodHierarchyFrom(it.allMethods())
        }

    private
    fun readObjectMethodHierarchyFrom(candidates: List<Method>): List<Method> = candidates
        .serializationMethodHierarchy("readObject", ObjectInputStream::class.java)

    private
    fun Iterable<Method>.serializationMethodHierarchy(methodName: String, parameterType: Class<*>): List<Method> =
        filter { method ->
            method.run {
                isPrivate(modifiers)
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
inline fun ReadContext.decodingBeanWithId(id: Int, decode: (Any, Class<*>, BeanStateReader) -> Unit): Any? {
    val beanType = readClass()
    return withBeanTrace(beanType) {
        val beanStateReader = beanStateReaderFor(beanType)
        beanStateReader.run { newBeanWithId(false, id) }.also { bean ->
            decode(bean, beanType, beanStateReader)
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
fun ReadContext.putIdentity(id: Int, instance: Any) {
    isolate.identities.putInstance(id, instance)
}


private
fun invokeAll(methods: List<Method>, bean: Any, vararg args: Any?) {
    methods.forEach { method ->
        method.invoke(bean, *args)
    }
}
