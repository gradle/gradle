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
 * Test utilities for timing routines.
 */
public final class TestTime {
    private TestTime() {}

    /**
     * Returns a timestamp of {@code epochMs} milliseconds since Unix epoch.
     * @param epochMs epoch offset in milliseconds
     * @return the timestamp
     */
    public static Timestamp timestampOf(long epochMs) {
        return Timestamp.ofMillis(epochMs);
    }
}
