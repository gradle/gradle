/*
 * Copyright 2021 the original author or authors.
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

@file:Suppress("ReplaceManualRangeWithIndicesCalls") // array.indices uses Iterable and boxing, we don't want that.

package org.gradle.configurationcache.serialization.codecs

import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.readArray
import org.gradle.configurationcache.serialization.readDouble
import org.gradle.configurationcache.serialization.readFloat
import org.gradle.configurationcache.serialization.readShort
import org.gradle.configurationcache.serialization.writeArray
import org.gradle.configurationcache.serialization.writeDouble
import org.gradle.configurationcache.serialization.writeFloat
import org.gradle.configurationcache.serialization.writeShort


object NonPrimitiveArrayCodec : Codec<Array<*>> {
    override suspend fun WriteContext.encode(value: Array<*>) {
        writeArray(value) { element ->
            write(element)
        }
    }

    override suspend fun ReadContext.decode(): Array<*> =
        readArray {
            read()
        }
}


object ShortArrayCodec : Codec<ShortArray> {
    override suspend fun WriteContext.encode(value: ShortArray) {
        writeSmallInt(value.size)
        for (i in 0 until value.size) {
            writeShort(value[i])
        }
    }

    override suspend fun ReadContext.decode() = ShortArray(readSmallInt()).also { array ->
        for (i in 0 until array.size) {
            array[i] = readShort()
        }
    }
}


object IntArrayCodec : Codec<IntArray> {
    override suspend fun WriteContext.encode(value: IntArray) {
        writeSmallInt(value.size)
        for (i in 0 until value.size) {
            writeSmallInt(value[i])
        }
    }

    override suspend fun ReadContext.decode() = IntArray(readSmallInt()).also { array ->
        for (i in 0 until array.size) {
            array[i] = readSmallInt()
        }
    }
}


object LongArrayCodec : Codec<LongArray> {
    override suspend fun WriteContext.encode(value: LongArray) {
        writeSmallInt(value.size)
        for (i in 0 until value.size) {
            writeLong(value[i])
        }
    }

    override suspend fun ReadContext.decode() = LongArray(readSmallInt()).also { array ->
        for (i in 0 until array.size) {
            array[i] = readLong()
        }
    }
}


object FloatArrayCodec : Codec<FloatArray> {
    override suspend fun WriteContext.encode(value: FloatArray) {
        writeSmallInt(value.size)
        for (i in 0 until value.size) {
            writeFloat(value[i])
        }
    }

    override suspend fun ReadContext.decode() = FloatArray(readSmallInt()).also { array ->
        for (i in 0 until array.size) {
            array[i] = readFloat()
        }
    }
}


object DoubleArrayCodec : Codec<DoubleArray> {
    override suspend fun WriteContext.encode(value: DoubleArray) {
        writeSmallInt(value.size)
        for (i in 0 until value.size) {
            writeDouble(value[i])
        }
    }

    override suspend fun ReadContext.decode() = DoubleArray(readSmallInt()).also { array ->
        for (i in 0 until array.size) {
            array[i] = readDouble()
        }
    }
}


object CharArrayCodec : Codec<CharArray> {
    override suspend fun WriteContext.encode(value: CharArray) {
        writeSmallInt(value.size)
        for (i in 0 until value.size) {
            writeSmallInt(value[i].code)
        }
    }

    override suspend fun ReadContext.decode() = CharArray(readSmallInt()).also { array ->
        for (i in 0 until array.size) {
            array[i] = readSmallInt().toChar()
        }
    }
}


object BooleanArrayCodec : Codec<BooleanArray> {
    override suspend fun WriteContext.encode(value: BooleanArray) {
        writeSmallInt(value.size)
        for (i in 0 until value.size) {
            writeBoolean(value[i])
        }
    }

    override suspend fun ReadContext.decode() = BooleanArray(readSmallInt()).also { array ->
        for (i in 0 until array.size) {
            array[i] = readBoolean()
        }
    }
}
