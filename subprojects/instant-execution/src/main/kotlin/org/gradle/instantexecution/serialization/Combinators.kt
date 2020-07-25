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

package org.gradle.instantexecution.serialization

import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.problems.DocumentationSection
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer

import java.io.File


internal
fun <T> singleton(value: T): Codec<T> =
    SingletonCodec(value)


internal
inline fun <reified T> ownerServiceCodec() =
    codec<T>({ }, { ownerService() })


internal
inline fun <reified T : Any> unsupported(documentationSection: DocumentationSection = DocumentationSection.RequirementsDisallowedTypes): Codec<T> = codec(
    encode = { value ->
        logUnsupported("serialize", T::class, value.javaClass, documentationSection)
    },
    decode = {
        logUnsupported("deserialize", T::class, documentationSection)
        null
    }
)


internal
fun <T> codec(
    encode: suspend WriteContext.(T) -> Unit,
    decode: suspend ReadContext.() -> T?
): Codec<T> = object : Codec<T> {
    override suspend fun WriteContext.encode(value: T) = encode(value)
    override suspend fun ReadContext.decode(): T? = decode()
}


internal
inline fun <reified T> ReadContext.ownerService() =
    ownerService(T::class.java)


internal
fun <T> ReadContext.ownerService(serviceType: Class<T>) =
    isolate.owner.service(serviceType)


private
data class SingletonCodec<T>(
    private val singleton: T
) : Codec<T> {
    override suspend fun WriteContext.encode(value: T) = Unit
    override suspend fun ReadContext.decode(): T? = singleton
}


internal
data class SerializerCodec<T>(val serializer: Serializer<T>) : Codec<T> {
    override suspend fun WriteContext.encode(value: T) = serializer.write(this, value)
    override suspend fun ReadContext.decode(): T = serializer.read(this)
}


internal
fun WriteContext.writeClassArray(values: Array<Class<*>>) {
    writeArray(values) { writeClass(it) }
}


internal
fun ReadContext.readClassArray(): Array<Class<*>> =
    readArray { readClass() }


internal
suspend fun ReadContext.readList(): List<Any?> =
    readList { read() }


internal
inline fun <T : Any?> ReadContext.readList(readElement: () -> T): List<T> =
    readCollectionInto({ size -> ArrayList<T>(size) }) {
        readElement()
    }


internal
suspend fun WriteContext.writeCollection(value: Collection<*>) {
    writeCollection(value) { write(it) }
}


internal
suspend fun <T : MutableCollection<Any?>> ReadContext.readCollectionInto(factory: (Int) -> T): T =
    readCollectionInto(factory) { read() }


internal
suspend fun WriteContext.writeMap(value: Map<*, *>) {
    writeSmallInt(value.size)
    writeMapEntries(value)
}


internal
suspend fun WriteContext.writeMapEntries(value: Map<*, *>) {
    for (entry in value.entries) {
        write(entry.key)
        write(entry.value)
    }
}


internal
suspend fun <T : MutableMap<Any?, Any?>> ReadContext.readMapInto(factory: (Int) -> T): T {
    val size = readSmallInt()
    val items = factory(size)
    readMapEntriesInto(items, size)
    return items
}


internal
suspend fun <K, V, T : MutableMap<K, V>> ReadContext.readMapEntriesInto(items: T, size: Int) {
    @Suppress("unchecked_cast")
    for (i in 0 until size) {
        val key = read() as K
        val value = read() as V
        items[key] = value
    }
}


internal
fun Encoder.writeClassPath(classPath: ClassPath) {
    writeCollection(classPath.asFiles) {
        writeFile(it)
    }
}


internal
fun Decoder.readClassPath(): ClassPath =
    DefaultClassPath.of(
        readCollectionInto({ size -> LinkedHashSet<File>(size) }) {
            readFile()
        }
    )


internal
fun Encoder.writeFile(file: File?) {
    BaseSerializerFactory.FILE_SERIALIZER.write(this, file)
}


internal
fun Decoder.readFile(): File =
    BaseSerializerFactory.FILE_SERIALIZER.read(this)


internal
fun Encoder.writeStrings(strings: Collection<String>) {
    writeCollection(strings) {
        writeString(it)
    }
}


internal
fun Decoder.readStrings(): List<String> =
    readCollectionInto({ size -> ArrayList(size) }) {
        readString()
    }


internal
inline fun <T> Encoder.writeCollection(collection: Collection<T>, writeElement: (T) -> Unit) {
    writeSmallInt(collection.size)
    for (element in collection) {
        writeElement(element)
    }
}


internal
inline fun Decoder.readCollection(readElement: () -> Unit) {
    val size = readSmallInt()
    for (i in 0 until size) {
        readElement()
    }
}


internal
inline fun <T, C : MutableCollection<T>> Decoder.readCollectionInto(
    containerForSize: (Int) -> C,
    readElement: () -> T
): C {
    val size = readSmallInt()
    val container = containerForSize(size)
    for (i in 0 until size) {
        container.add(readElement())
    }
    return container
}


internal
inline fun <T : Any?> WriteContext.writeArray(array: Array<T>, writeElement: (T) -> Unit) {
    writeClass(array.javaClass.componentType)
    writeSmallInt(array.size)
    for (element in array) {
        writeElement(element)
    }
}


internal
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


fun Encoder.writeShort(value: Short) {
    BaseSerializerFactory.SHORT_SERIALIZER.write(this, value)
}


fun Decoder.readShort(): Short =
    BaseSerializerFactory.SHORT_SERIALIZER.read(this)


fun Encoder.writeFloat(value: Float) {
    BaseSerializerFactory.FLOAT_SERIALIZER.write(this, value)
}


fun Decoder.readFloat(): Float =
    BaseSerializerFactory.FLOAT_SERIALIZER.read(this)


fun Encoder.writeDouble(value: Double) {
    BaseSerializerFactory.DOUBLE_SERIALIZER.write(this, value)
}


fun Decoder.readDouble(): Double =
    BaseSerializerFactory.DOUBLE_SERIALIZER.read(this)


inline
fun <reified T : Any> ReadContext.readClassOf() =
    readClass().asSubclass(T::class.java)
