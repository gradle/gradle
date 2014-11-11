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
        when: percentOf(50, -10) == 0
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Unable to calculate percentage: 50 of -10. All inputs must be >= 0"

        when: percentOf(-1, 100) == 0
        then:
        ex = thrown(IllegalArgumentException)
        ex.message == "Unable to calculate percentage: -1 of 100. All inputs must be >= 0"
    }

    def "formats bytes"() {
        expect:
        formatBytes(-1) == "-1 B"
        formatBytes(0) == "0 B"
        formatBytes(1) == "1 B"
        formatBytes(999) == "999 B"
        formatBytes(1000) == String.format("%.1f kB", 1.0)
        formatBytes(1001) == String.format("%.1f kB", 1.0)
        formatBytes(1501) == String.format("%.1f kB", 1.5)
        formatBytes(1999) == String.format("%.1f kB", 2.0)
        formatBytes(-1999) == String.format("%.1f kB", -2.0)
        formatBytes(1000000) == String.format("%.1f MB", 1.0)
        formatBytes(1000000000) == String.format("%.1f GB", 1.0)
        formatBytes(1000000000000) == String.format("%.1f TB", 1.0)
        formatBytes(1000000000000000) == String.format("%.1f PB", 1.0)
        formatBytes(1000000000000000000) == String.format("%.1f EB", 1.0)
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

