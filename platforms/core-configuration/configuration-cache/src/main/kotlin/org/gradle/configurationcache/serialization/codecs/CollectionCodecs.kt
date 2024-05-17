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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.codec
import org.gradle.configurationcache.serialization.logPropertyProblem
import org.gradle.configurationcache.serialization.readCollectionInto
import org.gradle.configurationcache.serialization.readList
import org.gradle.configurationcache.serialization.readMapInto
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.configurationcache.serialization.writeMap
import java.util.Hashtable
import java.util.ArrayDeque
import java.util.LinkedList
import java.util.Properties
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet


internal
val arrayListCodec: Codec<ArrayList<Any?>> = collectionCodec { ArrayList(it) }


internal
val linkedListCodec: Codec<LinkedList<Any?>> = collectionCodec { LinkedList() }


internal
val arrayDequeCodec: Codec<ArrayDeque<Any?>> = collectionCodec { ArrayDeque(it) }


internal
val copyOnWriteArrayListCodec: Codec<CopyOnWriteArrayList<Any?>> = codec(
    { writeCollection(it) },
    {
        // Avoid the overhead of copying the underlying array for each inserted element
        // by creating the COW data structure from a list.
        CopyOnWriteArrayList(readList())
    }
)


/**
 * Decodes HashSet instances as LinkedHashSet to preserve original iteration order.
 */
internal
object HashSetCodec : Codec<HashSet<Any?>> {

    override suspend fun WriteContext.encode(value: HashSet<Any?>) {
        writeCollectionCheckingForCircularElements(value)
    }

    override suspend fun ReadContext.decode(): HashSet<Any?> =
        readCollectionCheckingForCircularElementsInto(::LinkedHashSet)
}


private
suspend fun WriteContext.writeCollectionCheckingForCircularElements(collection: Collection<Any?>) {
    var writeCircularMarker = true
    writeCollection(collection) { element ->
        if (element != null && element in circularReferences && element.javaClass.overridesHashCode()) {
            logPropertyProblem("serialize") {
                text("Circular references can lead to undefined behavior upon deserialization.")
            }
            if (writeCircularMarker) {
                write(CircularReferenceMarker.INSTANCE)
                writeCircularMarker = false
            }
        }
        write(element)
    }
}


private
suspend fun <T : MutableCollection<Any?>> ReadContext.readCollectionCheckingForCircularElementsInto(
    factory: (Int) -> T
): T {
    val size = readSmallInt()
    val container = factory(size)
    for (i in 0 until size) {
        val element = read()
        if (element === CircularReferenceMarker.INSTANCE) {
            // upon reading a circular reference, wait until all objects have been initialized
            // before inserting the elements in the resulting set.
            val remainingSize = size - i
            val remaining = ArrayList<Any?>(remainingSize).apply {
                for (j in 0 until remainingSize) {
                    add(read())
                }
            }
            onFinish {
                container.addAll(remaining)
            }
            return container
        }
        container.add(element)
    }
    return container
}


private
object CircularReferenceMarker {

    val INSTANCE: Any = Marker.INSTANCE

    private
    enum class Marker {
        INSTANCE
    }
}


private
fun Class<*>.overridesHashCode(): Boolean =
    getMethod("hashCode").declaringClass !== java.lang.Object::class.java


internal
val treeSetCodec: Codec<TreeSet<Any?>> = codec(
    {
        write(it.comparator())
        writeCollection(it)
    },
    {
        @Suppress("unchecked_cast")
        val comparator = read() as Comparator<Any?>?
        readCollectionInto { TreeSet(comparator) }
    }
)


internal
val copyOnWriteArraySetCodec: Codec<CopyOnWriteArraySet<Any?>> = codec(
    { writeCollection(it) },
    {
        // Avoid the overhead of copying the underlying array for each inserted element
        // by creating the COW data structure from a list.
        CopyOnWriteArraySet(readList())
    }
)


internal
fun <T : MutableCollection<Any?>> collectionCodec(factory: (Int) -> T) = codec(
    { writeCollection(it) },
    { readCollectionInto(factory) }
)


/**
 * Decodes HashMap instances as LinkedHashMap to preserve original iteration order.
 */
internal
val hashMapCodec: Codec<HashMap<Any?, Any?>> = mapCodec { LinkedHashMap(it) }


internal
val linkedHashMapCodec: Codec<LinkedHashMap<Any?, Any?>> = mapCodec { LinkedHashMap(it) }


internal
val concurrentHashMapCodec: Codec<ConcurrentHashMap<Any?, Any?>> = mapCodec { ConcurrentHashMap<Any?, Any?>(it) }


/*
 * Cannot rely on Java serialization as
 * Hashtable's readObject() calls unsupported ObjectInputStream#readFields().
 */
internal
val hashtableCodec: Codec<Hashtable<Any?, Any?>> = mapCodec { Hashtable(it) }


/*
 * Decodes Properties as Properties instead of Hashtable.
 */
internal
val propertiesCodec: Codec<Properties> = mapCodec { Properties() }


internal
val treeMapCodec: Codec<TreeMap<Any?, Any?>> = codec(
    {
        write(it.comparator())
        writeMap(it)
    },
    {
        @Suppress("unchecked_cast")
        val comparator = read() as Comparator<Any?>?
        readMapInto { TreeMap(comparator) }
    }
)


internal
fun <T : MutableMap<Any?, Any?>> mapCodec(factory: (Int) -> T): Codec<T> = codec(
    { writeMap(it) },
    { readMapInto(factory) }
)
