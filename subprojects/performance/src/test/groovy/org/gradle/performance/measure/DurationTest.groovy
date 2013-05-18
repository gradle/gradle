/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.performance.measure

import spock.lang.Specification

class DurationTest extends Specification {
    def "millisecond durations have useful string formats"() {
        expect:
        Duration.millis(millis).toString() == str
        Duration.millis(millis).format() == formatted

        where:
        millis  | str          | formatted
        0       | "0 ms"       | "0 ms"
        1       | "1 ms"       | "1 ms"
        0.123   | "0.123 ms"   | "0.123 ms"
        -12     | "-12 ms"     | "-12 ms"
        1000    | "1000 ms"    | "1 s"
        123456  | "123456 ms"  | "2.058 m"
        3607000 | "3607000 ms" | "1.002 h"
    }

    def "second durations have useful string formats"() {
        expect:
        Duration.seconds(millis).toString() == str

        where:
        millis    | str           | format
        0         | "0 s"         | "0 ms"
        1         | "1 s"         | "1 s"
        1000      | "1000 s"      | "16.667 m"
        0.123     | "0.123 s"     | "123 ms"
        0.1234567 | "0.1234567 s" | "123.457 ms"
        -12       | "-12 s"       | "-12 s"
    }

    def "can convert between units"() {
        expect:
        Duration.millis(45000) == Duration.seconds(45)
        Duration.seconds(0.98) == Duration.millis(980)
        Duration.seconds(120) == Duration.minutes(2)
        Duration.seconds(30) == Duration.minutes(0.5)
        Duration.hours(30) == Duration.millis(30 * 60 * 60 * 1000)
    }
}
