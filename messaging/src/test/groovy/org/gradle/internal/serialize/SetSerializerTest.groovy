/*
 * Copyright 2013 the original author or authors.
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

class SetSerializerTest extends SerializerSpec {

    def stringSerializer = new NullSafeStringSerializer()

    def "serialize set of strings"() {
        when:
        def serializer = new SetSerializer(stringSerializer)

        then:
        serialize(["one", "two", "three"] as Set, serializer) as List == ["one", "two", "three"]
    }

    def "serialize set of longs"() {
        when:
        def serializer = new SetSerializer(BaseSerializerFactory.LONG_SERIALIZER)

        then:
        serialize([1L, 5L, 99L] as Set, serializer) as List == [1L, 5L, 99L]
    }

    def "serialize null entry"() {
        when:
        def serializer = new SetSerializer(stringSerializer)

        then:
        serialize(["one", null, "three"] as Set, serializer) as List == ["one", null, "three"]
    }

    def "serializes with HasSet"() {
        when:
        def serializer = new SetSerializer(stringSerializer, false)

        then:
        serialize(['1', '2'] as Set, serializer) instanceof HashSet
    }
}