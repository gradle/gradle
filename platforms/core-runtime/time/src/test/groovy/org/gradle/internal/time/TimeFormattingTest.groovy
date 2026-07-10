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

package org.gradle.internal.time

import spock.lang.Specification

class TimeFormattingTest extends Specification {

    def formatsShortDurations() {
        expect:
        TimeFormatting.formatDurationVeryTerse(0) == '0s'
        TimeFormatting.formatDurationVeryTerse(7) == '0.007s'
        TimeFormatting.formatDurationVeryTerse(1200) == '1.200s'
        TimeFormatting.formatDurationVeryTerse(59202) == '59.202s'
    }

    def formatsLongDuration() {
        expect:
        TimeFormatting.formatDurationVeryTerse(60 * 1000) == '1m0.00s'
        TimeFormatting.formatDurationVeryTerse(60 * 1000 + 12 * 1000 + 310) == '1m12.31s'
        TimeFormatting.formatDurationVeryTerse(23 * 60 * 1000 + 12 * 1000 + 310) == '23m12.31s'
        TimeFormatting.formatDurationVeryTerse(23 * 60 * 1000 + 310) == '23m0.31s'

        and:
        TimeFormatting.formatDurationVeryTerse(60 * 60 * 1000) == '1h0m0.00s'
        TimeFormatting.formatDurationVeryTerse(60 * 60 * 1000 + 20) == '1h0m0.02s'

        and:
        TimeFormatting.formatDurationVeryTerse(24 * 60 * 60 * 1000) == '1d0h0m0.00s'
        TimeFormatting.formatDurationVeryTerse(24 * 60 * 60 * 1000 + 23 * 1000) == '1d0h0m23.00s'
    }

    def formatsNegativeDurations() {
        expect:
        TimeFormatting.formatDurationVeryTerse(-1200) == '-1.200s'
        TimeFormatting.formatDurationVeryTerse(-60 * 1000) == '-1m0.00s'
        TimeFormatting.formatDurationVeryTerse(-60 * 60 * 1000) == '-1h0m0.00s'
        TimeFormatting.formatDurationVeryTerse(-(23 * 60 * 1000 + 12 * 1000 + 310)) == '-23m12.31s'
    }

    def roundsMillisWhenDurationIsGreaterThanOneMinute() {
        expect:
        TimeFormatting.formatDurationVeryTerse(60 * 1000 + 12 * 1000 + 300) == '1m12.30s'
        TimeFormatting.formatDurationVeryTerse(60 * 1000 + 12 * 1000 + 301) == '1m12.30s'
        TimeFormatting.formatDurationVeryTerse(60 * 1000 + 12 * 1000 + 305) == '1m12.31s'
        TimeFormatting.formatDurationVeryTerse(60 * 1000 + 12 * 1000 + 309) == '1m12.31s'
    }

    def "shows #output when elapsed time is greater or equals than #lowerBoundInclusive but lower than #upperBoundExclusive"() {
        when:
        def result = TimeFormatting.formatDurationTerse(input)

        then:
        result == output

        where:
        lowerBoundInclusive | upperBoundExclusive | input                               | output
        "None"              | "1 second"          | seconds(0.421345)                   | "421ms"
        "1 second"          | "10 seconds"        | seconds(4.21345)                    | "4s"
        "10 seconds"        | "1 minute"          | seconds(42.1234)                    | "42s"
        "10 seconds"        | "1 minute"          | seconds(60)                         | "1m"
        "1 minute"          | "10 minutes"        | seconds(61)                         | "1m 1s"
        "1 minute"          | "10 minutes"        | minutes(1)                          | "1m"
        "1 minute"          | "10 minutes"        | minutes(4.21234)                    | "4m 12s"
        "10 minutes"        | "1 hour"            | minutes(42.1234)                    | "42m 7s"
        "10 minutes"        | "1 hour"            | minutes(60)                         | "1h"
        "1 hour"            | "10 hours"          | minutes(61)                         | "1h 1m"
        "1 hour"            | "10 hours"          | hours(1)                            | "1h"
        "1 hour"            | "10 hours"          | hours(4.2123456)                    | "4h 12m 44s"
        "10 hours"          | "100 hours"         | hours(42.123456)                    | "42h 7m 24s"
        "100 hours"         | "None"              | hours(421.23456)                    | "421h 14m 4s"
        "1 hour"            | "10 hours"          | hours(1) + minutes(1)               | "1h 1m"
        "1 hour"            | "10 hours"          | hours(1) + seconds(1)               | "1h 1s"
        "1 hour"            | "10 hours"          | hours(1) + minutes(1) + seconds(1)  | "1h 1m 1s"
    }

    private static long hours(double value) {
        return minutes(value * 60.0)
    }

    private static long minutes(double value) {
        return seconds(value * 60.0)
    }

    private static long seconds(double value) {
        return value * 1000.0
    }

}
