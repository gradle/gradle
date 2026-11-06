/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl.serialization.codecs

import org.gradle.internal.cc.base.exceptions.ConfigurationCacheError
import org.gradle.internal.serialize.BaseSerializerFactory.BOOLEAN_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.INTEGER_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.codecs.BindingTags
import org.gradle.internal.serialize.graph.codecs.Bindings
import org.gradle.internal.serialize.graph.codecs.Decoding
import org.gradle.internal.serialize.graph.codecs.Encoding
import org.gradle.internal.serialize.graph.codecs.EncodingProducer
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BindingsBackedCodecTest : AbstractUserTypeCodecTest() {
    @Test
    fun `shows reasonable error message when tag value is out of range`() {
        val writeCodec = Bindings.of {
            bind(BOOLEAN_SERIALIZER)
            bind(INTEGER_SERIALIZER)
            bind(STRING_SERIALIZER)
        }.build()

        // Emulating tag mismatch.
        val readCodec = Bindings.of {
            bind(STRING_SERIALIZER)
        }.build()

        val bytes = writeToByteArray("some value", writeCodec)
        try {
            readFromByteArray(bytes, readCodec)
            fail("Expected exception to be thrown")
        } catch (e: ConfigurationCacheError) {
            // Expected
            assertThat(e.message, containsString("Cannot deserialize the value because the type tag 2 is not in the valid range [-1..1)"))
        }
    }

    @Test
    fun `codecs sharing binding tags select the binding for a type only once`() {
        val countingProducer = NonMatchingCountingProducer()
        val base = Bindings.of {
            bind(countingProducer)
            bind(STRING_SERIALIZER)
        }
        val tags = BindingTags()
        // Defining the bindings checks that no type is bound twice
        countingProducer.lookups.clear()

        repeat(3) {
            val codec = base.append { bind(INTEGER_SERIALIZER) }.build(tags)
            val bytes = writeToByteArray("value", codec)
            assertEquals("value", readFromByteArray(bytes, codec))
        }

        assertEquals("String lookups: ${countingProducer.lookups}", 1, countingProducer.lookups[String::class.java])
        assertTrue("Each type is looked up once: ${countingProducer.lookups}", countingProducer.lookups.values.all { it == 1 })
    }

    @Test
    fun `cannot share binding tags between bindings of different structure`() {
        val tags = BindingTags()
        Bindings.of { bind(STRING_SERIALIZER) }.build(tags)

        val e = assertThrows(IllegalArgumentException::class.java) {
            Bindings.of {
                bind(STRING_SERIALIZER)
                bind(INTEGER_SERIALIZER)
            }.build(tags)
        }
        assertThat(e.message, containsString("Cannot share binding tags between bindings of different structure"))
    }

    @Test
    fun `cannot append a binding for a type that is already bound`() {
        val base = Bindings.of { bind(STRING_SERIALIZER) }

        repeat(2) {
            val e = assertThrows(IllegalArgumentException::class.java) {
                base.append { bind(STRING_SERIALIZER) }
            }
            assertThat(e.message, containsString("There's already an encoding for type"))
        }
    }

    private
    class NonMatchingCountingProducer : EncodingProducer, Decoding {
        val lookups = mutableMapOf<Class<*>, Int>()

        override fun encodingForType(type: Class<*>): Encoding? {
            lookups.merge(type, 1, Int::plus)
            return null
        }

        override suspend fun ReadContext.decode(): Any? = error("Not expected to decode anything")
    }
}
