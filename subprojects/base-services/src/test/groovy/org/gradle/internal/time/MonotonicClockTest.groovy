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

import java.util.concurrent.TimeUnit

class MonotonicClockTest extends Specification {

    private static final long START_MILLIS = 641353121231L
    private static final long START_NANOS = 222222222222222222L

    private MonotonicClock.TimeSource timeSource = Mock(MonotonicClock.TimeSource) {
        1 * currentTimeMillis() >> START_MILLIS
        1 * nanoTime() >> START_NANOS
    }

    private Clock clock = new MonotonicClock(timeSource)

    def "prevents time from going backwards"() {
        when:
        setDtMs 0

        then:
        clock.currentTime == START_MILLIS + 0

        when:
        setDtMs 10

        then:
        clock.currentTime == START_MILLIS + 10

        when:
        setDtMs 8

        then:
        clock.currentTime == START_MILLIS + 10

        when:
        setDtMs 10

        then:
        clock.currentTime == START_MILLIS + 10

        when:
        setDtMs 15

        then:
        clock.currentTime == START_MILLIS + 15
    }

    def "provides current time based on nanoTime delta"() {
        when:
        setDtMs(delta)

        then:
        clock.currentTime == START_MILLIS + delta

        where:
        delta << [0, 100, -100]
    }

    private void setDtMs(final long deltaT) {
        1 * timeSource.nanoTime() >> START_NANOS + TimeUnit.MILLISECONDS.toNanos(deltaT)
    }
}
