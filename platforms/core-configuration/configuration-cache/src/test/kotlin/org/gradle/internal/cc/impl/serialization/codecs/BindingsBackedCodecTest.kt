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
import org.gradle.internal.serialize.graph.codecs.Bindings
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
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
}
