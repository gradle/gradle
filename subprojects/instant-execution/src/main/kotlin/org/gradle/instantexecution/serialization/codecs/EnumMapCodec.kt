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

package org.gradle.instantexecution.serialization.codecs

import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.readCollection
import org.gradle.instantexecution.serialization.readMapEntriesInto
import org.gradle.instantexecution.serialization.writeCollection
import org.gradle.instantexecution.serialization.writeMapEntries

import java.util.EnumMap
import java.util.EnumSet


object EnumMapCodec : Codec<EnumMap<*, Any?>> {

    override suspend fun WriteContext.encode(value: EnumMap<*, Any?>) {
        writeClass(keyTypeOf(value))
        writeSmallInt(value.size)
        writeMapEntries(value)
    }

    override suspend fun ReadContext.decode(): EnumMap<*, Any?>? {
        @Suppress("unchecked_cast")
        val keyType = readClass() as Class<EnumType>
        val size = readSmallInt()
        val map: MutableMap<EnumType, Any?> = EnumMap(keyType)
        readMapEntriesInto(map, size)
        return map as EnumMap<*, Any?>
    }

    private
    fun keyTypeOf(value: EnumMap<*, Any?>) =
        keyTypeField.get(value) as Class<*>

    private
    val keyTypeField by lazy {
        EnumMap::class.java.getDeclaredField("keyType").apply { isAccessible = true }
    }
}


object EnumSetCodec : Codec<EnumSet<*>> {

    override suspend fun WriteContext.encode(value: EnumSet<*>) {
        writeClass(elementTypeOf(value))
        writeCollection(value)
    }

    override suspend fun ReadContext.decode(): EnumSet<*>? {
        @Suppress("unchecked_cast")
        val elementType = readClass() as Class<EnumType>
        val set = EnumSet.noneOf(elementType)
        readCollection {
            set.add(read()!!.uncheckedCast())
        }
        return set
    }

    private
    fun elementTypeOf(value: EnumSet<*>) =
        elementTypeField.get(value) as Class<*>

    private
    val elementTypeField by lazy {
        EnumSet::class.java.getDeclaredField("elementType").apply { isAccessible = true }
    }
}


/**
 * Fools the Kotlin type-system into believing we have an [Enum] subtype above.
 */
private
enum class EnumType
