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

/**
 * A clock that always returns the same time.
 */
public class FixedClock implements Clock {
    private final long current;

    private FixedClock(long startTime) {
        current = startTime;
    }

    @Override
    public long getCurrentTime() {
        return current;
    }

    /**
     * Creates a clock that always returns 0 as the current time.
     *
     * @return the clock
     */
    public static Clock create() {
        return new FixedClock(0L);
    }

    /**
     * Creates a clock that always returns {@code startTime} as the current time.
     *
     * @param startTime start time in milliseconds since epoch
     * @return the clock
     */
    public static Clock createAt(long startTime) {
        return new FixedClock(startTime);
    }
}
