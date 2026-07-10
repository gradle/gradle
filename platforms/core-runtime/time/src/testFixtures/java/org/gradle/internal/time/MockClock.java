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

import com.google.common.base.Preconditions;

/**
 * Test implementation of the clock that can be adjusted manually. Can self-increment itself upon reads.
 */
public class MockClock implements Clock {
    public static final long DEFAULT_AUTOINCREMENT_MS = 10L;

    private long current;
    private boolean observed;
    private final long autoIncrement;

    private MockClock(long startTimeMs, long autoIncrement) {
        this.current = startTimeMs;
        this.autoIncrement = autoIncrement;
    }

    /**
     * Resets current time to {@code startTime} if nothing has observed the current time before.
     *
     * @param startTime the start time in milliseconds since epoch
     * @throws IllegalStateException if this clock has been read
     */
    public void withStartTime(long startTime) {
        Preconditions.checkState(!observed, "Something has already observed the current time");
        this.current = startTime;
    }

    /**
     * Increments current time by the specified amount of milliseconds.
     *
     * @param diff the amount of milliseconds to add to the current clock timestamp
     * @throws IllegalArgumentException if the diff is negative
     */
    public void increment(long diff) {
        Preconditions.checkArgument(diff >= 0, "Negative diff %d isn't allowed", diff);
        current += diff;
    }

    @Override
    public long getCurrentTime() {
        observed = true;
        long result = current;
        current += autoIncrement;
        return result;
    }

    /**
     * Creates an instance of a mock clock that starts at 0 and is only incremented by {@link #increment(long)}.
     *
     * @return the mock clock
     */
    public static MockClock create() {
        return new MockClock(0, 0);
    }

    /**
     * Creates an instance of a mock clock that starts at {@code startTime} and is incremented by {@link #DEFAULT_AUTOINCREMENT_MS} upon every {@link #getCurrentTime()} call.
     *
     * @param startTime start time in milliseconds since epoch
     * @return the mock clock
     */
    public static MockClock createAutoIncrementingAt(long startTime) {
        return new MockClock(startTime, DEFAULT_AUTOINCREMENT_MS);
    }
}
