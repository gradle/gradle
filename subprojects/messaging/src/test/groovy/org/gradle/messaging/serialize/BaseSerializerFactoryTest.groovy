/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.messaging.serialize

import spock.lang.Specification

class BaseSerializerFactoryTest extends Specification {
    def factory = new BaseSerializerFactory()

    def "uses efficient serialization for Strings"() {
        def encoder = Mock(Encoder)
        def decoder = Mock(Decoder)

        when:
        def serializer = factory.getSerializerFor(String)
        serializer.write(encoder, "hi")
        serializer.read(decoder)

        then:
        1 * encoder.writeString("hi")
        1 * decoder.readString() >> "bye"
    }

    def "uses efficient serialization for Files"() {
        def encoder = Mock(Encoder)
        def decoder = Mock(Decoder)

        when:
        def serializer = factory.getSerializerFor(File)
        serializer.write(encoder, new File("some-file"))
        def result = serializer.read(decoder)

        then:
        result == new File("some-file")
        1 * encoder.writeString("some-file")
        1 * decoder.readString() >> "some-file"
    }

    def "uses efficient serialization for Long"() {
        def encoder = Mock(Encoder)
        def decoder = Mock(Decoder)

        when:
        def serializer = factory.getSerializerFor(Long)
        serializer.write(encoder, 123L)
        serializer.read(decoder)

        then:
        1 * encoder.writeLong(123L)
        1 * decoder.readLong() >> 456L
    }

    def "uses Java serialization for unknown type"() {
        expect:
        factory.getSerializerFor(Thing) instanceof DefaultSerializer
    }

    class Thing { }
}
