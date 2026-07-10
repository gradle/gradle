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

package org.gradle.internal.serialize.kryo

import spock.lang.Specification

class KryoBackedDecoderTest extends Specification {
    def "can read from stream and then restart to use another stream"() {
        def input1 = encoded("string 1")
        def input2 = encoded("string 2")

        given:
        def decoder = new KryoBackedDecoder(input1)
        decoder.readString()
        decoder.restart(input2)

        expect:
        decoder.readPosition == 0
        decoder.readString() == "string 2"
        decoder.readInt() == 12
    }

    InputStream encoded(String text) {
        def stream = new ByteArrayOutputStream()
        def encoder = new KryoBackedEncoder(stream)
        encoder.writeString(text)
        encoder.writeInt(12)
        encoder.flush()
        return new ByteArrayInputStream(stream.toByteArray())
    }
}
