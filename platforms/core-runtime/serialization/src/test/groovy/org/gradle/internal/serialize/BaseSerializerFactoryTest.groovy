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

import java.nio.file.Path
import java.nio.file.Paths

class BaseSerializerFactoryTest extends SerializerSpec {
    def factory = new BaseSerializerFactory()

    def "uses efficient serialization for Strings"() {
        expect:
        def serializer = factory.getSerializerFor(String)
        usesEfficientSerialization("hi", serializer, 3) == "hi"
    }

    def "uses efficient serialization for Files"() {
        expect:
        def serializer = factory.getSerializerFor(File)
        usesEfficientSerialization(new File("some-file"), serializer, 10) == new File("some-file")
    }

    def "uses efficient serialization for Paths"() {
        expect:
        def serializer = factory.getSerializerFor(Path)
        def encoded = toBytes(Paths.get("some-file"), serializer)
        fromBytes(encoded, serializer) == Paths.get("some-file")
        encoded.length == 10
    }

    def "uses efficient serialization for Long"() {
        expect:
        def serializer = factory.getSerializerFor(Long)
        usesEfficientSerialization(123L, serializer) == 123L
    }

    enum Letters {
        A, B, C
    }

    def "uses efficient serialization for Enum"() {
        expect:
        def serializer = factory.getSerializerFor(Letters)
        usesEfficientSerialization(Letters.B, serializer) == Letters.B
    }

    def "uses efficient serialization for byte arrays"() {
        expect:
        def serializer = factory.getSerializerFor(byte[])
        def result = usesEfficientSerialization(new byte[5], serializer)
        result instanceof byte[]
        result.length == 5
    }

    def "uses efficient serialization for string maps"() {
        def serializer = BaseSerializerFactory.NO_NULL_STRING_MAP_SERIALIZER

        expect:
        usesEfficientSerialization(map, serializer) == map

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

    def "uses efficient serialization for booleans"() {
        expect:
        usesEfficientSerialization(true, factory.getSerializerFor(Boolean))
        !usesEfficientSerialization(false, factory.getSerializerFor(Boolean))
    }
}
