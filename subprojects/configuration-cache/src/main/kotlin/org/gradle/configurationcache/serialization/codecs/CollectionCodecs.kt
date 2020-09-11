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
import org.gradle.configurationcache.serialization.codec
import org.gradle.configurationcache.serialization.readArray
import org.gradle.configurationcache.serialization.readCollectionInto
import org.gradle.configurationcache.serialization.readMapInto
import org.gradle.configurationcache.serialization.writeArray
import org.gradle.configurationcache.serialization.writeCollection
import org.gradle.configurationcache.serialization.writeMap
import java.util.LinkedList
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap


internal
val arrayListCodec: Codec<ArrayList<Any?>> = collectionCodec { ArrayList<Any?>(it) }


internal
val linkedListCodec: Codec<LinkedList<Any?>> = collectionCodec { LinkedList<Any?>() }


/**
 * Decodes HashSet instances as LinkedHashSet to preserve original iteration order.
 */
internal
val hashSetCodec: Codec<HashSet<Any?>> = collectionCodec { LinkedHashSet<Any?>(it) }


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
    })


internal
fun <T : MutableCollection<Any?>> collectionCodec(factory: (Int) -> T) = codec(
    { writeCollection(it) },
    { readCollectionInto(factory) }
)


internal
val hashMapCodec: Codec<HashMap<Any?, Any?>> = mapCodec { HashMap<Any?, Any?>(it) }


internal
val linkedHashMapCodec: Codec<LinkedHashMap<Any?, Any?>> = mapCodec { LinkedHashMap<Any?, Any?>(it) }


internal
val concurrentHashMapCodec: Codec<ConcurrentHashMap<Any?, Any?>> = mapCodec { ConcurrentHashMap<Any?, Any?>(it) }


internal
val treeMapCodec: Codec<TreeMap<Any?, Any?>> = mapCodec { TreeMap<Any?, Any?>() }


internal
fun <T : MutableMap<Any?, Any?>> mapCodec(factory: (Int) -> T): Codec<T> = codec(
    { writeMap(it) },
    { readMapInto(factory) }
)


internal
val arrayCodec: Codec<Array<*>> = codec({
    writeArray(it) { element ->
        write(element)
    }
}, {
    readArray { read() }
})
