/*
 * Copyright 2021 the original author or authors.
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

import spock.lang.Specification
import spock.lang.Unroll


class LineEndingAwareInputStreamHasherTest extends Specification {
    @Unroll
    def "handles read when #description"() {
        def stream = inputStream(text)

        expect:
        readAllBytes(stream, 8) == normalizedText.bytes

        where:
        text              | normalizedText  | description
        "\r1234567"       | "\n1234567"     | "first character in stream is a line ending"
        "\r\n1234567"     | "\n1234567"     | "first character in stream is a multi-character line ending"
        "1234567\r"       | "1234567\n"     | "last character in stream is a line ending"
        "1234567\r\n"     | "1234567\n"     | "last character in stream is a multi-character line ending"
        "1234567\r1234"   | "1234567\n1234" | "last character in buffer is a line ending"
        "123456\r\n1234"  | "123456\n1234"  | "last character in buffer is a multi-character line ending"
        "1234567\r\n1234" | "1234567\n1234" | "multi-character line ending crosses buffer boundary"
    }

    static byte[] readAllBytes(InputStream inputStream, int bufferLength) {
        def streamHasher = new AbstractLineEndingAwareHasher.LineEndingAwareInputStreamHasher()
        ArrayList<Byte> bytes = []
        byte[] buffer = new byte[bufferLength]
        int read
        while ((read = streamHasher.read(inputStream, buffer)) != -1) {
            bytes.addAll(buffer[0..(read-1)].collect { Byte.valueOf(it) })
        }
        return bytes as byte[]
    }

    static InputStream inputStream(String input) {
        return inputStream(input.bytes)
    }

    static InputStream inputStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes)
    }
}
