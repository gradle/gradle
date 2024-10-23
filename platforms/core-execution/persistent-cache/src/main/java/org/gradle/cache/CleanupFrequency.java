/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.cache;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;

/**
 * Represents when cache cleanup should be triggered.
 *
 * @since 8.0
 */
public interface CleanupFrequency {
    /**
     * Trigger cleanup once every 24 hours.
     */
    CleanupFrequency DAILY = new CleanupFrequency() {
        @Override
        public boolean requiresCleanup(@Nullable Instant lastCleanupTime) {
            if (lastCleanupTime == null) {
                return true;
            } else {
                return Duration.between(lastCleanupTime, Instant.now())
                    .toHours() >= 24;
            }
        }
    };

    /**
     * Trigger cleanup after every build session
     */
    CleanupFrequency ALWAYS = new CleanupFrequency() {
        @Override
        public boolean requiresCleanup(@Nullable Instant lastCleanupTime) {
            return true;
        }

        @Override
        public boolean shouldCleanupOnEndOfSession() {
            return true;
        }
    };

    /**
     * Disable cleanup completely
     */
    CleanupFrequency NEVER = new CleanupFrequency() {
        @Override
        public boolean requiresCleanup(@Nullable Instant lastCleanupTime) {
            return false;
        }
    };

    boolean requiresCleanup(@Nullable Instant lastCleanupTime);
    default boolean shouldCleanupOnEndOfSession() {
        return false;
    }
}
