/*
 * Copyright 2016 the original author or authors.
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

/**
 * A clock that uses elapsed time from a wall clock sync point to determine the time.
 * <p>
 * System.currentTimeMillis() is susceptible to system time adjustments, which happen in small amounts quite often.
 * This can cause problems in that subsequent clock reads may yield times that are earlier,
 * which leads to confusing timestamp event streams and inaccurate duration measurements.
 * <p>
 * This clock uses System.nanoTime() to track elapsed time since a wall clock read.
 * It is therefore impervious to system wall clock time adjustments and may report different
 * times than System.currentTimeMillis().
 * <p>
 * Another key difference is that only considers time that has elapsed while the system has been awake.
 * If the machine hibernates, the clock effectively stops until it awakens.
 * <p>
 * A clock may be attached to a {@link ClockSync}, which can sync it with the system wall clock.
 */
public interface Clock {

    /**
     * The current time based on elapsed awake time since the last wall clock sync.
     * <p>
     * Values are guaranteed to be monotonic, except after calls to {@link ClockSync#sync()}
     * of any owning sync, which may cause the next current time read to be earlier than previous reads.
     */
    long getCurrentTime();

}
