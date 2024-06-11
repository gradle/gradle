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

import spock.lang.Specification

import java.nio.CharBuffer

abstract class AbstractCodecTest extends Specification {
    def "can encode and decode raw bytes using Stream view"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.outputStream.write(Byte.MIN_VALUE)
            encoder.outputStream.write(Byte.MAX_VALUE)
            encoder.outputStream.write(-1)
            encoder.outputStream.write([1, 2, 3, 4] as byte[])
            encoder.outputStream.write([0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7] as byte[], 2, 4)
        }
        decode(bytes) { Decoder decoder ->
            def buffer = new byte[3]
            assert decoder.inputStream.read(buffer) == 3
            assert buffer == [Byte.MIN_VALUE, Byte.MAX_VALUE, -1] as byte[]
            assert decoder.inputStream.read(buffer, 1, 2) == 2
            assert buffer[1] == 1
            assert buffer[2] == 2
            assert decoder.inputStream.read() == 3
            assert decoder.inputStream.read() == 4
            assert decoder.inputStream.read() == 0xc3
            def content = decoder.inputStream.bytes
            assert content == [0xc4, 0xc5, 0xc6] as byte[]

            assert decoder.inputStream.read() == -1
            assert decoder.inputStream.read(buffer) == -1
            assert decoder.inputStream.read(buffer, 0, 1) == -1
        }
    }

    def "ignores close on InputStream"() {
        def inputStream = Mock(InputStream)

        when:
        decodeFrom(inputStream) { Decoder decoder ->
            decoder.inputStream.close()
        }

        then:
        0 * inputStream.close()
    }

    def "ignores close or flush on OutputStream"() {
        def outputStream = Mock(OutputStream)

        when:
        encodeTo(outputStream) { Encoder encoder ->
            encoder.outputStream.flush()
            encoder.outputStream.close()
        }

        then:
        0 * outputStream.close()
        0 * outputStream.flush()
    }

    def "can encode and decode raw bytes"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeByte(Byte.MIN_VALUE)
            encoder.writeByte(Byte.MAX_VALUE)
            encoder.writeByte(-1 as byte)
            encoder.writeBytes([1, 2, 3, 4] as byte[])
            encoder.writeBytes([0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7] as byte[], 2, 1)
        }
        decode(bytes) { Decoder decoder ->
            def buffer = new byte[2]
            decoder.readBytes(buffer)
            assert buffer == [Byte.MIN_VALUE, Byte.MAX_VALUE] as byte[]
            assert decoder.readByte() == -1 as byte
            decoder.readBytes(buffer, 0, 2)
            assert buffer[0] == 1
            assert buffer[1] == 2
            assert decoder.readByte() == 3
            assert decoder.readByte() == 4
            assert decoder.readByte() == 0xc3 as byte
        }
    }

    def "can encode and decode many bytes"() {
        expect:
        def bytes = encode { Encoder encoder ->
            10000.times {
                encoder.writeByte(it as byte)
            }
        }
        decode(bytes) { Decoder decoder ->
            10000.times {
                assert decoder.readByte() == it as byte
            }
        }
    }

    def "decode fails when requested number of raw bytes are not available"() {
        given:
        def bytes = encode { Encoder encoder ->
            encoder.writeBytes([0xc1, 0xc2, 0xc3] as byte[])
        }

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readBytes(new byte[4])
        }

        then:
        thrown(EOFException)

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readBytes(new byte[10], 0, 5)
        }

        then:
        thrown(EOFException)

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readBytes(new byte[3], 0, 3)
            decoder.readByte()
        }

        then:
        thrown(EOFException)
    }

    def "can skip raw bytes"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeBytes([1, 2, 3, 4, 5, 6] as byte[])
            encoder.writeBytes(new byte[4096])
            encoder.writeBytes([7, 8] as byte[])
        }
        decode(bytes) { Decoder decoder ->
            def buffer = new byte[2]
            decoder.readBytes(buffer)
            assert buffer == [1, 2] as byte[]
            decoder.skipBytes(2)
            assert decoder.readByte() == 5 as byte
            assert decoder.readByte() == 6 as byte
            decoder.skipBytes(2000)
            decoder.skipBytes(2096)
            assert decoder.readByte() == 7 as byte
            assert decoder.readByte() == 8 as byte
        }
    }

    def "can skip raw bytes using InputStream"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeBytes([1, 2, 3, 4, 5, 6] as byte[])
        }
        decode(bytes) { Decoder decoder ->
            def buffer = new byte[2]
            decoder.readBytes(buffer)
            assert buffer == [1, 2] as byte[]
            assert decoder.inputStream.skip(2) == 2
            assert decoder.readByte() == 5 as byte
            assert decoder.readByte() == 6 as byte
            assert decoder.inputStream.skip(2) == 0
        }
    }

    def "decode fails when too few bytes are available to skip"() {
        when:
        def bytes = encode { Encoder encoder ->
            encoder.writeBytes([1, 2] as byte[])
        }
        decode(bytes) { Decoder decoder ->
            decoder.skipBytes(4)
        }

        then:
        thrown(EOFException)
    }

    def "can encode and decode byte array"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeBinary([] as byte[])
            encoder.writeBinary([1, 2, 3, 4] as byte[])
            encoder.writeBinary([0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7] as byte[], 2, 3)
        }
        decode(bytes) { Decoder decoder ->
            assert decoder.readBinary() == [] as byte[]
            assert decoder.readBinary() == [1, 2, 3, 4] as byte[]
            assert decoder.readBinary() == [0xc3, 0xc4, 0xc5] as byte[]
        }
    }

    def "decode fails when byte array cannot be fully read"() {
        given:
        def bytes = truncate { Encoder encoder ->
            encoder.writeBinary([1, 2, 3, 4] as byte[])
        }

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readBinary()
        }

        then:
        thrown(EOFException)

        when:
        decode([] as byte[]) { Decoder decoder ->
            decoder.readBinary()
        }

        then:
        thrown(EOFException)
    }

    def "can encode and decode long #value"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeLong(value)
        }
        decode(bytes) { Decoder decoder ->
            assert decoder.readLong() == value
        }

        where:
        value               | _
        0                   | _
        12                  | _
        -1                  | _
        0xff                | _
        0xffdd              | _
        0xffddcc            | _
        0xffddccbb          | _
        0xffddccbbaa        | _
        0xffddccbbaa99      | _
        0xffddccbbaa9988    | _
        0x7fddccbbaa998877  | _
        -0xff               | _
        -0xffdd             | _
        -0xffddcc           | _
        -0xffddccbb         | _
        -0xffddccbbaa       | _
        -0xffddccbbaa99     | _
        -0xffddccbbaa9988   | _
        -0x7fddccbbaa998877 | _
        Long.MAX_VALUE      | _
        Long.MIN_VALUE      | _
    }

    def "decode fails when long cannot be fully read"() {
        given:
        def bytes = truncate { Encoder encoder ->
            encoder.writeLong(0xa40745f3L)
        }

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readLong()
        }

        then:
        thrown(EOFException)
    }

    def "can encode and decode a small long"() {
        expect:
        def bytesA = encode { Encoder encoder ->
            encoder.writeSmallLong(a as long)
        }
        def bytesB = encode { Encoder encoder ->
            encoder.writeSmallLong(b as long)
        }
        decode(bytesA) { Decoder decoder ->
            assert decoder.readSmallLong() == a
        }
        decode(bytesB) { Decoder decoder ->
            assert decoder.readSmallLong() == b
        }
        bytesA.length <= bytesB.length

        where:
        a                 | b
        0                 | 0x1ff
        0x2ff             | 0x1000
        0x1000            | -1
        Integer.MAX_VALUE | -1
        Long.MAX_VALUE    | -1
        Long.MAX_VALUE    | -0xc3412
        Integer.MAX_VALUE | Integer.MIN_VALUE
        Long.MAX_VALUE    | Long.MIN_VALUE
    }

    def "decode fails when small long cannot be fully read"() {
        given:
        def bytes = truncate { Encoder encoder ->
            encoder.writeSmallLong(0xa40745f)
        }

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readSmallLong()
        }

        then:
        thrown(EOFException)
    }

    def "can encode and decode int #value"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeInt(value as int)
        }
        decode(bytes) { Decoder decoder ->
            assert decoder.readInt() == value
        }

        where:
        value             | _
        0                 | _
        12                | _
        -1                | _
        0xF               | _
        0xFD              | _
        0xFDD             | _
        0xFFDD            | _
        0xFFDDCC          | _
        0x7FDDCCBB        | _
        -0xFF             | _
        Integer.MAX_VALUE | _
        Integer.MIN_VALUE | _
    }

    def "decode fails when int cannot be fully read"() {
        given:
        def bytes = truncate { Encoder encoder ->
            encoder.writeInt(0xa40745f)
        }

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readInt()
        }

        then:
        thrown(EOFException)
    }

    def "can encode and decode a small int"() {
        expect:
        def bytesA = encode { Encoder encoder ->
            encoder.writeSmallInt(a as int)
        }
        def bytesB = encode { Encoder encoder ->
            encoder.writeSmallInt(b as int)
        }
        decode(bytesA) { Decoder decoder ->
            assert decoder.readSmallInt() == a
        }
        decode(bytesB) { Decoder decoder ->
            assert decoder.readSmallInt() == b
        }
        bytesA.length <= bytesB.length

        where:
        a                 | b
        0                 | 0x1ff
        0x2ff             | 0x1000
        0x1000            | -1
        Integer.MAX_VALUE | -1
        Integer.MAX_VALUE | -0xc3412
        Integer.MAX_VALUE | Integer.MIN_VALUE
    }

    def "decode fails when small int cannot be fully read"() {
        given:
        def bytes = truncate { Encoder encoder ->
            encoder.writeSmallInt(0xa40745f)
        }

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readSmallInt()
        }

        then:
        thrown(EOFException)
    }

    def "can encode and decode a boolean"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeBoolean(true)
            encoder.writeBoolean(false)
        }
        decode(bytes) { Decoder decoder ->
            assert decoder.readBoolean()
            assert !decoder.readBoolean()
        }
    }

    def "decode fails when boolean cannot be fully read"() {
        given:
        def bytes = truncate { Encoder encoder ->
            encoder.writeBoolean(true)
        }

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readBoolean()
        }

        then:
        thrown(EOFException)
    }

    def "can encode and decode a string"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeString(value)
        }
        decode(bytes) { Decoder decoder ->
            assert decoder.readString() == value.toString()
        }

        where:
        value                            | _
        ""                               | _
        "all ascii"                      | _
        "\u0000\u0101\u3100"             | _
        "${1 + 2}"                       | _
        new StringBuilder("some string") | _
        CharBuffer.wrap("a string")      | _
        (0..1000).join("-")              | _
    }

    def "decode fails when string cannot be fully read"() {
        given:
        def bytes = truncate { Encoder encoder ->
            encoder.writeString("hi")
        }

        when:
        decode(bytes) { Decoder decoder ->
            decoder.readString()
        }

        then:
        thrown(EOFException)
    }

    def "cannot encode a null string"() {
        when:
        encode { Encoder encoder ->
            encoder.writeString(null)
        }

        then:
        thrown(IllegalArgumentException)
    }

    def "can encode and decode a nullable string"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeNullableString(value)
        }
        decode(bytes) { Decoder decoder ->
            assert decoder.readNullableString() == value?.toString()
        }

        where:
        value                            | _
        null                             | _
        ""                               | _
        "all ascii"                      | _
        "\u0000\u0101\u3100"             | _
        "${1 + 2}"                       | _
        new StringBuilder("some string") | _
    }

    abstract void encodeTo(OutputStream outputStream, Closure<Encoder> closure)

    byte[] encode(Closure<Encoder> closure) {
        def bytes = new ByteArrayOutputStream()
        encodeTo(bytes, closure)
        return bytes.toByteArray()
    }

    byte[] truncate(Closure<Encoder> closure) {
        def bytes = new ByteArrayOutputStream()
        encodeTo(bytes, closure)
        def result = bytes.toByteArray()
        if (result.length < 2) {
            return [] as byte[]
        }
        return result[0..result.length - 2] as byte[]
    }

    abstract void decodeFrom(InputStream inputStream, Closure<Decoder> closure)

    void decode(byte[] bytes, Closure<Decoder> closure) {
        decodeFrom(new ByteArrayInputStream(bytes), closure)
    }
}
