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

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class LineEndingNormalizingInputStreamTest extends Specification {
    @Rule
    TemporaryFolder tempDir = new TemporaryFolder()

    @Unroll
    def "can normalize line endings in input streams (eol = '#description')"() {
        def stream = inputStream(textWithLineEndings(eol))

        expect:
        readAllBytesWithRead(stream) == textWithLineEndings('\n').bytes

        and:
        stream.reset()
        readAllBytesWithReadBuffer(stream, 8) == textWithLineEndings('\n').bytes

        where:
        eol     | description
        '\r'    | 'CR'
        '\r\n'  | 'CR-LF'
        '\n'    | 'LF'
    }

    @Unroll
    def "handles buffer read when #description"() {
        def stream = inputStream(text)

        expect:
        readAllBytesWithReadBuffer(stream, 8) == normalizedText.bytes

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

    def "delegate is called when operating on input stream"() {
        def delegate = Mock(InputStream)
        def inputStream = new LineEndingNormalizingInputStream(delegate)
        byte[] buffer = []

        when:
        inputStream.read()

        then:
        1 * delegate.read()

        when:
        inputStream.read(buffer)

        then:
        1 * delegate.read(buffer, 0, 0)

        when:
        inputStream.reset()

        then:
        1 * delegate.reset()

        when:
        inputStream.read(buffer, 0, 1)

        then:
        1 * delegate.read(_, 0, 1)

        when:
        inputStream.skip(1)

        then:
        1 * delegate.skip(1)

        when:
        inputStream.available()

        then:
        1 * delegate.available()

        when:
        inputStream.close()

        then:
        1 * delegate.close()
    }

    static String textWithLineEndings(String eol) {
        return "${eol}This is a line${eol}Another line${eol}${eol}Yet another line\nAnd one more\n\nAnd yet one more${eol}${eol}"
    }

    static InputStream inputStream(String input) {
        return new LineEndingNormalizingInputStream(new ByteArrayInputStream(input.bytes))
    }

    static byte[] readAllBytesWithRead(InputStream inputStream) {
        ArrayList<Byte> bytes = []
        int b
        while ((b = inputStream.read()) != -1) {
            bytes.add(Byte.valueOf((byte)b))
        }
        return bytes as byte[]
    }

    static byte[] readAllBytesWithReadBuffer(InputStream inputStream, int bufferLength) {
        ArrayList<Byte> bytes = []
        byte[] buffer = new byte[bufferLength]
        int read
        while ((read = inputStream.read(buffer)) != -1) {
            bytes.addAll(buffer[0..(read-1)].collect { Byte.valueOf(it) })
        }
        return bytes as byte[]
    }
}
