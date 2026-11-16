/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize.codecs.guava

import com.google.common.collect.ConcurrentHashMultiset
import com.google.common.collect.Multiset
import com.google.common.collect.Ordering
import com.google.common.collect.TreeMultiset
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.serialize.graph.writeCollection


/**
 * Preserves the identity of the multiset, given it is mutable.
 */
object TreeMultisetCodec : Codec<TreeMultiset<Any?>> {

    override suspend fun WriteContext.encode(value: TreeMultiset<Any?>) {
        encodePreservingIdentityOf(value) {
            // Natural ordering is what a null comparator stands for when creating a TreeMultiset
            write(value.comparator().takeUnless { it === Ordering.natural<Comparable<Any>>() })
            writeMultiset(value)
        }
    }

    override suspend fun ReadContext.decode(): TreeMultiset<Any?>? =
        decodePreservingIdentity { id ->
            @Suppress("unchecked_cast")
            val comparator = read() as Comparator<Any?>?
            TreeMultiset.create<Any?>(comparator).also {
                isolate.identities.putInstance(id, it)
                readMultisetInto(it) { read() }
            }
        }
}


/**
 * Preserves the identity of the multiset, given it is mutable.
 */
object ConcurrentHashMultisetCodec : Codec<ConcurrentHashMultiset<Any>> {

    override suspend fun WriteContext.encode(value: ConcurrentHashMultiset<Any>) {
        encodePreservingIdentityOf(value) {
            writeMultiset(value)
        }
    }

    override suspend fun ReadContext.decode(): ConcurrentHashMultiset<Any>? =
        decodePreservingIdentity { id ->
            ConcurrentHashMultiset.create<Any>().also {
                isolate.identities.putInstance(id, it)
                readMultisetInto(it) { read()!! }
            }
        }
}


/**
 * Writes each distinct element once, along with its count.
 */
private
suspend fun WriteContext.writeMultiset(value: Multiset<*>) {
    writeCollection(value.entrySet()) { entry ->
        write(entry.element)
        writeSmallInt(entry.count)
    }
}


private
inline fun <E> ReadContext.readMultisetInto(multiset: Multiset<E>, readElement: () -> E) {
    repeat(readSmallInt()) {
        val element = readElement()
        multiset.add(element, readSmallInt())
    }
}
