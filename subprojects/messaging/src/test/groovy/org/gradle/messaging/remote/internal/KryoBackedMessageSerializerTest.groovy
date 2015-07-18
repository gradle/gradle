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

package org.gradle.messaging.remote.internal

import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Serializers
import org.gradle.internal.serialize.StatefulSerializer
import spock.lang.Specification

class KryoBackedMessageSerializerTest extends Specification {
    def serializer = Mock(StatefulSerializer)
    def messageSerializer = new KryoBackedMessageSerializer<String>(Serializers.stateful(BaseSerializerFactory.STRING_SERIALIZER))

    def "creates reader and writer backed by serializer"() {
        def outputStream = new ByteArrayOutputStream()

        when:
        def writer = messageSerializer.newWriter(outputStream)
        writer.write("a")
        writer.write("b")
        writer.write("c")

        then:
        def reader = messageSerializer.newReader(new ByteArrayInputStream(outputStream.toByteArray()), null, null)
        reader.read() == "a"
        reader.read() == "b"
        reader.read() == "c"
    }
}
