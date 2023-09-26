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

package org.gradle.internal.serialize.kryo

import org.gradle.internal.serialize.AbstractCodecTest
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder

class KryoBackedCodecTest extends AbstractCodecTest {
    @Override
    void encodeTo(OutputStream outputStream, Closure<Encoder> closure) {
        def encoder = new KryoBackedEncoder(outputStream, 10)
        closure.call(encoder)
        encoder.flush()
    }

    @Override
    void decodeFrom(InputStream inputStream, Closure<Decoder> closure) {
        def decoder = new KryoBackedDecoder(inputStream, 10)
        closure.call(decoder)
    }

    def "can encode and decode empty byte stream"() {
        when:
        def bytes = encode { Encoder encoder ->
            encoder.encodeChunked {}
            encoder.writeString("done")
        }

        then:
        decode(bytes) { Decoder decoder ->
            decoder.decodeChunked {}
            assert decoder.readString() == "done"
        }
    }

    def "can encode and skip empty byte stream"() {
        when:
        def bytes = encode { Encoder encoder ->
            encoder.encodeChunked {}
            encoder.writeString("done")
        }

        then:
        decode(bytes) { Decoder decoder ->
            decoder.skipChunked()
            assert decoder.readString() == "done"
        }
    }

    def "can encode and decode byte stream"() {
        when:
        def bytes = encode { Encoder encoder ->
            encoder.encodeChunked { Encoder nested ->
                nested.writeSmallInt(12)
                nested.writeString("chunked")
            }
            encoder.writeString("done")
        }

        then:
        decode(bytes) { Decoder decoder ->
            decoder.decodeChunked { Decoder nested ->
                assert nested.readSmallInt() == 12
                assert nested.readString() == "chunked"
            }
            assert decoder.readString() == "done"
        }
    }

    def "can encode and decode multiple byte streams"() {
        when:
        def bytes = encode { Encoder encoder ->
            encoder.encodeChunked { Encoder nested ->
                nested.writeSmallInt(1)
                nested.writeString("chunked")
            }
            encoder.encodeChunked { Encoder nested ->
            }
            encoder.encodeChunked { Encoder nested ->
                nested.writeSmallInt(2)
            }
            encoder.writeString("done")
        }

        then:
        decode(bytes) { Decoder decoder ->
            decoder.decodeChunked { Decoder nested ->
                assert nested.readSmallInt() == 1
                assert nested.readString() == "chunked"
            }
            decoder.decodeChunked {}
            decoder.decodeChunked { Decoder nested ->
                assert nested.readSmallInt() == 2
            }
            assert decoder.readString() == "done"
        }
    }

    def "can encode and skip byte stream"() {
        when:
        def bytes = encode { Encoder encoder ->
            encoder.encodeChunked { Encoder nested ->
                nested.writeSmallInt(12)
                nested.writeString("chunked")
            }
            encoder.writeString("done")
        }

        then:
        decode(bytes) { Decoder decoder ->
            decoder.skipChunked()
            assert decoder.readString() == "done"
        }
    }

    def "can query write and read positions"() {
        def outstr = new ByteArrayOutputStream()
        def encoder = new KryoBackedEncoder(outstr)

        expect:
        encoder.writePosition == 0

        when:
        encoder.writeBoolean(true)
        encoder.writeByte(12 as byte)
        encoder.writeLong(1234)

        then:
        encoder.writePosition == 10
        outstr.size() == 0

        when:
        encoder.flush()

        then:
        encoder.writePosition == 10
        outstr.size() == 10

        when:
        encoder.writeBytes(new byte[4098])

        then:
        encoder.writePosition == 4108
        outstr.size() == 4106

        when:
        encoder.close()

        then:
        encoder.writePosition == 4108
        outstr.size() == 4108

        when:
        def instr = new ByteArrayInputStream(outstr.toByteArray())
        def decoder = new KryoBackedDecoder(instr)

        then:
        instr.available() == 4108
        decoder.readPosition == 0

        when:
        decoder.readBoolean()
        decoder.readByte()
        decoder.readLong()

        then:
        instr.available() == 12 // decoder has buffered from instr
        decoder.readPosition == 10

        when:
        decoder.skipBytes(4098)

        then:
        instr.available() == 0
        decoder.readPosition == 4108
    }
}
