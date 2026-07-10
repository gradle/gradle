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
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests contracts of the [WideningCodec] interface itself. Interactions with the
 * lookup helpers ([findCodecThatWidensIncompatibly] etc.) live in their own tests
 * (`FindCodecThatWidensIncompatiblyTest`) — this file only covers what the
 * interface guarantees to implementers.
 */
class WideningCodecTest {
    @Test
    fun `publicDecodedType defaults to decodedType when the implementer does not override it`() {
        // The one non-trivial contract this interface carries: a codec that only
        // supplies `decodedType` and `wideningFix` sees `publicDecodedType` fall
        // through to `decodedType`. Removing that default would silently regress
        // every codec that relies on it (Configuration, SourceDirectorySet, …).
        val codec = object : WideningCodec<String> {
            override val decodedType: Class<String> = String::class.java
            override val wideningFix: String = "Use a supported type instead."
            override suspend fun WriteContext.encode(value: String) = Unit
            override suspend fun ReadContext.decode(): String? = null
        }
        assertSame(String::class.java, codec.publicDecodedType)
    }
}
