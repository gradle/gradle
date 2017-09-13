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
 * A clock that is guaranteed to not go backwards.
 * <p>
 * It aims to strike a balance between never going backwards (allowing timestamps to represent causality)
 * and keeping in sync with the system wall clock so that time values make sense in comparison with the system wall clock,
 * including timestamps generated from other processes.
 * <p>
 * This clock effectively measures time by duration (according to System.nanoTime()),
 * in between syncs with the system wall clock.
 * When issuing the first timestamp after the sync interval has expired,
 * The system wall clock will be read, and the current time set to the max of wall clock time or the most recently issued timestamp.
 * All other timestamps are calculated as the wall clock time at last sync + elapsed time since.
 * <p>
 * This clock deals relatively well when the system wall clock shift is adjusted by small amounts.
 * It also deals relatively well when the system wall clock jumps forward by large amounts (this clock will jump with it).
 * It does not deal as well with large jumps back in time.
 * <p>
 * When the system wall clock jumps back in time, this clock will effectively slow down until it is back in sync.
 * All syncing timestamps will be the same as the previously issued timestamp.
 * The rate by which this clock slows, and therefore the time it takes to resync,
 * is determined by how frequently the clock is read.
 * If timestamps are only requested at a rate greater than the sync interval,
 * all timestamps will have the same value until the clocks synchronize (i.e. this clock will pause).
 * If timestamps are requested more frequently than the sync interval,
 * timestamps before and after the sync point will under represent the actual elapsed time,
 * gradually bringing the clocks back into sync.
 */
class MonotonicClock implements Clock {

    private static final long SYNC_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(3);

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
