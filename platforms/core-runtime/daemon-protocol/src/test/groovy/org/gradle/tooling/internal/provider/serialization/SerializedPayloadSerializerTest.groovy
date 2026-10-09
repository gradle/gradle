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

package org.gradle.tooling.internal.provider.serialization

import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.tooling.internal.provider.CustomPayload
import org.gradle.tooling.internal.provider.PayloadInterface
import org.gradle.tooling.internal.provider.WrapperPayload

/**
 * Round-trips a {@link SerializedPayload} through its on-the-wire serializer, exercising the explicit, non-Java
 * header codec end to end. {@link PayloadSerializerTest} (extended here for its classloader fixtures) stops at the
 * in-memory payload; these cases also push it through the Kryo wire format, as the daemon protocol does.
 */
class SerializedPayloadSerializerTest extends PayloadSerializerTest {
    def wire = new SerializedPayloadSerializer()

    def "wire round-trips an object from an isolated implementation classloader"() {
        def payloadClass = isolated(CustomPayload, PayloadInterface).loadClass(CustomPayload.name)
        def original = payloadClass.newInstance(value: 'value')

        when:
        def received = receiver.deserialize(wireRoundTrip(originator.serialize(original)))

        then:
        received.value == 'value'
    }

    def "wire round-trips nested objects across classloaders with a shared parent"() {
        def parent = isolated(WrapperPayload, PayloadInterface)
        def payloadClass = isolated(parent, CustomPayload).loadClass(CustomPayload.name)
        def wrapperClass = isolated(parent, WrapperPayload).loadClass(WrapperPayload.name)
        def original = wrapperClass.newInstance(payload: payloadClass.newInstance(value: 'value'))

        when:
        def received = receiver.deserialize(wireRoundTrip(originator.serialize(original)))

        then:
        received.payload.value == 'value'
    }

    private SerializedPayload wireRoundTrip(SerializedPayload payload) {
        def bytes = new ByteArrayOutputStream()
        def encoder = new KryoBackedEncoder(bytes)
        wire.write(encoder, payload)
        encoder.flush()
        wire.read(new KryoBackedDecoder(new ByteArrayInputStream(bytes.toByteArray())))
    }
}
