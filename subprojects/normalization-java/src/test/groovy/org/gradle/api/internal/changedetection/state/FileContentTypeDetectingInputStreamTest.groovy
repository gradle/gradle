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

import java.nio.charset.Charset

class FileContentTypeDetectingInputStreamTest extends Specification {
    @Unroll
    def "can detect #expectedType files with #description"() {
        def inputStream = inputStream(content)

        when:
        def contentRead = readAllBytesWithRead(inputStream)

        then:
        contentRead == content

        and:
        inputStream.contentType == expectedType

        when:
        inputStream.reset()

        then:
        inputStream.contentType == FileContentType.TEXT

        when:
        contentRead = readAllBytesWithReadBuffer(inputStream)

        then:
        contentRead == content

        and:
        inputStream.contentType == expectedType

        where:
        expectedType           | description                   | content
        FileContentType.TEXT   | "new lines"               | "this is\na text file\n".bytes
        FileContentType.TEXT   | "new lines with CR-LF"    | "this is\r\na text file\r\n".bytes
        FileContentType.TEXT   | "no new lines"            | "No new lines\tin this file".bytes
        FileContentType.TEXT   | "utf8 content"            | "here's some UTF8 content: €ЇΩ".getBytes(Charset.forName("UTF-8"))
        FileContentType.BINARY | "png content"             | [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a] as byte[]
        FileContentType.BINARY | "jpg content"             | [0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0xff, 0xda] as byte[]
        FileContentType.BINARY | "java class file content" | [0xca, 0xfe, 0xba, 0xbe, 0x00, 0x00, 0x00, 0x37, 0x0a, 0x00] as byte[]
    }

    def "delegate is called when operating on input stream"() {
        def delegate = Mock(InputStream)
        def inputStream = new FileContentTypeDetectingInputStream(delegate)
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
        1 * delegate.read(buffer, 0, 1)

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

    static FileContentTypeDetectingInputStream inputStream(byte[] bytes) {
        return new FileContentTypeDetectingInputStream(new ByteArrayInputStream(bytes))
    }

    static byte[] readAllBytesWithRead(InputStream inputStream) {
        ArrayList<Byte> bytes = []
        int b
        while ((b = inputStream.read()) != -1) {
            bytes.add(Byte.valueOf((byte)b))
        }
        return bytes as byte[]
    }

    static byte[] readAllBytesWithReadBuffer(InputStream inputStream) {
        ArrayList<Byte> bytes = []
        byte[] buffer = new byte[8]
        int read
        while ((read = inputStream.read(buffer)) != -1) {
            bytes.addAll(buffer[0..(read-1)].collect { Byte.valueOf(it) })
        }
        return bytes as byte[]
    }
}
