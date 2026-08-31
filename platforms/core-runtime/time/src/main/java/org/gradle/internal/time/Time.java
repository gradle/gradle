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

import org.gradle.internal.os.OperatingSystem;

import java.util.concurrent.TimeUnit;

/**
 * Instruments for observing time.
 */
public abstract class Time {

    /**
     * Reads time directly from the platform.
     */
    private static final TimeSource SYSTEM = new TimeSource() {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }
    };

    /**
     * The time source backing all instruments created by this class.
     * <p>
     * On Windows, since the platform's high-resolution timer can be expensive, this is a
     * {@link SwappableTimeSource} so that a {@link TimeSourceManager} can substitute a cached
     * source when it measures the timer to actually be expensive. On other platforms, where
     * the timer is known to be fast, this reads from the platform directly, avoiding
     * the additional indirection.
     */
    private static final TimeSource TIME_SOURCE = OperatingSystem.current().isWindows() ? new SwappableTimeSource(SYSTEM) : SYSTEM;

    private static final Clock CLOCK = new MonotonicClock(TIME_SOURCE, TimeUnit.SECONDS.toMillis(3));

    /**
     * Updates {@link #TIME_SOURCE} to read from the given source. Used by {@link TimeSourceManager}
     * to install a cached time source when the platform time source is measured to be expensive.
     *
     * @throws IllegalStateException If called on a platform whose time source is not swappable.
     */
    static void installTimeSource(TimeSource timeSource) {
        swappableTimeSource().set(timeSource);
    }

    /**
     * Restores direct platform readings for {@link #TIME_SOURCE}.
     *
     * @throws IllegalStateException If called on a platform whose time source is not swappable.
     */
    static void uninstallTimeSource() {
        swappableTimeSource().set(SYSTEM);
    }

    private static SwappableTimeSource swappableTimeSource() {
        if (!(TIME_SOURCE instanceof SwappableTimeSource)) {
            throw new IllegalStateException("The static time source is not swappable on this platform.");
        }
        return (SwappableTimeSource) TIME_SOURCE;
    }

    /**
     * The time source that is currently active. Returns the installed source, if any,
     * otherwise the platform time source.
     */
    static TimeSource getEffectiveTimeSource() {
        if (TIME_SOURCE instanceof SwappableTimeSource) {
            return ((SwappableTimeSource) TIME_SOURCE).get();
        }
        return TIME_SOURCE;
    }

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
     * Timers use the monotonic high-resolution time source to measure elapsed time,
     * and are therefore not synchronized with {@link #clock()} or the system wall clock.
     *
     * The underlying time source does not consider time elapsed while the system is in hibernation.
     * Therefore, timers effectively measure the elapsed time, of which the system was awake.
     */
    public static Timer startTimer() {
        return new DefaultTimer(TIME_SOURCE);
    }

    public static CountdownTimer startCountdownTimer(long timeoutMillis) {
        return new DefaultCountdownTimer(TIME_SOURCE, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public static CountdownTimer startCountdownTimer(long timeout, TimeUnit unit) {
        return new DefaultCountdownTimer(TIME_SOURCE, timeout, unit);
    }

    private Time() {
    }

}
