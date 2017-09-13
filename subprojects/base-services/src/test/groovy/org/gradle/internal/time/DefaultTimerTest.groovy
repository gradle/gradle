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

class DefaultTimerTest extends Specification {

    def timeSource = Mock(TimeSource) {
        1 * nanoTime() >> 0
    }

    def timer = new DefaultTimer(timeSource)

    def testOnlySecondsTwoDigits() throws Exception {
        when:
        setTime(51243)

        then:
        timer.getElapsed() == "51.243 secs"
    }

    def testOnlySecondsEvenMs() {
        when:
        setTime(4000)

        then:
        timer.getElapsed() == "4.0 secs"
    }

    def testMinutesAndSeconds() {
        when:
        setTime(0, 32, 40, 322)

        then:
        timer.getElapsed() == "32 mins 40.322 secs"
    }

    def testHoursMinutesAndSeconds() {
        when:
        setTime(3, 2, 5, 111)

        then:
        timer.getElapsed() == "3 hrs 2 mins 5.111 secs"
    }

    def testHoursZeroMinutes() {
        when:
        setTime(1, 0, 32, 0)

        then:
        timer.getElapsed() == "1 hrs 0 mins 32.0 secs"
    }

    def testReset() throws Exception {
        when:
        setTime(100)
        timer.reset()
        setTime(200)

        then:
        timer.elapsedMillis == 100
    }

    def testElapsed() throws Exception {
        when:
        setTime(100)

        then:
        timer.elapsedMillis == 100
    }

    private void setTime(long timestamp) {
        1 * timeSource.nanoTime() >> TimeUnit.MILLISECONDS.toNanos(timestamp)
    }

    private void setTime(int hours, int minutes, int seconds, int millis) {
        long dt = (hours * 3600 * 1000) + (minutes * 60 * 1000) + (seconds * 1000) + millis
        setTime(dt)
    }
}
