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
 * An immutable timestamp. Can be accessed in different bases.
 */
public final class Timestamp {
    private final long timeMs;

    private Timestamp(long timeMs) {
        this.timeMs = timeMs;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public static Timestamp ofMillis(long epochMs) {
        return new Timestamp(epochMs);
    }
}
