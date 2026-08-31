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

import org.gradle.api.Named
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
 * Codec for [Named] instances created by `ObjectFactory.named()`. Their implementation class is
 * generated on demand (by `NamedObjectInstantiator`) and is not guaranteed to exist at decoding
 * time — e.g. after a daemon restart — so they cannot be serialized as regular beans.
 *
 * Instead, they are serialized via the [Managed] protocol.
 * A custom class implementing [Named] directly is not [Managed] and is serialized as a regular bean.
 */
class NamedCodec(
    private val managedFactory: ManagedFactoryRegistry
) : Decoding, EncodingProducer {

    override fun encodingForType(type: Class<*>): Encoding? =
        NamedEncoding.takeIf {
            Named::class.java.isAssignableFrom(type) &&
                Managed::class.java.isAssignableFrom(type) &&
                !GeneratedSubclass::class.java.isAssignableFrom(type)
        }

    override suspend fun ReadContext.decode(): Named =
        decodePreservingIdentity { id ->
            val factoryId = readSmallInt()
            val type = readClass()
            val state = read()
            val value = managedFactory.lookup(factoryId).fromState(type, state)
                ?: error("Failed to recreate Named value of type $type from state $state")
            isolate.identities.putInstance(id, value as Named)
            value
        }
}

private
object NamedEncoding : Encoding {
    override suspend fun WriteContext.encode(value: Any) {
        val managed = value as Managed
        encodePreservingIdentityOf(managed) {
            writeSmallInt(managed.factoryId)
            writeClass(managed.publicType())
            write(managed.unpackState())
        }
    }
}
