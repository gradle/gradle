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
import org.gradle.internal.serialize.graph.codecs.SelectiveCodec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.startsWith
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
    fun `first binding that accepts the type wins`() {
        val codec = BindingsBlueprint.of {
            bind(PrefixingCodec("first", String::class.java))
            bind(PrefixingCodec("second", String::class.java))
        }.build()

        assertThat(configurationCacheRoundtripOf("value", codec), equalTo("first:value"))
    }

    @Test
    fun `cannot bind selective codec to a type`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            BindingsBlueprint.of {
                bind(String::class, PrefixingCodec("selective", String::class.java))
            }
        }

        assertThat(e.message, containsString("bind it with bind(SelectiveCodec) instead"))
    }

    @Test
    fun `asks each binding about a type only once`() {
        val rejecting = PrefixingCodec("rejecting", Int::class.javaObjectType)
        val accepting = PrefixingCodec("accepting", String::class.java)
        val codec = BindingsBlueprint.of {
            bind(rejecting)
            bind(accepting)
        }.build()

        repeat(3) {
            assertThat(configurationCacheRoundtripOf("value", codec), equalTo("accepting:value"))
        }

        assertThat(rejecting.acceptsCalls, equalTo(1))
        assertThat(accepting.acceptsCalls, equalTo(1))
    }

    @Test
    fun `fills slots with a codec per built codec`() {
        val slot = BindingSlot(String::class.java)
        val blueprint = BindingsBlueprint.of {
            bind(INTEGER_SERIALIZER)
            bind(slot)
            bind(BOOLEAN_SERIALIZER)
        }

        val firstCodec = blueprint.build { fill(slot, StringPrefixingCodec("first")) }
        val secondCodec = blueprint.build { fill(slot, StringPrefixingCodec("second")) }

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
        val slot = BindingSlot(String::class.java)
        val blueprint = BindingsBlueprint.of {
            bind(slot)
        }

        val e = assertThrows(IllegalArgumentException::class.java) {
            blueprint.build {
                fill(slot, StringPrefixingCodec("own"))
                fill(BindingSlot(String::class.java), StringPrefixingCodec("other"))
            }
        }
        assertThat(e.message, startsWith("Slots are not part of the blueprint"))
    }

    @Test
    fun `cannot fill a slot with a selective codec`() {
        val slot = BindingSlot(Any::class.java)
        val blueprint = BindingsBlueprint.of {
            bind(slot)
        }

        assertThrows(IllegalArgumentException::class.java) {
            blueprint.build { fill(slot, PrefixingCodec("selective", String::class.java)) }
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
                fill(slot, StringPrefixingCodec("first"))
                fill(slot, StringPrefixingCodec("second"))
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
    class PrefixingCodec(private val prefix: String, private val acceptedType: Class<*>) : SelectiveCodec {

        var acceptsCalls = 0

        override fun accepts(type: Class<*>): Boolean {
            acceptsCalls++
            return acceptedType.isAssignableFrom(type)
        }

        override suspend fun WriteContext.encode(value: Any) {
            writeString(value.toString())
        }

        override suspend fun ReadContext.decode(): Any =
            "$prefix:${readString()}"
    }

    private
    class StringPrefixingCodec(private val prefix: String) : Codec<String> {
        override suspend fun WriteContext.encode(value: String) = writeString("$prefix:$value")

        override suspend fun ReadContext.decode(): String = readString()
    }
}
