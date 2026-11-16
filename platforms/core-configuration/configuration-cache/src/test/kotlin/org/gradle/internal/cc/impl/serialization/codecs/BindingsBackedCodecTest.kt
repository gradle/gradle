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
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.Bindings
import org.gradle.internal.serialize.graph.codecs.SelectiveCodec
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThrows
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
    fun `first binding that accepts the type wins`() {
        val codec = Bindings.of {
            bind(PrefixingCodec("first", String::class.java))
            bind(PrefixingCodec("second", String::class.java))
        }.build()

        assertThat(configurationCacheRoundtripOf("value", codec), equalTo("first:value"))
    }

    @Test
    fun `cannot bind selective codec to a type`() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            Bindings.of {
                bind(String::class, PrefixingCodec("selective", String::class.java))
            }
        }

        assertThat(e.message, containsString("bind it with bind(SelectiveCodec) instead"))
    }

    @Test
    fun `asks each binding about a type only once`() {
        val rejecting = PrefixingCodec("rejecting", Int::class.javaObjectType)
        val accepting = PrefixingCodec("accepting", String::class.java)
        val codec = Bindings.of {
            bind(rejecting)
            bind(accepting)
        }.build()

        repeat(3) {
            assertThat(configurationCacheRoundtripOf("value", codec), equalTo("accepting:value"))
        }

        assertThat(rejecting.acceptsCalls, equalTo(1))
        assertThat(accepting.acceptsCalls, equalTo(1))
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
}
