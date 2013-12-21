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
    private long start;
    private TimeProvider timeProvider;

    private static final long MS_PER_MINUTE = 60000;
    private static final long MS_PER_HOUR = MS_PER_MINUTE * 60;

    public Clock() {
        this(new TrueTimeProvider());
    }

    public Clock(long start) {
        this(new TrueTimeProvider(), start);
    }

    protected Clock(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        reset();
    }

    protected Clock(TimeProvider timeProvider, long start) {
        this.timeProvider = timeProvider;
        this.start = start;
    }

    public String getTime() {
        long timeInMs = getTimeInMs();
        return prettyTime(timeInMs);
    }

    public long getTimeInMs() {
        return timeProvider.getCurrentTime() - start;
    }

    public void reset() {
        start = timeProvider.getCurrentTime();
    }

    public long getStartTime() {
        return start;
    }

    public static String prettyTime(long timeInMs) {
        StringBuffer result = new StringBuffer();
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
