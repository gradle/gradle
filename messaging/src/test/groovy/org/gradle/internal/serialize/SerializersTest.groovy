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

import spock.lang.Specification

class SerializersTest extends Specification {
    def "adapts Serializer to StatefulSerializer"() {
        def serializer = Mock(Serializer)
        def decoder = Mock(Decoder)
        def encoder = Mock(Encoder)

        given:
        def stateful = Serializers.stateful(serializer)
        def reader = stateful.newReader(decoder)
        def writer = stateful.newWriter(encoder)

        when:
        def result1 = reader.read()
        def result2 = reader.read()

        then:
        result1 == "one"
        result2 == "two"
        1 * serializer.read(decoder) >> "one"
        1 * serializer.read(decoder) >> "two"
        0 * serializer._

        when:
        writer.write("one")
        writer.write("two")

        then:
        1 * serializer.write(encoder, "one")
        1 * serializer.write(encoder, "two")
        0 * serializer._
    }
}
