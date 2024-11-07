/*
 * Copyright 2024 the original author or authors.
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

class TimestampTest extends Specification {
    def "can create timestamp with millisecond precision"() {
        def ts = Timestamp.ofMillis(millis)

        expect:
        ts.timeMs == millis
        ts.nanosOfMillis == 0
        where:
        millis      || _
        1           || _
        1_000       || _
        123_000     || _
        123_001     || _
        123_000_999 || _
    }

    def "can create timestamp with nanoseconds precision"() {
        def ts = Timestamp.ofMillis(millis, nanoAdjustment)

        expect:
        ts.timeMs == millis
        ts.nanosOfMillis == nanoAdjustment
        where:
        millis | nanoAdjustment
        0      | 0
        1_000  | 0
        1_000  | 1
        1_000  | 999_999
    }

    def "timestamp equivalence takes nanos into account"() {
        def ts1 = Timestamp.ofMillis(100, 100)
        def ts2 = Timestamp.ofMillis(100, 101)

        expect:
        ts1 != ts2
    }
}
