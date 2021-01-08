/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.stream

import spock.lang.Specification

class EncodedStreamTest extends Specification {
    def "can encode and decode an empty stream"() {
        def outputStream = new ByteArrayOutputStream()
        def encoder = new EncodedStream.EncodedOutput(outputStream)

        when:
        encoder.flush()

        then:
        def inputStream = new ByteArrayInputStream(outputStream.toByteArray())
        def decoder = new EncodedStream.EncodedInput(inputStream)
        decoder.read() < 0
    }

    def "can encode and decode a string"() {
        def outputStream = new ByteArrayOutputStream()
        def encoder = new EncodedStream.EncodedOutput(outputStream)

        when:
        encoder.write("this is some content".bytes)
        encoder.flush()

        then:
        def inputStream = new ByteArrayInputStream(outputStream.toByteArray())
        def decoder = new EncodedStream.EncodedInput(inputStream)
        def content = decoder.bytes
        new String(content) == "this is some content"
    }

    def "can encode and decode binary content"() {
        def outputStream = new ByteArrayOutputStream()
        def encoder = new EncodedStream.EncodedOutput(outputStream)

        when:
        encoder.write(0)
        encoder.write(127)
        encoder.write(128)
        encoder.write(255)
        encoder.flush()

        then:
        def inputStream = new ByteArrayInputStream(outputStream.toByteArray())
        def decoder = new EncodedStream.EncodedInput(inputStream)
        decoder.read() == 0
        decoder.read() == 127
        decoder.read() == 128
        decoder.read() == 255
        decoder.read() < 0
    }
}
