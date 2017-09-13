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

    private static final Clock CLOCK = new MonotonicClock();

    /**
     * A clock that is guaranteed not to go backwards.
     *
     * This should generally be used by Gradle processes instead of System.currentTimeMillis().
     * For the gory details, see {@link MonotonicClock}.
     *
     * For timing activities, where correlation with the current time is not required, use {@link #startTimer()}.
     */
    public static Clock clock() {
        return CLOCK;
    }

    /**
     * Replacement for System.currentTimeMillis(), based on {@link #clock()}.
     */
    public static long currentTimeMillis() {
        return CLOCK.getCurrentTime();
    }

    /**
     * Measures elapsed time.
     *
     * Timers use System.nanoTime() to measure elapsed time,
     * and are therefore not synchronized with {@link #clock()} or the system wall clock.
     *
     * System.nanoTime() does not consider time elapsed while the system is in hibernation.
     * Therefore, timers effectively measure the elapsed time, of which the system was awake.
     */
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
