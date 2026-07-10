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

package org.gradle.internal.serialize.graph.codecs

import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test


class BindingsBackedCodecEncodingLookupTest {
    private interface Base
    private open class Mid : Base
    private class Leaf : Mid()
    private class Unrelated

    private val midCodec = object : Codec<Mid> {
        override suspend fun WriteContext.encode(value: Mid) = Unit
        override suspend fun ReadContext.decode(): Mid? = null
    }

    private val codec = Bindings.of { bind(Mid::class.java, midCodec) }.build()

    @Test
    fun `encodingForType returns registered codec for exact type`() {
        assertSame(midCodec, codec.encodingForType(Mid::class.java))
    }

    @Test
    fun `encodingForType returns registered codec for subtype`() {
        assertSame(midCodec, codec.encodingForType(Leaf::class.java))
    }

    @Test
    fun `encodingForType returns null for unrelated type`() {
        assertNull(codec.encodingForType(Unrelated::class.java))
    }

    @Test
    fun `encodingForType returns null for supertype of registered type`() {
        assertNull(codec.encodingForType(Base::class.java))
    }

    @Test
    fun `encodingForType caches the result of the binding walk`() {
        // Prove reuse by construction: count how many times the underlying binding
        // walk runs. If the second lookup went through `computeEncoding` instead of
        // reusing the cache entry, the counter would tick a second time.
        val countingBinding = CountingEncodingProducer(
            matches = { Mid::class.java.isAssignableFrom(it) }
        )
        val codec = Bindings.of { bind(countingBinding, TrivialDecoding) }.build()

        codec.encodingForType(Leaf::class.java)
        codec.encodingForType(Leaf::class.java)

        assertEquals(1, countingBinding.walkCount)
    }

    @Test
    fun `encodingForType caches a lookup miss so repeated queries do not re-walk`() {
        // Miss path also goes through `computeIfAbsent` (stores a `noMatch` sentinel),
        // so a repeated query for an unregistered type must not re-walk the bindings.
        val countingBinding = CountingEncodingProducer(matches = { false })
        val codec = Bindings.of { bind(countingBinding, TrivialDecoding) }.build()

        assertNull(codec.encodingForType(Unrelated::class.java))
        assertNull(codec.encodingForType(Unrelated::class.java))

        assertEquals(1, countingBinding.walkCount)
    }

    private class CountingEncodingProducer(
        private val matches: (Class<*>) -> Boolean
    ) : EncodingProducer {
        var walkCount = 0
            private set

        override fun encodingForType(type: Class<*>): Encoding? {
            walkCount++
            return if (matches(type)) TrivialEncoding else null
        }
    }

    private object TrivialEncoding : Encoding {
        override suspend fun WriteContext.encode(value: Any) = Unit
    }

    private object TrivialDecoding : Decoding {
        override suspend fun ReadContext.decode(): Any? = null
    }
}
