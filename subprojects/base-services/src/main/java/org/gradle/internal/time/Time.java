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

package org.gradle.internal.time;

import java.util.concurrent.TimeUnit;

/**
 * Instruments for observing time.
 */
public abstract class Time {

    private static final Clock SYSTEM = new Clock() {
        @Override
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }
    };

    /**
     * A clock that provides the time based on the system wall clock.
     *
     * This is identical to use of {@link System#currentTimeMillis()}.
     * The time provided is susceptible to time adjustments.
     * It should not be used for measuring short durations or for assigning
     * ordering timestamps as it may jump backwards or forwards.
     *
     * Use {@link #startTimer()} for measuring durations instead.
     */
    public static Clock systemWallClock() {
        return SYSTEM;
    }

    /**
     * A clock that is based on elapsed time.
     *
     * This clock differs from the system wall clock in that it determines the current time
     * based on the elapsed “CPU time” since construction.
     *
     * This rate of time advancement is determined by {@link System#nanoTime()}.
     * This may, depending on the hardware, move at a slightly different rate than the system wall clock.
     * It has been observed that this clock may drift from the system wall clock by a few milliseconds within 10 minutes.
     *
     * This clock may also drift from the system wall clock due to the system wall clock adjustments (e.g. NTP adjustments),
     * or if the machine goes to sleep. In such a case, this clock will stop while the system wall clock marches on.
     *
     * Timestamps are guaranteed to be monotonic.
     */
    public static Clock elapsedTimeClock() {
        return new MonotonicElapsedTimeClock(TimeSource.SYSTEM);
    }

    public static Timer startTimer() {
        return new DefaultTimer(TimeSource.SYSTEM);
    }

    public static CountdownTimer startCountdownTimer(long timeoutMillis) {
        return new DefaultCountdownTimer(TimeSource.SYSTEM, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public static CountdownTimer startCountdownTimer(long timeout, TimeUnit unit) {
        return new DefaultCountdownTimer(TimeSource.SYSTEM, timeout, unit);
    }

    private Time() {
    }

}
