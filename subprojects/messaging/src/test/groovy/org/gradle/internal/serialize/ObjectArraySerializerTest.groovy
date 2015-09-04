/*
 * Copyright 2015 the original author or authors.
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

class ObjectArraySerializerTest extends SerializerSpec {
    def serializer = new ObjectArraySerializer(new BaseSerializerFactory().getSerializerFor(String))

    def "can serialize arrays"() {
        expect:
        serialize(elements as Object[], serializer) == elements as Object[]

        where:
        elements        | _
        []              | _
        ["a", "b", "c"] | _
    }
}
