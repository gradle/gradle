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

package org.gradle.internal.serialize

class SortedMapSerializerTest extends SerializerSpec {

    def stringSerializer = new NullSafeStringSerializer()

    def "serializes entries in sorted key order regardless of input order"() {
        given:
        def serializer = new SortedMapSerializer<String, String>(stringSerializer, stringSerializer)
        def input = new LinkedHashMap<String, String>()
        input.put("cherry", "3")
        input.put("apple", "1")
        input.put("banana", "2")

        when:
        def result = serialize(input, serializer)

        then:
        result.keySet() as List == ["apple", "banana", "cherry"]
        result.values() as List == ["1", "2", "3"]
    }

    def "round-trips empty map"() {
        given:
        def serializer = new SortedMapSerializer<String, String>(stringSerializer, stringSerializer)

        when:
        def result = serialize([:], serializer)

        then:
        result.isEmpty()
    }

    def "already sorted input round-trips correctly"() {
        given:
        def serializer = new SortedMapSerializer<String, String>(stringSerializer, stringSerializer)

        when:
        def result = serialize(["a": "1", "b": "2", "c": "3"], serializer)

        then:
        result == ["a": "1", "b": "2", "c": "3"]
        result.keySet() as List == ["a", "b", "c"]
    }

    def "sorts both keys and set values when combined with SortedSetSerializer"() {
        given:
        def serializer = new SortedMapSerializer<String, Set<String>>(
            stringSerializer,
            new SortedSetSerializer<String>(stringSerializer)
        )
        def input = new LinkedHashMap<String, Set<String>>()
        input.put("c", new LinkedHashSet<String>(["3", "1"]))
        input.put("a", new LinkedHashSet<String>(["2"]))
        input.put("b", new LinkedHashSet<String>(["3", "1", "2"]))

        when:
        def result = serialize(input, serializer)

        then: "keys are sorted"
        result.keySet() as List == ["a", "b", "c"]

        and: "values within each key are sorted"
        result["a"] as List == ["2"]
        result["b"] as List == ["1", "2", "3"]
        result["c"] as List == ["1", "3"]
    }

    def "wire format is compatible with MapSerializer"() {
        given:
        def sortedSerializer = new SortedMapSerializer<String, String>(stringSerializer, stringSerializer)
        def plainSerializer = new MapSerializer<String, String>(stringSerializer, stringSerializer)
        def input = ["a": "1", "b": "2", "c": "3"]

        when:
        def bytes = toBytes(input, sortedSerializer)
        def result = fromBytes(bytes, plainSerializer)

        then:
        result == input
    }
}
