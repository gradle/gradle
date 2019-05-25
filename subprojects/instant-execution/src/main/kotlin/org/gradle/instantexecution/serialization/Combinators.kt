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

import org.gradle.api.internal.project.ProjectInternal
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
inline fun <reified T> ownerProjectService() =
    codec<T>({ }, { readProjectService() })


internal
fun <T> codec(
    encode: WriteContext.(T) -> Unit,
    decode: ReadContext.() -> T?
): Codec<T> = object : Codec<T> {
    override fun WriteContext.encode(value: T) = encode(value)
    override fun ReadContext.decode(): T? = decode()
}


private
inline fun <reified T> ReadContext.readProjectService() =
    ownerProject.services.get(T::class.java)


internal
val IsolateContext.ownerProject
    get() = isolate.owner.project as ProjectInternal


private
data class SingletonCodec<T>(
    private val singleton: T
) : Codec<T> {
    override fun WriteContext.encode(value: T) = Unit
    override fun ReadContext.decode(): T? = singleton
}


internal
data class SerializerCodec<T>(val serializer: Serializer<T>) : Codec<T> {
    override fun WriteContext.encode(value: T) = serializer.write(this, value)
    override fun ReadContext.decode(): T = serializer.read(this)
}


internal
fun WriteContext.writeClass(value: Class<*>) {
    writeString(value.name)
}


internal
fun ReadContext.readClass(): Class<*> =
    classLoader.loadClass(readString())


internal
fun ReadContext.readList() = readCollectionInto { size -> ArrayList<Any?>(size) }


internal
fun ReadContext.readSet() = readCollectionInto { size -> LinkedHashSet<Any?>(size) }


internal
fun WriteContext.writeCollection(value: Collection<*>) {
    writeCollection(value) { write(it) }
}


internal
fun <T : MutableCollection<Any?>> ReadContext.readCollectionInto(factory: (Int) -> T): T =
    readCollectionInto(factory) { read() }


internal
fun WriteContext.writeMap(value: Map<*, *>) {
    writeSmallInt(value.size)
    for (entry in value.entries) {
        write(entry.key)
        write(entry.value)
    }
}


internal
fun ReadContext.readMap(): Map<Any?, Any?> {
    val size = readSmallInt()
    val items = LinkedHashMap<Any?, Any?>()
    for (i in 0 until size) {
        val key = read()
        val value = read()
        items[key] = value
    }
    return items
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
fun Encoder.writeStrings(strings: List<String>) {
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
fun <T> Encoder.writeCollection(collection: Collection<T>, writeElement: (T) -> Unit) {
    writeSmallInt(collection.size)
    for (element in collection) {
        writeElement(element)
    }
}


internal
fun Decoder.readCollection(readElement: () -> Unit) {
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
