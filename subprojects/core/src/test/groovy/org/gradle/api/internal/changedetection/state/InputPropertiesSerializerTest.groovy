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

package org.gradle.api.internal.changedetection.state

import org.gradle.api.GradleException
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.MapSerializer
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import spock.lang.Specification
import spock.lang.Subject

class InputPropertiesSerializerTest extends Specification {

    def output = new ByteArrayOutputStream()
    def encoder = new OutputStreamBackedEncoder(output)

    @Subject serializer = new InputPropertiesSerializer(this.class.getClassLoader())

    def "serializes empty properties"() {
        write [:]
        expect:
        [:] == written
    }

    def "serializes properties"() {
        write([a: "x", b: "y"])
        expect:
        [a: "x", b: "y"] == written
    }

    def "serializes properties with custom classes"() {
        write([a: new SomeSerializableObject(x:10)])
        expect:
        written["a"].x == 10
    }

    def "informs which properties are not serializable"() {
        when: write([a: 'x', b: new SomeNotSerializableObject()])
        then:
        def ex = thrown(GradleException)
        ex.message == "Unable to store task input properties. Property 'b' with value 'I'm not serializable' cannot be serialized."
        ex.cause.class == MapSerializer.EntrySerializationException
    }

    static class SomeSerializableObject implements Serializable {
        int x
    }

    static class SomeNotSerializableObject {
        public String toString() { "I'm not serializable" }
    }

    private Map<String, Object> getWritten() {
        serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(output.toByteArray())))
    }

    private void write(Map map) {
        serializer.write(encoder, map)
    }
}
