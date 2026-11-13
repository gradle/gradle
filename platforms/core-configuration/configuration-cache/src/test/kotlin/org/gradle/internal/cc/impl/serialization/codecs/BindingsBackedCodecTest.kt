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
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.BindingSlot
import org.gradle.internal.serialize.graph.codecs.BindingsBlueprint
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class BindingsBackedCodecTest : AbstractUserTypeCodecTest() {
    @Test
    fun `shows reasonable error message when tag value is out of range`() {
        val writeCodec = BindingsBlueprint.of {
            bind(BOOLEAN_SERIALIZER)
            bind(INTEGER_SERIALIZER)
            bind(STRING_SERIALIZER)
        }.build()

        // Emulating tag mismatch.
        val readCodec = BindingsBlueprint.of {
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
    fun `fills slots with a codec per built codec`() {
        val slot = BindingSlot(String::class.java)
        val blueprint = BindingsBlueprint.of {
            bind(INTEGER_SERIALIZER)
            bind(slot)
            bind(BOOLEAN_SERIALIZER)
        }

        val firstCodec = blueprint.build { fill(slot, PrefixingCodec("first")) }
        val secondCodec = blueprint.build { fill(slot, PrefixingCodec("second")) }

        assertThat(readFromByteArray(writeToByteArray("value", firstCodec), firstCodec), equalTo("first:value"))
        assertThat(readFromByteArray(writeToByteArray("value", secondCodec), secondCodec), equalTo("second:value"))
        assertThat(readFromByteArray(writeToByteArray(true, firstCodec), firstCodec), equalTo(true))
    }

    @Test
    fun `cannot build a codec with unfilled slots`() {
        val slot = BindingSlot(String::class.java)
        val blueprint = BindingsBlueprint.of {
            bind(slot)
        }

        assertThrows(IllegalArgumentException::class.java) { blueprint.build() }
        assertThrows(IllegalArgumentException::class.java) { blueprint.build { } }
    }

    @Test
    fun `cannot fill a slot that is not part of the blueprint`() {
        val blueprint = BindingsBlueprint.of {
            bind(BindingSlot(String::class.java))
        }

        assertThrows(IllegalArgumentException::class.java) {
            blueprint.build { fill(BindingSlot(String::class.java), PrefixingCodec("other")) }
        }
    }

    @Test
    fun `cannot fill a slot twice`() {
        val slot = BindingSlot(String::class.java)
        val blueprint = BindingsBlueprint.of {
            bind(slot)
        }

        assertThrows(IllegalArgumentException::class.java) {
            blueprint.build {
                fill(slot, PrefixingCodec("first"))
                fill(slot, PrefixingCodec("second"))
            }
        }
    }

    @Test
    fun `cannot bind a type already covered by a slot`() {
        assertThrows(IllegalArgumentException::class.java) {
            BindingsBlueprint.of {
                bind(BindingSlot(CharSequence::class.java))
                bind(STRING_SERIALIZER)
            }
        }
    }

    @Test
    fun `cannot bind a slot for a type that already has an encoding`() {
        assertThrows(IllegalArgumentException::class.java) {
            BindingsBlueprint.of {
                bind(STRING_SERIALIZER)
                bind(BindingSlot(String::class.java))
            }
        }
    }

    private
    class PrefixingCodec(private val prefix: String) : Codec<String> {
        override suspend fun WriteContext.encode(value: String) = writeString("$prefix:$value")

        override suspend fun ReadContext.decode(): String = readString()
    }
}
