/*
 * Copyright 2024 the original author or authors.
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
 * An immutable timestamp. Can be accessed in different bases.
 */
public final class Timestamp implements Comparable<Timestamp> {
    private static final long MS_IN_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    private final long timeMs;
    private final int nanoAdjustment;

    private Timestamp(long timeMs, int nanoAdjustment) {
        this.timeMs = timeMs;
        this.nanoAdjustment = nanoAdjustment;
    }

    /**
     * Returns the epoch offset in milliseconds.
     *
     * @return the offset in milliseconds
     */
    public long getTimeMs() {
        return timeMs;
    }

    /**
     * Gets the number of nanoseconds, from the start of the millisecond returned by {@link #getTimeMs()}.
     *
     * @return the nanoseconds within the milliseconds, between {@code 0} and {@code 999,999}, inclusive.
     */
    public int getNanosOfMillis() {
        return nanoAdjustment;
    }

    /**
     * Returns a timestamp of {@code epochMs} milliseconds since Unix epoch.
     * <p>
     * Prefer using {@link Clock#getTimestamp()} to obtain the current timestamp to avoid potentially losing precision.
     *
     * @param epochMs epoch offset in milliseconds
     * @return the timestamp
     */
    public static Timestamp ofMillis(long epochMs) {
        return ofMillis(epochMs, 0);
    }

    /**
     * Returns a timestamp of {@code epochMs} milliseconds plus {@code nanoAdjustment} nanoseconds since Unix epoch.
     *
     * @param epochMs epoch offset in milliseconds
     * @param nanoAdjustment extra non-negative nanoseconds adjustment, less than one millisecond
     * @return the timestamp
     */
    public static Timestamp ofMillis(long epochMs, long nanoAdjustment) {
        if (nanoAdjustment < 0 || nanoAdjustment >= MS_IN_NANOS) {
            throw new IllegalArgumentException("nanoAdjustment is invalid: " + nanoAdjustment);
        }
        return new Timestamp(epochMs, (int) nanoAdjustment);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Timestamp)) {
            return false;
        }
        Timestamp timestamp = (Timestamp) o;
        return timeMs == timestamp.timeMs && nanoAdjustment == timestamp.nanoAdjustment;
    }

    @Override
    public int hashCode() {
        // Long.hashCode but Java 6-compatible
        return 31 * nanoAdjustment + (int) (timeMs ^ (timeMs >>> 32));
    }

    @Override
    public String toString() {
        return timeMs + "ms";
    }

    @Override
    public int compareTo(Timestamp o) {
        int compareMillis = compareMillis(o);
        return compareMillis != 0 ? compareMillis : compareNanos(o);
    }

    private int compareMillis(Timestamp o) {
        return timeMs == o.timeMs
            ? 0
            : timeMs < o.timeMs ? -1 : 1;
    }

    private int compareNanos(Timestamp o) {
        return nanoAdjustment == o.nanoAdjustment
            ? 0
            : nanoAdjustment < o.nanoAdjustment ? -1 : 1;
    }
}
