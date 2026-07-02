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
    fun `encodingForType result is cached`() {
        val first = codec.encodingForType(Leaf::class.java)
        assertNotNull(first)
        val second = codec.encodingForType(Leaf::class.java)
        assertSame(first, second)
    }
}
