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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A clock that measures time based on elapsed time since an initial system wall clock read and never goes backwards.
 * <p>
 * This provider guarantees that non concurrent reads always yield a value that is greater or equal than the previous read.
 * This monotonicity guarantee is important to build scans.
 * <p>
 * While System.nanoTime() is usually monotonic it is not actually guaranteed, especially on virtualized hardware.
 *
 * - http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6458294
 */
class MonotonicClock implements Clock {

    private static final long SYNC_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(5);

    private final TimeSource timeSource;

    private long syncMillis;
    private long syncNanos;

    private long max;

    private final Lock lock = new ReentrantLock();
    private long syncIntervalMillis;

    MonotonicClock() {
        this(TimeSource.SYSTEM, SYNC_INTERVAL_MILLIS);
    }

    @VisibleForTesting
    MonotonicClock(TimeSource timeSource, long syncIntervalMillis) {
        this.timeSource = timeSource;
        this.syncMillis = this.max = timeSource.currentTimeMillis();
        this.syncNanos = timeSource.nanoTime();
        this.syncIntervalMillis = syncIntervalMillis;
    }

    public long getCurrentTime() {
        lock.lock();
        try {
            long nowNanos = timeSource.nanoTime();
            long sinceSyncNanos = nowNanos - syncNanos;
            long sinceSyncMillis = TimeUnit.NANOSECONDS.toMillis(sinceSyncNanos);
            long currentTime = syncMillis + sinceSyncMillis;

            if (sinceSyncMillis >= syncIntervalMillis) {
                syncNanos = nowNanos;
                return syncMillis = max = Math.max(timeSource.currentTimeMillis(), max);
            } else {
                return max = Math.max(currentTime, max);
            }
        } finally {
            lock.unlock();
        }
    }

}
