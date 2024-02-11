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

package org.gradle.process.internal.health.memory

import spock.lang.Specification

class MemoryAmountTest extends Specification {

    def "bytes #bytes"() {
        given:
        def amount = MemoryAmount.of(bytes)

        expect:
        amount.bytes == bytes
        amount.toString() == bytes.toString()

        where:
        bytes << [1L, Long.MAX_VALUE]
    }

    def "invalid bytes #bytes"() {
        when:
        MemoryAmount.of(bytes)

        then:
        thrown IllegalArgumentException

        where:
        bytes << [0L, -1L, Long.MIN_VALUE]
    }

    def "string notation #notation"() {
        given:
        def amount = MemoryAmount.of(notation)

        expect:
        amount.bytes == bytes
        amount.toString() == notation

        where:
        notation | bytes
        '23'     | 23L
        '23k'    | 23L * 1024
        '23K'    | 23L * 1024
        '23m'    | 23L * 1024 * 1024
        '23M'    | 23L * 1024 * 1024
        '23g'    | 23L * 1024 * 1024 * 1024
        '23G'    | 23L * 1024 * 1024 * 1024
        '23t'    | 23L * 1024 * 1024 * 1024 * 1024
        '23T'    | 23L * 1024 * 1024 * 1024 * 1024
    }

    def "invalid string notation #notation"() {
        when:
        MemoryAmount.of(notation)

        then:
        thrown IllegalArgumentException

        where:
        notation << [null, '', '5b', '5B']
    }

    def "parse string notation"() {
        expect:
        MemoryAmount.parseNotation(null) == -1
        MemoryAmount.parseNotation('') == -1
        MemoryAmount.parseNotation(' ') == -1
        MemoryAmount.parseNotation('23') == 23
    }

    def "parse invalid string notation"() {
        when:
        MemoryAmount.parseNotation('invalid')

        then:
        thrown IllegalArgumentException
    }

    def "of kilo mega giga tera bytes"() {
        expect:
        MemoryAmount.ofKiloBytes(23) == MemoryAmount.of('23k')
        MemoryAmount.ofMegaBytes(23) == MemoryAmount.of('23m')
        MemoryAmount.ofGigaBytes(23) == MemoryAmount.of('23g')
        MemoryAmount.ofTeraBytes(23) == MemoryAmount.of('23t')

    }

}
