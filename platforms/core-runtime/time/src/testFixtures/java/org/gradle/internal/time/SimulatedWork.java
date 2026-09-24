/*
 * Copyright 2026 the original author or authors.
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
 * Generates build script snippets that occupy the executing thread for a known amount of time,
 * for tests that assert on the durations Gradle measures and reports — build operation results,
 * user code application timings, and the Tooling API progress events built from them.
 * <p>
 * The generated snippet does not simply sleep. It waits until {@link Time#nanoTime()}, the clock
 * those durations are measured with, has advanced by at least the requested amount. A measurement
 * taken around the snippet is then guaranteed to cover it.
 */
public class SimulatedWork {

    /**
     * A duration that is long enough to be measured reliably by every clock Gradle reports
     * durations with, while remaining short enough to spend in a test.
     */
    public static final long WORK_MILLIS = 100;

    private SimulatedWork() {
    }

    /**
     * A snippet that occupies the executing thread for {@link #WORK_MILLIS}.
     */
    public static String simulateWork() {
        return simulateWork(WORK_MILLIS);
    }

    /**
     * A snippet that occupies the executing thread until the clock that measures reported
     * durations has advanced by the given duration.
     */
    public static String simulateWork(long durationMillis) {
        // Wrapped in a closure so that several of these can be used in the same scope.
        return String.format("""
            ({
                def deadline = org.gradle.internal.time.Time.nanoTime() + %dL
                def remaining
                while ((remaining = deadline - org.gradle.internal.time.Time.nanoTime()) > 0) {
                    Thread.sleep(Math.max(1L, (long) (remaining / 1_000_000L)))
                }
            })()
            """, TimeUnit.MILLISECONDS.toNanos(durationMillis));
    }

    /**
     * {@link #WORK_MILLIS} in nanoseconds, for comparing against a reported duration.
     */
    public static long workNanos() {
        return workNanos(WORK_MILLIS);
    }

    /**
     * The given duration in nanoseconds, for comparing against a reported duration.
     */
    public static long workNanos(long durationMillis) {
        return TimeUnit.MILLISECONDS.toNanos(durationMillis);
    }

}
