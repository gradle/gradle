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


package org.gradle.internal.time

import spock.lang.Specification

import java.util.concurrent.TimeUnit

class DefaultEventTimerTest extends Specification {

    private static final long TEST_BASE_TIME = 641353121231L;

    private TimeSource timeProvider = Mock(TimeSource);
    private DefaultEventTimer clock;

    void setup() {
        1 * timeProvider.currentTimeMillis() >> TEST_BASE_TIME
        1 * timeProvider.nanoTime() >> TEST_BASE_TIME;
        clock = new DefaultEventTimer(timeProvider);
    }

    def testOnlySecondsTwoDigits() throws Exception {
        when:
        setDtMs(51243);

        then:
        clock.getElapsed() == "51.243 secs"
    }

    def testOnlySecondsEvenMs() {
        when:
        setDtMs(4000);

        then:
        clock.getElapsed() == "4.0 secs"
    }

    def testMinutesAndSeconds() {
        when:
        setDtHrsMinsSecsMillis(0, 32, 40, 322);

        then:
        clock.getElapsed() == "32 mins 40.322 secs"
    }

    def testHoursMinutesAndSeconds() {
        when:
        setDtHrsMinsSecsMillis(3, 2, 5, 111);

        then:
        clock.getElapsed() == "3 hrs 2 mins 5.111 secs"
    }

    def testHoursZeroMinutes() {
        when:
        setDtHrsMinsSecsMillis(1, 0, 32, 0);

        then:
        clock.getElapsed() == "1 hrs 0 mins 32.0 secs"
    }

    private void setDtMs(final long deltaT) {
        1 * timeProvider.nanoTime() >> TEST_BASE_TIME + TimeUnit.NANOSECONDS.convert(deltaT, TimeUnit.MILLISECONDS);
    }

    private void setDtHrsMinsSecsMillis(int hours, int minutes, int seconds, int millis) {
        long dt = (hours * 3600 * 1000) + (minutes * 60 * 1000) + (seconds * 1000) + millis;
        setDtMs(dt)
    }
}
