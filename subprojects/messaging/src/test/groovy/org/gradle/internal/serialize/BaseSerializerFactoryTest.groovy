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

package org.gradle.internal.serialize

class BaseSerializerFactoryTest extends SerializerSpec {
    def factory = new BaseSerializerFactory()

    def "uses efficient serialization for Strings"() {
        def encoder = Mock(Encoder)
        def decoder = Mock(Decoder)

        when:
        def serializer = factory.getSerializerFor(String)
        serializer.write(encoder, "hi")
        def result = serializer.read(decoder)

        then:
        result == "bye"
        1 * encoder.writeString("hi")
        1 * decoder.readString() >> "bye"
        0 * _
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
        0 * _
    }

    def "uses efficient serialization for Long"() {
        def encoder = Mock(Encoder)
        def decoder = Mock(Decoder)

        when:
        def serializer = factory.getSerializerFor(Long)
        serializer.write(encoder, 123L)
        def result = serializer.read(decoder)

        then:
        result == 456L
        1 * encoder.writeLong(123L)
        1 * decoder.readLong() >> 456L
        0 * _
    }

    enum Letters {
        A, B, C
    }

    def "uses efficient serialization for Enum"() {
        def encoder = Mock(Encoder)
        def decoder = Mock(Decoder)

        when:
        def serializer = factory.getSerializerFor(Letters)
        serializer.write(encoder, Letters.B)
        def result = serializer.read(decoder)

        then:
        result == Letters.C
        1 * encoder.writeSmallInt(1)
        1 * decoder.readSmallInt() >> 2
        0 * _
    }

    def "uses efficient serialization for byte arrays"() {
        def s = factory.getSerializerFor(byte[])
        def os = new ByteArrayOutputStream()
        s.write(new OutputStreamBackedEncoder(os), new byte[5])

        expect:
        def result = serialize(new byte[5], s)
        result instanceof byte[]
        result.length == 5
    }

    def "can serialize string maps"() {
        def s = BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER

        expect:
        serialize(map, s) == map

        where:
        map << [
                [:], ["foo": "bar"], [a: "a", "b": "b"]
        ]
    }

    def "uses Java serialization for unknown type"() {
        expect:
        factory.getSerializerFor(Thing) instanceof DefaultSerializer
    }

    class Thing {}

    def "serialize booleans"() {
        expect:
        serialize(true, factory.getSerializerFor(Boolean))
        !serialize(false, factory.getSerializerFor(Boolean))
    }
}
