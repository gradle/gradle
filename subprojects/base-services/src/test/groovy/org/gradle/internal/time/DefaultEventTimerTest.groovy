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

    def testStartTime() throws Exception {
        expect:
        clock.startTime == TEST_BASE_TIME
    }

    def testReset() throws Exception {
        when:
        1 * timeProvider.currentTimeMillis() >> TEST_BASE_TIME + 1
        1 * timeProvider.nanoTime() >> TEST_BASE_TIME;
        clock.reset()

        then:
        clock.startTime == TEST_BASE_TIME + 1
    }

    def testElapsed() throws Exception {
        when:
        setDtMs(100)

        then:
        clock.startTime == TEST_BASE_TIME
        clock.elapsedMillis == 100
    }

    private void setDtMs(final long deltaT) {
        1 * timeProvider.nanoTime() >> TEST_BASE_TIME + TimeUnit.NANOSECONDS.convert(deltaT, TimeUnit.MILLISECONDS);
    }
}
