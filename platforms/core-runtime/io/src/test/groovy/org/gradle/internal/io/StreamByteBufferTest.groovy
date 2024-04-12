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

package org.gradle.internal.io

import groovy.transform.CompileStatic
import spock.lang.Specification

import java.security.MessageDigest

class StreamByteBufferTest extends Specification {
    private static final int TESTROUNDS = 10000
    private static final String TEST_STRING = "Hello \u00f6\u00e4\u00e5\u00d6\u00c4\u00c5"
    private static final byte[] TEST_STRING_BYTES = TEST_STRING.getBytes('UTF-8')

    static byte[] testbuffer = new byte[256 * TESTROUNDS]

    @CompileStatic
    def setupSpec() {
        for (int i = 0; i < TESTROUNDS; i++) {
            for (int j = 0; j < 256; j++) {
                testbuffer[i * 256 + j] = (byte) (j & 0xff)
            }
        }
    }

    def "can convert to byte array"() {
        given:
        def byteBuffer = createTestInstance()
        expect:
        byteBuffer.readAsByteArray() == testbuffer
    }

    def "reads source InputStream fully"() {
        given:
        def byteBuffer = new StreamByteBuffer()
        def byteArrayInputStream = new ByteArrayInputStream(testbuffer)

        when:
        byteBuffer.readFully(byteArrayInputStream)

        then:
        byteBuffer.totalBytesUnread() == testbuffer.length
        byteBuffer.readAsByteArray() == testbuffer
    }

    def "can create buffer from InputStream"() {
        given:
        def byteArrayInputStream = new ByteArrayInputStream(testbuffer)

        when:
        def byteBuffer = StreamByteBuffer.of(byteArrayInputStream)

        then:
        byteBuffer.totalBytesUnread() == testbuffer.length
        byteBuffer.readAsByteArray() == testbuffer
    }

    def "reads source InputStream up to limit"() {
        given:
        def byteBuffer = new StreamByteBuffer(chunkSize)
        def byteArrayInputStream = new ByteArrayInputStream(testbuffer)
        byte[] testBufferPart = new byte[limit]
        System.arraycopy(testbuffer, 0, testBufferPart, 0, limit)

        when:
        byteBuffer.readFrom(byteArrayInputStream, limit)

        then:
        byteBuffer.totalBytesUnread() == limit
        byteBuffer.readAsByteArray() == testBufferPart

        where:
        chunkSize = 8192
        limit << [1, 8191, 8192, 8193, 8194]
    }

    def "can create buffer from InputStream and limiting size"() {
        given:
        def byteArrayInputStream = new ByteArrayInputStream(testbuffer)
        byte[] testBufferPart = new byte[limit]
        System.arraycopy(testbuffer, 0, testBufferPart, 0, limit)

        when:
        def byteBuffer = StreamByteBuffer.of(byteArrayInputStream, limit)

        then:
        byteBuffer.totalBytesUnread() == limit
        byteBuffer.readAsByteArray() == testBufferPart

        where:
        limit << [1, 8191, 8192, 8193, 8194]
    }

    def "converts to String"() {
        given:
        def byteBuffer = new StreamByteBuffer(chunkSize)
        def out = byteBuffer.getOutputStream()

        when:
        if (preUseBuffer) {
            // check the case that buffer has been used before
            out.write('HELLO'.getBytes("UTF-8"))
            byteBuffer.readAsString("UTF-8") == 'HELLO'
            byteBuffer.readAsString() == ''
            byteBuffer.readAsString() == ''
        }
        out.write(TEST_STRING_BYTES)

        then:
        byteBuffer.readAsString("UTF-8") == TEST_STRING

        where:
        // make sure that multi-byte unicode characters get split in different chunks
        [chunkSize, preUseBuffer] << [(1..(TEST_STRING_BYTES.length * 3)).toList() + [100, 1000], [false, true]].combinations()
    }

    def "empty buffer to String returns empty String"() {
        given:
        def byteBuffer = new StreamByteBuffer()

        expect:
        byteBuffer.readAsString() == ''
    }

    def "can use InputStream interface to read from buffer"() {
        given:
        def byteBuffer = createTestInstance()
        def input = byteBuffer.getInputStream()
        def bytesOut = new ByteArrayOutputStream(byteBuffer
                .totalBytesUnread())

        when:
        copy(input, bytesOut, 2048)

        then:
        bytesOut.toByteArray() == testbuffer
    }

    def "can use InputStream and OutputStream interfaces to access buffer"() {
        given:
        def streamBuf = new StreamByteBuffer(32000)
        def output = streamBuf.getOutputStream()

        when:
        output.write(1)
        output.write(2)
        output.write(3)
        output.write(255)
        output.close()

        then:
        def input = streamBuf.getInputStream()
        input.read() == 1
        input.read() == 2
        input.read() == 3
        input.read() == 255
        input.read() == -1
        input.close()
    }

    def "can write array and read one-by-one"() {
        given:
        def streamBuf = new StreamByteBuffer(32000)
        def output = streamBuf.getOutputStream()
        byte[] bytes = [(byte) 1, (byte) 2, (byte) 3] as byte[]

        when:
        output.write(bytes)
        output.close()

        then:
        def input = streamBuf.getInputStream()
        input.read() == 1
        input.read() == 2
        input.read() == 3
        input.read() == -1
        input.close()
    }

    def "smoke test read to array"(int streamByteBufferSize, int testBufferSize) {
        expect:
        def streamBuf = new StreamByteBuffer(streamByteBufferSize)
        def output = streamBuf.getOutputStream()
        for (int i = 0; i < testBufferSize; i++) {
            output.write(i % (Byte.MAX_VALUE * 2))
        }
        output.close()

        byte[] buffer = new byte[testBufferSize]
        def input = streamBuf.getInputStream()
        assert testBufferSize == input.available()
        int readBytes = input.read(buffer)
        assert readBytes == testBufferSize
        for (int i = 0; i < buffer.length; i++) {
            assert (byte) (i % (Byte.MAX_VALUE * 2)) == buffer[i]
        }
        assert input.read() == -1
        input.close()

        where:
        [streamByteBufferSize, testBufferSize] << [[10000, 10000], [1, 10000], [2, 10000], [10000, 2], [10000, 1]]
    }

    def "smoke test read one by one"(int streamByteBufferSize, int testBufferSize) {
        expect:
        def streamBuf = new StreamByteBuffer(streamByteBufferSize)
        def output = streamBuf.getOutputStream()
        for (int i = 0; i < testBufferSize; i++) {
            output.write(i % (Byte.MAX_VALUE * 2))
        }
        output.close()

        def input = streamBuf.getInputStream()
        assert input.available() == testBufferSize
        for (int i = 0; i < testBufferSize; i++) {
            assert input.read() == i % (Byte.MAX_VALUE * 2)
        }
        assert input.read() == -1
        input.close()

        where:
        [streamByteBufferSize, testBufferSize] << [[10000, 10000], [1, 10000], [2, 10000], [10000, 2], [10000, 1]]
    }

    def "can write buffer to OutputStream"() {
        given:
        def byteBuffer = createTestInstance()
        def bytesOut = new ByteArrayOutputStream(byteBuffer.totalBytesUnread())

        when:
        byteBuffer.writeTo(bytesOut)

        then:
        bytesOut.toByteArray() == testbuffer
    }

    def "can copy buffer to OutputStream one-by-one"() {
        given:
        def byteBuffer = createTestInstance()
        def input = byteBuffer.getInputStream()
        def bytesOut = new ByteArrayOutputStream(byteBuffer.totalBytesUnread())

        when:
        copyOneByOne(input, bytesOut)

        then:
        bytesOut.toByteArray() == testbuffer
    }

    private static int copy(InputStream input, OutputStream output, int bufSize) throws IOException {
        byte[] buffer = new byte[bufSize]
        int count = 0
        int n = 0
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n)
            count += n
        }
        return count
    }

    private static int copyOneByOne(InputStream input, OutputStream output) throws IOException {
        int count = 0
        int b
        while (-1 != (b = input.read())) {
            output.write(b)
            count++
        }
        return count
    }

    StreamByteBuffer createTestInstance() throws IOException {
        StreamByteBuffer byteBuffer = new StreamByteBuffer()
        OutputStream output = byteBuffer.getOutputStream()
        copyAllFromTestBuffer(output, 27)
        return byteBuffer
    }

    private void copyAllFromTestBuffer(OutputStream output, int partsize) throws IOException {
        int position = 0
        int bytesLeft = testbuffer.length
        while (bytesLeft > 0) {
            output.write(testbuffer, position, partsize)
            position += partsize
            bytesLeft -= partsize
            if (bytesLeft < partsize) {
                partsize = bytesLeft
            }
        }
    }

    def "returns chunk size in range"() {
        given:
        def defaultChunkSize = StreamByteBuffer.DEFAULT_CHUNK_SIZE
        def maxChunkSize = StreamByteBuffer.MAX_CHUNK_SIZE
        expect:
        StreamByteBuffer.chunkSizeInDefaultRange(1) == defaultChunkSize
        StreamByteBuffer.chunkSizeInDefaultRange(0) == defaultChunkSize
        StreamByteBuffer.chunkSizeInDefaultRange(-1) == defaultChunkSize
        StreamByteBuffer.chunkSizeInDefaultRange(defaultChunkSize + 1) == defaultChunkSize + 1
        StreamByteBuffer.chunkSizeInDefaultRange(defaultChunkSize) == defaultChunkSize
        StreamByteBuffer.chunkSizeInDefaultRange(defaultChunkSize - 1) == defaultChunkSize
        StreamByteBuffer.chunkSizeInDefaultRange(maxChunkSize) == maxChunkSize
        StreamByteBuffer.chunkSizeInDefaultRange(maxChunkSize - 1) == maxChunkSize - 1
        StreamByteBuffer.chunkSizeInDefaultRange(maxChunkSize + 1) == maxChunkSize
        StreamByteBuffer.chunkSizeInDefaultRange(2 * maxChunkSize) == maxChunkSize
    }

    def "creates new instance with chunk size in range"() {
        when:
        def buffer = StreamByteBuffer.createWithChunkSizeInDefaultRange(1)
        then:
        buffer.chunkSize == StreamByteBuffer.DEFAULT_CHUNK_SIZE
    }

    def "reads available unicode characters in buffer and pushes in-progress ones back"() {
        given:
        def byteBuffer = new StreamByteBuffer(chunkSize)
        def out = byteBuffer.getOutputStream()
        def stringBuilder = new StringBuilder()

        when:
        out.write("HELLO".bytes)
        then:
        byteBuffer.readAsString() == "HELLO"
        byteBuffer.readAsString() == ""
        byteBuffer.readAsString() == ""

        when:
        for (int i = 0; i < TEST_STRING_BYTES.length; i++) {
            out.write(TEST_STRING_BYTES[i])
            stringBuilder.append(byteBuffer.readAsString('UTF-8'))
            if (readTwice) {
                // make sure 2nd readAsString handles properly multi-byte boundary
                stringBuilder.append(byteBuffer.readAsString('UTF-8'))
            }
        }

        then:
        stringBuilder.toString() == TEST_STRING

        where:
        // make sure that multi-byte unicode characters get split in different chunks
        [chunkSize, readTwice] << [(1..(TEST_STRING_BYTES.length * 3)).toList() + [100, 1000], [false, true]].combinations()
    }

    def "calculates available characters when reading and writing"() {
        given:
        def byteBuffer = new StreamByteBuffer(8)
        def out = byteBuffer.outputStream

        when:
        out.write("1234567890123".bytes)
        then:
        byteBuffer.totalBytesUnread() == 13

        when:
        byteBuffer.readAsString()
        then:
        byteBuffer.totalBytesUnread() == 0

        when:
        out.write("4567890123456".bytes)
        then:
        byteBuffer.totalBytesUnread() == 13

        when:
        byteBuffer.readAsString()
        then:
        byteBuffer.totalBytesUnread() == 0

        when:
        out.write("789".bytes)
        then:
        byteBuffer.totalBytesUnread() == 3

        when:
        byteBuffer.readAsString()
        then:
        byteBuffer.totalBytesUnread() == 0

        when:
        byteBuffer.readAsString()
        then:
        byteBuffer.totalBytesUnread() == 0
    }

    def "can read buffer as list of byte arrays"() {
        given:
        def byteBuffer = createTestInstance()
        def digestTestBuffer = MessageDigest.getInstance("MD5")
        digestTestBuffer.update(testbuffer, 0, testbuffer.length)
        def digestByteArrayList = MessageDigest.getInstance("MD5")
        when:
        def byteArrayList = byteBuffer.readAsListOfByteArrays()
        byteArrayList.each { digestByteArrayList.update(it) }
        then:
        byteArrayList.sum { it.size() } == testbuffer.length
        digestByteArrayList.digest() == digestTestBuffer.digest()
    }

    def "can create buffer instance from list of byte arrays"() {
        given:
        def byteArrayList = [[(byte) 1, (byte) 2, (byte) 3] as byte[], [(byte) 4] as byte[], [(byte) 5, (byte) 6] as byte[]]
        expect:
        StreamByteBuffer.of(byteArrayList).readAsByteArray() == (byteArrayList.flatten() as byte[])
    }
}
