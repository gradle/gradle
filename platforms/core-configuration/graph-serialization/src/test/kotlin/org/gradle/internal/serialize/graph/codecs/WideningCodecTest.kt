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

import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WideningCodecTest {
    @Test
    fun `publicDecodedType defaults to decodedType`() {
        val codec = wideningCodec<String>(String::class.java)
        assertSame(String::class.java, codec.publicDecodedType)
    }

    @Test
    fun `publicDecodedType can be overridden independently of decodedType`() {
        val codec = object : WideningCodec<String> {
            override val decodedType: Class<String> = String::class.java
            override val publicDecodedType: Class<*> = CharSequence::class.java
            override val wideningFix: String = "Use a different type"
            override suspend fun WriteContext.encode(value: String) = Unit
            override suspend fun ReadContext.decode(): String? = null
        }
        assertEquals(CharSequence::class.java, codec.publicDecodedType)
        assertEquals(String::class.java, codec.decodedType)
    }

    @Test
    fun `wideningFix is exposed on the codec`() {
        val codec = wideningCodec<String>(String::class.java, "Use a different type")
        assertEquals("Use a different type", codec.wideningFix)
    }

    private fun <T : Any> wideningCodec(
        type: Class<T>,
        resolution: String = "Use a supported type instead"
    ): WideningCodec<T> =
        object : WideningCodec<T> {
            override val decodedType: Class<T> = type
            override val wideningFix: String = resolution
            override suspend fun WriteContext.encode(value: T) = Unit
            override suspend fun ReadContext.decode(): T? = null
        }
}
