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

import java.time.Instant;

/**
 * Specifies the details of cache cleanup, including the action to be performed and the frequency at which cache cleanup should occur.
 */
public interface CacheCleanupStrategy {
    CacheCleanupStrategy NO_CLEANUP = new CacheCleanupStrategy() {
        @Override
        public void clean(CleanableStore store, Instant lastCleanupTime) {
            CleanupAction.NO_OP.clean(store, null);
        }

        @Override
        public CleanupFrequency getCleanupFrequency() {
            return CleanupFrequency.NEVER;
        }
    };

    /**
     * Cleans the given store based on this cleanup strategy.
     */
    void clean(CleanableStore store, Instant lastCleanupTime);

    /**
     * Returns the frequency at which cache cleanup can occur.  Possible values are only once a day, every time, or never.
     */
    CleanupFrequency getCleanupFrequency();
}
