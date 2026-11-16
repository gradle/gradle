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

import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.ImmutableSortedSet
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.writeCollection


object ImmutableListMultimapCodec : Codec<ImmutableListMultimap<Any, Any>> {

    override suspend fun WriteContext.encode(value: ImmutableListMultimap<Any, Any>) {
        writeMultimap(value)
    }

    override suspend fun ReadContext.decode(): ImmutableListMultimap<Any, Any>? {
        val keyCount = readSmallInt()
        if (keyCount == 0) {
            return ImmutableListMultimap.of()
        }
        val builder = ImmutableListMultimap.builder<Any, Any>()
        readMultimapInto(builder, keyCount)
        return builder.build()
    }
}


object ImmutableSetMultimapCodec : Codec<ImmutableSetMultimap<Any, Any>> {

    override suspend fun WriteContext.encode(value: ImmutableSetMultimap<Any, Any>) {
        writeMultimap(value)
        if (!value.isEmpty) {
            write(value.valueComparator())
        }
    }

    override suspend fun ReadContext.decode(): ImmutableSetMultimap<Any, Any>? {
        val keyCount = readSmallInt()
        if (keyCount == 0) {
            return ImmutableSetMultimap.of()
        }
        val builder = ImmutableSetMultimap.builder<Any, Any>()
        readMultimapInto(builder, keyCount)
        @Suppress("unchecked_cast")
        val valueComparator = read() as Comparator<Any>?
        if (valueComparator != null) {
            builder.orderValuesBy(valueComparator)
        }
        return builder.build()
    }

    /**
     * The value comparator is not exposed by [ImmutableSetMultimap], but its value sets are sorted sets if and only if it has one.
     */
    private
    fun ImmutableSetMultimap<Any, Any>.valueComparator(): Comparator<in Any>? =
        (asMap().values.first() as? ImmutableSortedSet<Any>)?.comparator()
}


/**
 * Writes the entries grouped by key, which preserves the iteration order of both the keys and the values of each key.
 */
private
suspend fun WriteContext.writeMultimap(value: ImmutableMultimap<Any, Any>) {
    writeCollection(value.asMap().entries) { (key, values) ->
        write(key)
        writeCollection(values)
    }
}


private
suspend fun ReadContext.readMultimapInto(builder: ImmutableMultimap.Builder<Any, Any>, keyCount: Int) {
    repeat(keyCount) {
        val key = read()!!
        repeat(readSmallInt()) {
            builder.put(key, read()!!)
        }
    }
}
