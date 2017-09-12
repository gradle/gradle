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
     * A clock that guarantees monotonic timestamps.
     *
     * This clock differs from the system wall clock in that it determines the current time
     * based on the elapsed “CPU time”, and periodically syncing with the system wall clock.
     */
    public static Clock clock() {
        return CLOCK;
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
