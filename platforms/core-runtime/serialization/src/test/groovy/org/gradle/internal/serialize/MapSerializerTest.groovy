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

class MapSerializerTest extends SerializerSpec {

    def stringSerializer = new NullSafeStringSerializer()

    def "retains order of serialized entries"() {
        when:
        def serializer = new MapSerializer(BaseSerializerFactory.LONG_SERIALIZER, stringSerializer)
        Map values = serialize([10L: "one", 2L: "two", 30L: "three"], serializer) as Map

        then:
        values.keySet() as List == [10L, 2L, 30L]
    }

    def "serialize map"() {
        when:
        def serializer = new MapSerializer(BaseSerializerFactory.LONG_SERIALIZER, stringSerializer)

        then:
        serialize([1L: "one", 2L: "two", 3L: "three"], serializer) == [1L: "one", 2L: "two", 3L: "three"]
    }

    def "serialize null value"() {
        when:
        def serializer = new MapSerializer(BaseSerializerFactory.LONG_SERIALIZER, stringSerializer)

        then:
        serialize([1L: "one", 2L: null], serializer) == [1L: "one", 2L: null]
    }
}
