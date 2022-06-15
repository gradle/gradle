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

import org.gradle.internal.hash.HashCode.ByteArrayBackedHashCode
import org.gradle.internal.hash.HashCode.HashCode128
import spock.lang.Specification

class HashCodeTest extends Specification {
    def "can parse hex string #input"() {
        def hash = HashCode.fromString(input)

        expect:
        hash.toString() == input.toLowerCase(Locale.ROOT)
        hash.toByteArray() == bytes
        hash.length() == length
        hash.hashCode() == (int) hashCode
        type.isInstance(hash)

        where:
        input                              | type                    | length | hashCode   | bytes
        "12345678"                         | ByteArrayBackedHashCode | 4      | 0x78563412 | toBytes(0x12, 0x34, 0x56, 0x78)
        "CAFEBABE"                         | ByteArrayBackedHashCode | 4      | 0xBEBAFECA | toBytes(0xCA, 0xFE, 0xBA, 0xBE)
        "abbaabba"                         | ByteArrayBackedHashCode | 4      | 0xBAABBAAB | toBytes([0xAB, 0xBA] * 2)
        "abbaabbaabba"                     | ByteArrayBackedHashCode | 6      | 0xBAABBAAB | toBytes([0xAB, 0xBA] * 3)
        "aB" * 255                         | ByteArrayBackedHashCode | 255    | 0xABABABAB | toBytes([0xAB] * 255)
        "e5b7d1919156335a9c453a4956bbe775" | HashCode128             | 16     | 0x91D1B7E5 | toBytes([0xE5, 0xB7, 0xD1, 0x91, 0x91, 0x56, 0x33, 0x5A, 0x9C, 0x45, 0x3A, 0x49, 0x56, 0xBB, 0xE7, 0x75])
    }

    def "can parse bytes: #input"() {
        def hash = HashCode.fromBytes(input)

        expect:
        hash.toString() == toString
        hash.toByteArray() == input
        hash.length() == length
        hash.hashCode() == (int) hashCode
        type.isInstance(hash)

        where:
        input                                                                                                     | type                    | length | toString                           | hashCode
        toBytes(0x12, 0x34, 0x56, 0x78)                                                                           | ByteArrayBackedHashCode | 4      | "12345678"                         | 0x78563412
        toBytes(0xCA, 0xFE, 0xBA, 0xBE)                                                                           | ByteArrayBackedHashCode | 4      | "cafebabe"                         | 0xBEBAFECA
        toBytes([0xAB, 0xBA] * 3)                                                                                 | ByteArrayBackedHashCode | 6      | "abbaabbaabba"                     | 0xBAABBAAB
        toBytes([0xAB] * 255)                                                                                     | ByteArrayBackedHashCode | 255    | "ab" * 255                         | 0xABABABAB
        toBytes([0xE5, 0xB7, 0xD1, 0x91, 0x91, 0x56, 0x33, 0x5A, 0x9C, 0x45, 0x3A, 0x49, 0x56, 0xBB, 0xE7, 0x75]) | HashCode128             | 16     | "e5b7d1919156335a9c453a4956bbe775" | 0x91D1B7E5
    }

    def "#a == #b: #equals"() {
        def hashA = HashCode.fromString(a)
        def hashB = HashCode.fromString(b)

        expect:
        (hashA == hashB) == equals
        (hashB == hashA) == equals

        where:
        a                                  | b                                  | equals
        "abcdef12"                         | "abcdef12"                         | true
        "abcdef12"                         | "abcdef1234"                       | false
        "abcdef1234"                       | "abcdef12"                         | false
        "e5b7d1919156335a9c453a4956bbe775" | "e5b7d1919156335a9c453a4956bbe775" | true
        "e5b7d1919156335a9c453a4956bbe775" | "f5b7d1919156335a9c453a4956bbe775" | false
        "e5b7d1919156335a9c453a4956bbe775" | "f5b7d191"                         | false
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
        a                                    | b                                    | expected
        "abcdef12"                           | "abcdef12"                           | 0
        "abcdef12"                           | "abcdef1234"                         | -1
        "abcdef1234"                         | "abcdef12"                           | 1
        "abcdef1234"                         | "bcdef123"                           | -1
        "bcdef123"                           | "abcdef12"                           | 1
        "e5b7d1919156335a9c453a4956bbe775"   | "e5b7d1919156335a9c453a4956bbe775"   | 0
        "e5b7d1919156335a9c453a4956bbe775"   | "f5b7d1919156335a9c453a4956bbe775"   | -1
        "e5b7d1919156335a9c453a4956bbe775"   | "f5b7d1919156335a9c453a4956bbe77512" | -1
        "e5b7d1919156335a9c453a4956bbe775"   | "f5b7d191"                           | -1
        "f5b7d1919156335a9c453a4956bbe775"   | "e5b7d1919156335a9c453a4956bbe775"   | 1
        "f5b7d1919156335a9c453a4956bbe77512" | "e5b7d1919156335a9c453a4956bbe775"   | 1
        "f5b7d191"                           | "e5b7d1919156335a9c453a4956bbe775"   | 1
    }

    def "not equals with null"() {
        expect:
        TestHashCodes.hashCodeFrom(0x12345678) != null
        null != TestHashCodes.hashCodeFrom(0x12345678)
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

    def "can create compact string representation"() {
        expect:
        Hashing.md5().hashString("").toCompactString() == "ck2u8j60r58fu0sgyxrigm3cu"
        Hashing.md5().hashString("a").toCompactString() == "r6p51cluyxfm1x21kf967yw1"
        Hashing.md5().hashString("i").toCompactString() == "7ycx034q3zbhupl01mv32dx6p"
    }

    def "can create zero-padded hex representation"() {
        expect:
        HashCode.fromString("12345678").toZeroPaddedString(8) == "12345678"
        HashCode.fromString("12345678").toZeroPaddedString(7) == "12345678"
        HashCode.fromString("12345678").toZeroPaddedString(0) == "12345678"
        HashCode.fromString("12345678").toZeroPaddedString(9) == "012345678"
        HashCode.fromString("12345678").toZeroPaddedString(16) == "0000000012345678"
        Hashing.md5().hashString("").toZeroPaddedString(40) == "00000000d41d8cd98f00b204e9800998ecf8427e"
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
