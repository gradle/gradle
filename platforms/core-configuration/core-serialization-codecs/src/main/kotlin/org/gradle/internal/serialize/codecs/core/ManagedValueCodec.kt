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

package org.gradle.internal.serialize.codecs.core

import org.gradle.api.internal.GeneratedSubclass
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.Decoding
import org.gradle.internal.serialize.graph.codecs.Encoding
import org.gradle.internal.serialize.graph.codecs.EncodingProducer
import org.gradle.internal.serialize.graph.decodePreservingIdentity
import org.gradle.internal.serialize.graph.encodePreservingIdentityOf
import org.gradle.internal.state.Managed
import org.gradle.internal.state.ManagedFactoryRegistry


/**
 * Codec for values that implement [Managed]. Serializes them via the Managed protocol
 * ([Managed.getFactoryId] + [Managed.publicType] + [Managed.unpackState]) so they can be
 * reconstructed by the corresponding [org.gradle.internal.state.ManagedFactory] without
 * requiring the generated implementation class to be available at decoding time.
 *
 * Only matches types that implement [Managed] but do NOT implement
 * [GeneratedSubclass] (they are covered by DefaultClassEncoder).
 */
class ManagedValueCodec(
    private val managedFactory: ManagedFactoryRegistry
) : Decoding, EncodingProducer {

    override fun encodingForType(type: Class<*>): Encoding? =
        ManagedValueEncoding.takeIf {
            Managed::class.java.isAssignableFrom(type) &&
                !GeneratedSubclass::class.java.isAssignableFrom(type)
        }

    override suspend fun ReadContext.decode(): Managed =
        decodePreservingIdentity { id ->
            val factoryId = readSmallInt()
            val type = readClass()
            val state = read()
            val value = managedFactory.lookup(factoryId).fromState(type, state)
                ?: error("Failed to recreate managed value of type $type from state $state")
            isolate.identities.putInstance(id, value as Managed)
            value
        }
}

private
object ManagedValueEncoding : Encoding {
    override suspend fun WriteContext.encode(value: Any) {
        encodePreservingIdentityOf(value as Managed) {
            writeSmallInt(value.factoryId)
            writeClass(value.publicType())
            write(value.unpackState())
        }
    }
}
