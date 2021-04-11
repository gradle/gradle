/*
 * Copyright 2021 the original author or authors.
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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap

class Int2ObjectMapSerializerTest extends SerializerSpec {

    def stringSerializer = new NullSafeStringSerializer()

    def "serialize map"() {
        when:
        def serializer = new Int2ObjectMapSerializer(stringSerializer)

        then:
        serialize([1: "one", 2: "two", 3: "three"], serializer) == [1: "one", 2: "two", 3: "three"]
    }

    def "serialize null value"() {
        when:
        def serializer = new Int2ObjectMapSerializer(stringSerializer)

        then:
        serialize([1: "one", 2: null], serializer) == [1: "one", 2: null]
    }

    def "uses efficient serialization value"() {
        when:
        def serializer = new Int2ObjectMapSerializer(stringSerializer)

        then:
        usesEfficientSerialization([1: "one", 2: null], serializer)
    }

    def "deserialize bytes to Int2ObjectMap"() {
        when:
        def serializer = new Int2ObjectMapSerializer(stringSerializer)

        then:
        serialize([1: "one", 2: null], serializer) instanceof Int2ObjectMap
    }

}
