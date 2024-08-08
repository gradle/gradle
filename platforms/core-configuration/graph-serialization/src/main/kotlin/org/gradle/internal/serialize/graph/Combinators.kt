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

package org.gradle.internal.serialize.graph

import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine


fun <T> singleton(value: T): Codec<T> =
    SingletonCodec(value)


fun <T> codec(
    encode: suspend WriteContext.(T) -> Unit,
    decode: suspend ReadContext.() -> T?
): Codec<T> = object : Codec<T> {
    override suspend fun WriteContext.encode(value: T) = encode(value)
    override suspend fun ReadContext.decode(): T? = decode()
}


inline fun <reified T> IsolateContext.ownerService() =
    ownerService(T::class.java)


fun <T> IsolateContext.ownerService(serviceType: Class<T>) =
    isolate.owner.service(serviceType)


fun <T : Any> reentrant(codec: Codec<T>): Codec<T> = object : Codec<T> {

    var encodeCall: EncodeFrame<T>? = null

    var decodeCall: DecodeFrame<T?>? = null

    override suspend fun WriteContext.encode(value: T) {
        when (encodeCall) {
            null -> {
                encodeCall = EncodeFrame(value, null)
                encodeLoop(coroutineContext)
            }

            else -> suspendCoroutine { k ->
                encodeCall = EncodeFrame(value, k)
            }
        }
    }

    override suspend fun ReadContext.decode(): T? =
        when {
            immediateMode -> {
                codec.run { decode() }
            }

            decodeCall == null -> {
                decodeCall = DecodeFrame(null)
                decodeLoop(coroutineContext)
            }

            else -> suspendCoroutine { k ->
                decodeCall = DecodeFrame(k)
            }
        }

    private
    fun WriteContext.encodeLoop(coroutineContext: CoroutineContext) {
        do {
            val call = encodeCall!!
            suspend {
                codec.run {
                    encode(call.value)
                }
            }.startCoroutine(
                Continuation(coroutineContext) {
                    when (val k = call.k) {
                        null -> {
                            encodeCall = null
                            it.getOrThrow()
                        }

                        else -> k.resumeWith(it)
                    }
                }
            )
        } while (encodeCall != null)
    }

    private
    fun ReadContext.decodeLoop(coroutineContext: CoroutineContext): T? {
        var result: T? = null
        do {
            val call = decodeCall!!
            suspend {
                codec.run { decode() }
            }.startCoroutine(
                Continuation(coroutineContext) {
                    when (val k = call.k) {
                        null -> {
                            decodeCall = null
                            result = it.getOrThrow()
                        }

                        else -> k.resumeWith(it)
                    }
                }
            )
        } while (decodeCall != null)
        return result
    }
}


private
class DecodeFrame<T>(val k: Continuation<T>?)


private
data class EncodeFrame<T>(val value: T, val k: Continuation<Unit>?)


private
data class SingletonCodec<T>(
    private val singleton: T
) : Codec<T> {
    override suspend fun WriteContext.encode(value: T) = Unit
    override suspend fun ReadContext.decode(): T? = singleton
}


data class SerializerCodec<T>(val serializer: Serializer<T>) : Codec<T> {
    override suspend fun WriteContext.encode(value: T) = serializer.write(this, value)
    override suspend fun ReadContext.decode(): T = serializer.read(this)
}


fun WriteContext.writeClassArray(values: Array<Class<*>>) {
    writeArray(values) { writeClass(it) }
}


fun ReadContext.readClassArray(): Array<Class<*>> =
    readArray { readClass() }


suspend fun ReadContext.readList(): List<Any?> =
    readList { read() }


inline fun <T : Any?> ReadContext.readList(readElement: () -> T): List<T> =
    readCollectionInto({ size -> ArrayList(size) }) {
        readElement()
    }


suspend fun WriteContext.writeCollection(value: Collection<*>) {
    writeCollection(value) { write(it) }
}


suspend fun <T : MutableCollection<Any?>> ReadContext.readCollectionInto(factory: (Int) -> T): T =
    readCollectionInto(factory) { read() }


suspend fun WriteContext.writeMap(value: Map<*, *>) {
    writeSmallInt(value.size)
    writeMapEntries(value)
}


suspend fun WriteContext.writeMapEntries(value: Map<*, *>) {
    for (entry in value.entries) {
        write(entry.key)
        write(entry.value)
    }
}


suspend fun <T : MutableMap<Any?, Any?>> ReadContext.readMapInto(factory: (Int) -> T): T {
    val size = readSmallInt()
    val items = factory(size)
    readMapEntriesInto(items, size)
    return items
}


suspend fun <K, V, T : MutableMap<K, V>> ReadContext.readMapEntriesInto(items: T, size: Int) {
    @Suppress("unchecked_cast")
    repeat(size) {
        val key = read() as K
        val value = read() as V
        items[key] = value
    }
}


fun Encoder.writeFile(file: File) {
    BaseSerializerFactory.FILE_SERIALIZER.write(this, file)
}


fun Decoder.readFile(): File =
    BaseSerializerFactory.FILE_SERIALIZER.read(this)


fun Encoder.writeStrings(strings: Collection<String>) {
    writeCollection(strings) {
        writeString(it)
    }
}


fun Decoder.readStrings(): List<String> =
    readCollectionInto({ size -> ArrayList(size) }) {
        readString()
    }


fun Decoder.readStringsSet(): Set<String> =
    readCollectionInto({ size -> LinkedHashSet(size) }) {
        readString()
    }


inline fun <T> Encoder.writeCollection(collection: Collection<T>, writeElement: (T) -> Unit) {
    writeSmallInt(collection.size)
    for (element in collection) {
        writeElement(element)
    }
}


inline fun Decoder.readCollection(readElement: () -> Unit) {
    val size = readSmallInt()
    repeat(size) {
        readElement()
    }
}


inline fun <T, C : MutableCollection<T>> Decoder.readCollectionInto(
    containerForSize: (Int) -> C,
    readElement: () -> T
): C = buildCollection(containerForSize) {
    add(readElement())
}


inline fun <T, C> Decoder.buildCollection(
    containerForSize: (Int) -> C,
    readElement: C.() -> T
): C {
    val size = readSmallInt()
    val container = containerForSize(size)
    repeat(size) {
        container.readElement()
    }
    return container
}


inline fun <T : Any?> WriteContext.writeArray(array: Array<T>, writeElement: (T) -> Unit) {
    writeClass(array.javaClass.componentType)
    writeSmallInt(array.size)
    for (element in array) {
        writeElement(element)
    }
}


inline fun <T : Any?> ReadContext.readArray(readElement: () -> T): Array<T> {
    val componentType = readClass()
    val size = readSmallInt()
    val array: Array<T> = java.lang.reflect.Array.newInstance(componentType, size).uncheckedCast()
    for (i in 0 until size) {
        array[i] = readElement()
    }
    return array
}


fun <E : Enum<E>> Encoder.writeEnum(value: E) {
    writeSmallInt(value.ordinal)
}


inline fun <reified E : Enum<E>> Decoder.readEnum(): E =
    readSmallInt().let { ordinal -> enumValues<E>()[ordinal] }


inline
fun <reified T : Any> ReadContext.readClassOf(): Class<out T> =
    readClass().asSubclass(T::class.java)


/**
 * Workaround for serializing JDK types with complex/opaque state on Java 17+.
 *
 * **IMPORTANT** Should be avoided for composite/container types as all components would be serialized
 * using Java serialization.
 */
fun WriteContext.encodeUsingJavaSerialization(value: Any) {
    ObjectOutputStream(outputStream).useToRun {
        writeObject(value)
    }
}


fun ReadContext.decodeUsingJavaSerialization(): Any? =
    ObjectInputStream(inputStream).readObject()
