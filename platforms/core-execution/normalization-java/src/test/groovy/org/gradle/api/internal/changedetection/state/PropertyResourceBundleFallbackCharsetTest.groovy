/*
 * Copyright 2020 the original author or authors.
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

import java.nio.ByteBuffer
import java.nio.CharBuffer

class PropertyResourceBundleFallbackCharsetTest extends Specification {
    private final byte[] utf8bytes =  [0xc3, 0xa2, 0x61, 0x62, 0x63, 0x64] as byte[]
    private final byte[] iso8859bytes = [0xe2, 0x61, 0x62, 0x63, 0x64] as byte[]

    def charset = new PropertyResourceBundleFallbackCharset()

    def "can decode a UTF-8 stream"() {
        when:
        CharBuffer result = charset.newDecoder().decode(buffer(utf8bytes))

        then:
        result.toString() == new String(utf8bytes, "UTF-8")
        result.toString() != new String(utf8bytes, "ISO-8859-1")
    }

    def "can fallback to decode an IS0-8859-1 stream"() {
        when:
        CharBuffer result = charset.newDecoder().decode(buffer(iso8859bytes))

        then:
        result.toString() == new String(iso8859bytes, "ISO-8859-1")
        result.toString() != new String(iso8859bytes, "UTF-8")
    }

    static ByteBuffer buffer(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(bytes.length)
        byteBuffer.put(bytes)
        byteBuffer.rewind()
        return byteBuffer
    }
}
