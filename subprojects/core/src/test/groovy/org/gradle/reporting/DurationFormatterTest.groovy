/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.reporting

import spock.lang.Specification

class DurationFormatterTest extends Specification {
    final DurationFormatter formatter = new DurationFormatter()

    def formatsShortDurations() {
        expect:
        formatter.format(0) == '0s'
        formatter.format(7) == '0.007s'
        formatter.format(1200) == '1.200s'
        formatter.format(59202) == '59.202s'
    }

    def formatsLongDuration() {
        expect:
        formatter.format(60 * 1000) == '1m0.00s'
        formatter.format(60 * 1000 + 12 * 1000 + 310) == '1m12.31s'
        formatter.format(23 * 60 * 1000 + 12 * 1000 + 310) == '23m12.31s'
        formatter.format(23 * 60 * 1000 + 310) == '23m0.31s'

        and:
        formatter.format(60 * 60 * 1000) == '1h0m0.00s'
        formatter.format(60 * 60 * 1000 + 20) == '1h0m0.02s'

        and:
        formatter.format(24 * 60 * 60 * 1000) == '1d0h0m0.00s'
        formatter.format(24 * 60 * 60 * 1000 + 23 * 1000) == '1d0h0m23.00s'
    }

    def roundsMillisWhenDurationIsGreaterThanOneMinute() {
        expect:
        formatter.format(60 * 1000 + 12 * 1000 + 300) == '1m12.30s'
        formatter.format(60 * 1000 + 12 * 1000 + 301) == '1m12.30s'
        formatter.format(60 * 1000 + 12 * 1000 + 305) == '1m12.31s'
        formatter.format(60 * 1000 + 12 * 1000 + 309) == '1m12.31s'
    }
}
