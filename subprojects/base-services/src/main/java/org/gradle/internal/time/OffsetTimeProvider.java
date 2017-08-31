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

/**
 * A time provider that disregards wall clock time adjustments after construction.
 * <p>
 * System.currentTimeMillis() provides the system wall clock time,
 * which may be adjusted during the JVM's lifetime.
 * This provider provides the time based on the system wall clock time
 * at construction and the elapsed time since.
 *
 * @see MonotonicTimeProvider
 */
class OffsetTimeProvider implements TimeProvider {

    private final TimeSource timeSource;
    private final long startMillis;
    private final long startNanos;

    OffsetTimeProvider() {
        this(new TimeSource.True());
    }

    @VisibleForTesting
    OffsetTimeProvider(TimeSource timeSource) {
        this.timeSource = timeSource;
        startMillis = timeSource.currentTimeMillis();
        startNanos = timeSource.nanoTime();
    }

    @Override
    public long getCurrentTime() {
        long elapsedMills = TimeUnit.NANOSECONDS.toMillis(timeSource.nanoTime() - startNanos);
        return startMillis + elapsedMills;
    }

}
