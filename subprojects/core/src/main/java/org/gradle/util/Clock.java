/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;

public class Clock {
    private long startTime;
    private long startInstant;
    private TimeProvider timeProvider;

    private static final long MS_PER_MINUTE = 60000;
    private static final long MS_PER_HOUR = MS_PER_MINUTE * 60;

    public Clock() {
        this(new TrueTimeProvider());
    }

    // TODO:DAZ Remove this
    public Clock(long startInstant) {
        this(new TrueTimeProvider(), startInstant);
    }

    protected Clock(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        reset();
    }

    // TODO:DAZ Remove this
    protected Clock(TimeProvider timeProvider, long startInstant) {
        this(timeProvider, timeProvider.getCurrentTime(), startInstant);
    }

    private Clock(TimeProvider timeProvider, long startTime, long startInstant) {
        this.timeProvider = timeProvider;
        this.startTime = startTime;
        this.startInstant = startInstant;
    }

    public String getElapsed() {
        long timeInMs = getElapsedMillis();
        return prettyTime(timeInMs);
    }

    public long getElapsedMillis() {
        return Math.max(timeProvider.getCurrentTimeForDuration() - startInstant, 0);
    }

    public void reset() {
        startTime = timeProvider.getCurrentTime();
        startInstant = timeProvider.getCurrentTimeForDuration();
    }

    public long getStartTime() {
        return startTime;
    }

    public static String prettyTime(long timeInMs) {
        StringBuilder result = new StringBuilder();
        if (timeInMs > MS_PER_HOUR) {
            result.append(timeInMs / MS_PER_HOUR).append(" hrs ");
        }
        if (timeInMs > MS_PER_MINUTE) {
            result.append((timeInMs % MS_PER_HOUR) / MS_PER_MINUTE).append(" mins ");
        }
        result.append((timeInMs % MS_PER_MINUTE) / 1000.0).append(" secs");
        return result.toString();
    }
}
