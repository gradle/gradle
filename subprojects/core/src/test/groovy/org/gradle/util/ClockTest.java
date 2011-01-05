/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.util;

import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class ClockTest {

    private static final long TEST_BASE_TIME = 641353121231L;

    private JUnit4Mockery context = new JUnit4Mockery();
    private TimeProvider timeProvider;
    private Clock clock;

    @Before
    public void setUp() {
        timeProvider = context.mock(TimeProvider.class);
        setBaseTime();
        clock = new Clock(timeProvider);
    }

    @Test public void testOnlySecondsTwoDigits() throws Exception {
        setDtMs(51243);
        assertEquals("51.243 secs", clock.getTime());
    }

    @Test public void testOnlySecondsEvenMs() {
        setDtMs(4000);
        assertEquals("4.0 secs", clock.getTime());
    }

    @Test public void testMinutesAndSeconds() {
        setDtHrsMinsSecsMillis(0, 32, 40, 322);
        assertEquals("32 mins 40.322 secs", clock.getTime());
    }

    @Test public void testHoursMinutesAndSeconds() {
        setDtHrsMinsSecsMillis(3, 2, 5, 111);
        assertEquals("3 hrs 2 mins 5.111 secs", clock.getTime());
    }

    @Test public void testHoursZeroMinutes() {
        setDtHrsMinsSecsMillis(1, 0, 32, 0);
        assertEquals("1 hrs 0 mins 32.0 secs", clock.getTime());
    }

    private void setBaseTime() {
        returnFromTimeProvider(TEST_BASE_TIME);
    }

    private void setDtMs(final long deltaT) {
        returnFromTimeProvider(TEST_BASE_TIME + deltaT);
    }

    private void setDtHrsMinsSecsMillis(int hours, int minutes, int seconds, int millis) {
        long dt = (hours * 3600 * 1000) + (minutes * 60 * 1000) + (seconds * 1000) + millis;
        returnFromTimeProvider(TEST_BASE_TIME + dt);
    }

    private void returnFromTimeProvider(final long time) {
        context.checking(new Expectations(){{
            one(timeProvider).getCurrentTime();
            will(returnValue(time));
        }});
    }
}
