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

package org.gradle.internal.serialize.graph.codecs

import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.decodeBean
import org.gradle.internal.serialize.graph.encodeBean


object ValueObjectCodec : Codec<ValueObject> {

    override suspend fun WriteContext.encode(value: ValueObject) {
        encodeBean(value)
    }

    override suspend fun ReadContext.decode(): ValueObject =
        decodeBean().uncheckedCast()
}


/**
 * Marker interface for types that should be serialized as values (i.e., without identity-preserving deserialization).
 *
 * This makes them slightly more efficient to serialize.
 */
interface ValueObject
