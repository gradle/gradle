/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.util

import spock.lang.Specification

import static org.gradle.internal.util.NumberUtil.formatBytes
import static org.gradle.internal.util.NumberUtil.percentOf

class NumberUtilTest extends Specification {

    def "knows percentage"() {
        expect:
        percentOf(0, 100) == 0
        percentOf(1, 100) == 1
        percentOf(99, 100) == 99
        percentOf(100, 100) == 100
        percentOf(101, 100) == 101
        percentOf(50, 200) == 25
        percentOf(50, 200) == 25
        percentOf(17, 301) == 5
        percentOf(50, 0) == 0
    }

    def "percentage does not allow negative values"() {
        when:
        percentOf(50, -10) == 0

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Unable to calculate percentage: 50 of -10. All inputs must be >= 0"

        when:
        percentOf(-1, 100) == 0
        then:
        ex = thrown(IllegalArgumentException)
        ex.message == "Unable to calculate percentage: -1 of 100. All inputs must be >= 0"
    }

    def "formats bytes (#bytes -> #humanReadableString)"(long bytes, String humanReadableString) {
        expect:
        formatBytes(bytes) == humanReadableString
        formatBytes(-bytes) == "-$humanReadableString"

        where:
        bytes               | humanReadableString
        1                   | "1 B"
        999                 | "999 B"
        1000                | "1000 B"
        1001                | "1001 B"
        1501                | "1.4 KiB"
        1999                | "1.9 KiB"
        1000000             | "976.5 KiB"
        1000000000          | "953.6 MiB"
        1000000000000       | "931.3 GiB"
        1000000000000000    | "909.4 TiB"
        1000000000000000000 | "888.1 PiB"

        100L * 1000**1      | "97.6 KiB" // https://www.ibm.com/support/knowledgecenter/SSQRB8/com.ibm.spectrum.si.doc/fqz0_r_units_measurement_data.html#fqz0_r_data_storage_values__percentage_diff_between_decimal_binary
        100L * 1000**2      | "95.3 MiB"
        100L * 1000**3      | "93.1 GiB"
        100L * 1000**4      | "90.9 TiB"
        100L * 1000**5      | "88.8 PiB"
    }

    def "converts to 1024 based human readable format (#bytes -> #humanReadableString)"() {
        expect:
        formatBytes(bytes) == humanReadableString

        where:
        bytes | humanReadableString
        0     | '0 B'
        null  | 'unknown size'
    }

    def "converts 2^10 based human readable format (±#bytes -> ±#humanReadableString)"(long bytes, String humanReadableString) {
        expect:
        formatBytes(bytes) == humanReadableString
        formatBytes(-bytes) == "-$humanReadableString"

        where:
        bytes             | humanReadableString
        1                 | '1 B'
        10                | '10 B'
        11                | '11 B'
        111               | '111 B'
        512               | '512 B'
        1010              | '1010 B'
        1025              | '1 KiB'
        1126              | '1 KiB'
        1127              | '1.1 KiB'

        1024L**1          | '1 KiB'
        1024L**1 + 1      | '1 KiB'
        1024L**1 * 987    | '987 KiB'
        1024L**2 - 1      | '1023.9 KiB'

        1024L**2          | '1 MiB'
        1024L**2 + 1      | '1 MiB'
        1024L**2 * 654    | '654 MiB'
        1024L**3 - 1      | '1023.9 MiB'

        1024L**3          | '1 GiB'
        1024L**3 + 1      | '1 GiB'
        1024L**3 * 321    | '321 GiB'
        1024L**4 - 1      | '1023.9 GiB'

        1024L**4          | '1 TiB'
        1024L**4 + 1      | '1 TiB'
        1024L**4 * 21     | '21 TiB'
        1024L**5 - 1      | '1023.9 TiB'

        1024L**5          | '1 PiB'
        1024L**5 + 1      | '1 PiB'
        1024L**5 * 5      | '5 PiB'
        1024L**6 - 1      | '1023.9 PiB'

        1024L**6          | '1 EiB'
        1024L**6 + 1      | '1 EiB'

        Integer.MAX_VALUE | '1.9 GiB'
        Long.MAX_VALUE    | '7.9 EiB'
    }

    def "knows ordinal"() {
        expect:
        ordinal == NumberUtil.ordinal(input)

        where:
        input | ordinal
        0     | "0th"
        1     | "1st"
        2     | "2nd"
        3     | "3rd"
        4     | "4th"
        10    | "10th"
        11    | "11th"
        12    | "12th"
        13    | "13th"
        14    | "14th"
        20    | "20th"
        21    | "21st"
        22    | "22nd"
        23    | "23rd"
        24    | "24th"
        100   | "100th"
        1001  | "1001st"
        10012 | "10012th"
        10013 | "10013th"
        10014 | "10014th"
    }
}

