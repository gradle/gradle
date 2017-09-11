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

import com.google.common.annotations.VisibleForTesting;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A clock that measures time based on elapsed time since an initial system wall clock read and never goes backwards.
 * <p>
 * This provider guarantees that non concurrent reads always yield a value that is greater or equal than the previous read.
 * This monotonicity guarantee is important to build scans.
 * <p>
 * While System.nanoTime() is usually monotonic it is not actually guaranteed, especially on virtualized hardware.
 *
 * - http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6458294
 *
 * @see Time#clock()
 */
public class MonotonicClock implements Clock {

    private final TimeSource timeSource;
    private final long startMillis;
    private final long startNanos;

    private final AtomicLong max = new AtomicLong();

    public MonotonicClock() {
        this(new TimeSource.True());
    }

    @VisibleForTesting
    MonotonicClock(TimeSource timeSource) {
        this.timeSource = timeSource;
        this.startMillis = timeSource.currentTimeMillis();
        this.startNanos = timeSource.nanoTime();
    }

    @Override
    public long getCurrentTime() {
        long elapsedMills = TimeUnit.NANOSECONDS.toMillis(timeSource.nanoTime() - startNanos);
        long currentTime = startMillis + elapsedMills;
        long currentMax;
        do {
            currentMax = max.get();
            currentTime = Math.max(currentTime, currentMax);
        } while (!max.compareAndSet(currentMax, currentTime));

        return currentTime;
    }

    interface TimeSource {

        long currentTimeMillis();

        long nanoTime();

        class True implements TimeSource {
            @Override
            public long currentTimeMillis() {
                return System.currentTimeMillis();
            }

            @Override
            public long nanoTime() {
                return System.nanoTime();
            }
        }

    }
}
