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

package org.gradle.internal.serialize.codecs.stdlib

import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.codec
import org.gradle.internal.serialize.graph.readCollectionInto
import org.gradle.internal.serialize.graph.readList
import org.gradle.internal.serialize.graph.readMapInto
import org.gradle.internal.serialize.graph.writeCollection
import org.gradle.internal.serialize.graph.writeMap
import java.util.ArrayDeque
import java.util.Hashtable
import java.util.LinkedList
import java.util.Properties
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet


val arrayListCodec: Codec<ArrayList<Any?>> = collectionCodec { ArrayList(it) }


val linkedListCodec: Codec<LinkedList<Any?>> = collectionCodec { LinkedList() }


val arrayDequeCodec: Codec<ArrayDeque<Any?>> = collectionCodec { ArrayDeque(it) }


val copyOnWriteArrayListCodec: Codec<CopyOnWriteArrayList<Any?>> = codec(
    { writeCollection(it) },
    {
        // Avoid the overhead of copying the underlying array for each inserted element
        // by creating the COW data structure from a list.
        CopyOnWriteArrayList(readList())
    }
)


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


val copyOnWriteArraySetCodec: Codec<CopyOnWriteArraySet<Any?>> = codec(
    { writeCollection(it) },
    {
        // Avoid the overhead of copying the underlying array for each inserted element
        // by creating the COW data structure from a list.
        CopyOnWriteArraySet(readList())
    }
)


fun <T : MutableCollection<Any?>> collectionCodec(factory: (Int) -> T) = codec(
    { writeCollection(it) },
    { readCollectionInto(factory) }
)


/**
 * Decodes HashMap instances as LinkedHashMap to preserve original iteration order.
 */
val hashMapCodec: Codec<HashMap<Any?, Any?>> = mapCodec { LinkedHashMap(it) }


val linkedHashMapCodec: Codec<LinkedHashMap<Any?, Any?>> = mapCodec { LinkedHashMap(it) }


val concurrentHashMapCodec: Codec<ConcurrentHashMap<Any?, Any?>> = mapCodec { ConcurrentHashMap<Any?, Any?>(it) }


/*
 * Cannot rely on Java serialization as
 * Hashtable's readObject() calls unsupported ObjectInputStream#readFields().
 */
val hashtableCodec: Codec<Hashtable<Any?, Any?>> = mapCodec { Hashtable(it) }


/*
 * Decodes Properties as Properties instead of Hashtable.
 */
val propertiesCodec: Codec<Properties> = mapCodec { Properties() }


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


fun <T : MutableMap<Any?, Any?>> mapCodec(factory: (Int) -> T): Codec<T> = codec(
    { writeMap(it) },
    { readMapInto(factory) }
)
