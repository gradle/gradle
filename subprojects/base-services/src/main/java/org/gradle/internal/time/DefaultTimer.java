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

public class DefaultTimer implements Timer {
    private static final long MS_PER_MINUTE = 60000;
    private static final long MS_PER_HOUR = MS_PER_MINUTE * 60;
    private TimeSource timeSource;
    protected long startInstantMillis;

    DefaultTimer(TimeSource timeSource) {
        this.timeSource = timeSource;
        reset();
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

    @Override
    public String getElapsed() {
        long timeInMs = getElapsedMillis();
        return prettyTime(timeInMs);
    }

    @Override
    public long getElapsedMillis() {
        return Math.max(getInstantMillis() - startInstantMillis, 0);
    }

    public void reset() {
        startInstantMillis = getInstantMillis();
    }

    protected long getInstantMillis() {
        long nanos = timeSource.nanoTime();
        return TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS);
    }

    protected long getWallClockMillis() {
        return timeSource.currentTimeMillis();
    }
}
