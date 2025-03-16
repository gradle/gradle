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

package org.gradle.internal.serialize.codecs.stdlib

import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.readArray
import org.gradle.internal.serialize.graph.writeArray
import org.gradle.internal.serialize.DecoderExtensions.readLengthPrefixedBooleans
import org.gradle.internal.serialize.DecoderExtensions.readLengthPrefixedChars
import org.gradle.internal.serialize.DecoderExtensions.readLengthPrefixedDoubles
import org.gradle.internal.serialize.DecoderExtensions.readLengthPrefixedFloats
import org.gradle.internal.serialize.DecoderExtensions.readLengthPrefixedInts
import org.gradle.internal.serialize.DecoderExtensions.readLengthPrefixedLongs
import org.gradle.internal.serialize.DecoderExtensions.readLengthPrefixedShorts
import org.gradle.internal.serialize.EncoderExtensions.writeLengthPrefixedBooleans
import org.gradle.internal.serialize.EncoderExtensions.writeLengthPrefixedChars
import org.gradle.internal.serialize.EncoderExtensions.writeLengthPrefixedDoubles
import org.gradle.internal.serialize.EncoderExtensions.writeLengthPrefixedFloats
import org.gradle.internal.serialize.EncoderExtensions.writeLengthPrefixedInts
import org.gradle.internal.serialize.EncoderExtensions.writeLengthPrefixedLongs
import org.gradle.internal.serialize.EncoderExtensions.writeLengthPrefixedShorts


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
    override suspend fun WriteContext.encode(value: ShortArray) =
        writeLengthPrefixedShorts(this, value)

    override suspend fun ReadContext.decode() =
        readLengthPrefixedShorts(this)
}


object IntArrayCodec : Codec<IntArray> {
    override suspend fun WriteContext.encode(value: IntArray) =
        writeLengthPrefixedInts(this, value)

    override suspend fun ReadContext.decode() =
        readLengthPrefixedInts(this)
}


object LongArrayCodec : Codec<LongArray> {
    override suspend fun WriteContext.encode(value: LongArray) =
        writeLengthPrefixedLongs(this, value)

    override suspend fun ReadContext.decode() =
        readLengthPrefixedLongs(this)
}


object FloatArrayCodec : Codec<FloatArray> {
    override suspend fun WriteContext.encode(value: FloatArray) =
        writeLengthPrefixedFloats(this, value)

    override suspend fun ReadContext.decode() =
        readLengthPrefixedFloats(this)
}


object DoubleArrayCodec : Codec<DoubleArray> {
    override suspend fun WriteContext.encode(value: DoubleArray) =
        writeLengthPrefixedDoubles(this, value)

    override suspend fun ReadContext.decode() =
        readLengthPrefixedDoubles(this)
}


object CharArrayCodec : Codec<CharArray> {
    override suspend fun WriteContext.encode(value: CharArray) =
        writeLengthPrefixedChars(this, value)

    override suspend fun ReadContext.decode() =
        readLengthPrefixedChars(this)
}


object BooleanArrayCodec : Codec<BooleanArray> {
    override suspend fun WriteContext.encode(value: BooleanArray) =
        writeLengthPrefixedBooleans(this, value)

    override suspend fun ReadContext.decode() =
        readLengthPrefixedBooleans(this)
}
