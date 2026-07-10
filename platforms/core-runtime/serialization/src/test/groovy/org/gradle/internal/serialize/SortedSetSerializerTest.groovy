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

class SortedSetSerializerTest extends SerializerSpec {

    def stringSerializer = new NullSafeStringSerializer()

    def "serializes elements in sorted order regardless of input order"() {
        given:
        def serializer = new SortedSetSerializer<String>(stringSerializer)
        def input = new LinkedHashSet<String>(["cherry", "apple", "banana"])

        when:
        def result = serialize(input, serializer)

        then:
        result as List == ["apple", "banana", "cherry"]
    }

    def "round-trips empty set"() {
        given:
        def serializer = new SortedSetSerializer<String>(stringSerializer)

        when:
        def result = serialize([] as Set, serializer)

        then:
        result.isEmpty()
    }

    def "already sorted input round-trips correctly"() {
        given:
        def serializer = new SortedSetSerializer<String>(stringSerializer)

        when:
        def result = serialize(["a", "b", "c"] as Set, serializer)

        then:
        result as List == ["a", "b", "c"]
    }

    def "wire format is compatible with SetSerializer"() {
        given:
        def sortedSerializer = new SortedSetSerializer<String>(stringSerializer)
        def plainSerializer = new SetSerializer<String>(stringSerializer)
        def input = ["a", "b", "c"] as Set

        when:
        def bytes = toBytes(input, sortedSerializer)
        def result = fromBytes(bytes, plainSerializer)

        then:
        result == input
    }
}
