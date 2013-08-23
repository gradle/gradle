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

package org.gradle.messaging.serialize

import spock.lang.Specification

abstract class AbstractCodecTest extends Specification {
    def "can encode and decode raw bytes"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.outputStream.write(Byte.MIN_VALUE)
            encoder.outputStream.write(Byte.MAX_VALUE)
            encoder.outputStream.write(-1)
            encoder.outputStream.write([1,2,3,4] as byte[])
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
            def content = decoder.inputStream.bytes
            assert content == [4, 0xc3, 0xc4, 0xc5, 0xc6] as byte[]
        }
    }

    def "can encode and decode a long"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeLong(value)
        }
        decode(bytes) { Decoder decoder ->
            assert decoder.readLong() == value
        }

        where:
        value          | _
        0              | _
        12             | _
        -1             | _
        Long.MAX_VALUE | _
        Long.MIN_VALUE | _
    }

    abstract byte[] encode(Closure closure)

    abstract void decode(byte[] bytes, Closure closure)
}
