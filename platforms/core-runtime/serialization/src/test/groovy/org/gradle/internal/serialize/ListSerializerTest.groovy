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

class ListSerializerTest extends SerializerSpec {

    def stringSerializer = new NullSafeStringSerializer()

    def "serialize list of strings"() {
        when:
        def serializer = new ListSerializer(stringSerializer)

        then:
        serialize(["one", "two", "three"], serializer) == ["one", "two", "three"]
    }

    def "serialize list of longs"() {
        when:
        def serializer = new ListSerializer(BaseSerializerFactory.LONG_SERIALIZER)

        then:
        serialize([10L, 5L, 99L], serializer) == [10L, 5L, 99L]
    }

    def "serialize null entry"() {
        when:
        def serializer = new ListSerializer(stringSerializer)

        then:
        serialize(["one", null, "three"], serializer) == ["one", null, "three"]
    }
}
