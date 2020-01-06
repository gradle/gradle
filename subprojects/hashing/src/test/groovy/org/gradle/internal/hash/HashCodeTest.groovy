/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.hash

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class HashCodeTest extends Specification {
    def "can parse hex string #input"() {
        def hash = HashCode.fromString(input)

        expect:
        hash.toString() == toString
        hash.toByteArray() == bytes
        hash.length() == length
        hash.hashCode() == (int) hashCode

        where:
        input          | length | toString       | hashCode   | bytes
        "12345678"     | 4      | "12345678"     | 0x78563412 | toBytes(0x12, 0x34, 0x56, 0x78)
        "CAFEBABE"     | 4      | "cafebabe"     | 0xBEBAFECA | toBytes(0xCA, 0xFE, 0xBA, 0xBE)
        "abbaabba"     | 4      | "abbaabba"     | 0xBAABBAAB | toBytes([0xAB, 0xBA] * 2)
        "abbaabbaabba" | 6      | "abbaabbaabba" | 0xBAABBAAB | toBytes([0xAB, 0xBA] * 3)
        "aB" * 255     | 255    | "ab" * 255     | 0xABABABAB | toBytes([0xAB] * 255)
    }

    def "can parse int: #input"() {
        def hash = HashCode.fromInt((int) input)

        expect:
        hash.toString() == toString
        hash.toByteArray() == bytes
        hash.length() == length
        hash.hashCode() == (int) hashCode

        where:
        input          | length | toString       | hashCode   | bytes
        0x12345678     | 4      | "12345678"     | 0x78563412 | toBytes(0x12, 0x34, 0x56, 0x78)
        0xCAFEBABE     | 4      | "cafebabe"     | 0xBEBAFECA | toBytes(0xCA, 0xFE, 0xBA, 0xBE)
        0xabbaabba     | 4      | "abbaabba"     | 0xBAABBAAB | toBytes([0xAB, 0xBA] * 2)
    }

    def "can parse bytes: #input"() {
        def hash = HashCode.fromBytes(input)

        expect:
        hash.toString() == toString
        hash.toByteArray() == bytes
        hash.length() == length
        hash.hashCode() == (int) hashCode

        where:
        input                           | length | toString       | hashCode   | bytes
        toBytes(0x12, 0x34, 0x56, 0x78) | 4      | "12345678"     | 0x78563412 | toBytes(0x12, 0x34, 0x56, 0x78)
        toBytes(0xCA, 0xFE, 0xBA, 0xBE) | 4      | "cafebabe"     | 0xBEBAFECA | toBytes(0xCA, 0xFE, 0xBA, 0xBE)
        toBytes([0xAB, 0xBA] * 3)       | 6      | "abbaabbaabba" | 0xBAABBAAB | toBytes([0xAB, 0xBA] * 3)
        toBytes([0xAB] * 255)           | 255    | "ab" * 255     | 0xABABABAB | toBytes([0xAB] * 255)
    }

    def "#a == #b: #equals"() {
        def hashA = HashCode.fromString(a)
        def hashB = HashCode.fromString(b)

        expect:
        (hashA == hashB) == equals
        (hashB == hashA) == equals

        where:
        a            | b            | equals
        "abcdef12"   | "abcdef12"   | true
        "abcdef12"   | "abcdef1234" | false
        "abcdef1234" | "abcdef12"   | false
    }

    def "#a <=> #b: #expected"() {
        def hashA = HashCode.fromString(a)
        def hashB = HashCode.fromString(b)
        def compareAB = hashA <=> hashB
        def compareBA = hashB <=> hashA

        expect:
        Math.signum(compareAB) == expected
        Math.signum(compareBA) == -expected

        where:
        a            | b            | expected
        "abcdef12"   | "abcdef12"   | 0
        "abcdef12"   | "abcdef1234" | -1
        "abcdef1234" | "abcdef12"   | 1
        "abcdef1234" | "bcdef123"   | -1
        "bcdef123"   | "abcdef12"   | 1
    }

    def "not equals with null"() {
        expect:
        HashCode.fromInt(0x12345678) != null
        null != HashCode.fromInt(0x12345678)
    }

    def "won't parse string with odd length"() {
        when:
        HashCode.fromString("a" * 9)

        then:
        thrown Exception
    }

    def "won't parse string with non-hex chars: #illegal"() {
        when:
        HashCode.fromString(illegal)

        then:
        thrown Exception

        where:
        illegal << ["abcdefgh", "-1235689", "0x123456"]
    }

    def "won't parse too short string: #length"() {
        when:
        HashCode.fromString("a" * length)

        then:
        thrown Exception

        where:
        length << [0, 2, 4, 6]
    }

    def "won't parse too long string"() {
        when:
        HashCode.fromString("a" * 512)

        then:
        thrown Exception
    }

    def "won't parse too short bytes: #length"() {
        when:
        HashCode.fromBytes(toBytes([0x12] * length))

        then:
        thrown Exception

        where:
        length << [0, 1, 2, 3]
    }

    def "won't parse too long bytes"() {
        when:
        HashCode.fromBytes(toBytes([0x12] * 256))

        then:
        thrown Exception
    }

    private static byte[] toBytes(int ... elements) {
        toBytes(elements as List<Integer>)
    }

    private static byte[] toBytes(Iterable<Integer> elements) {
        byte[] result = new byte[elements.size()]
        for (int i = 0; i < elements.size(); i++) {
            result[i] = (byte) elements[i]
        }
        return result
    }
}
