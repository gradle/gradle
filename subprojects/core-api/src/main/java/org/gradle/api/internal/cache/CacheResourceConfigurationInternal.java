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

package org.gradle.api.internal.cache;

import org.gradle.api.cache.CacheResourceConfiguration;
import org.gradle.api.provider.Property;

import java.util.function.Supplier;

public interface CacheResourceConfigurationInternal extends CacheResourceConfiguration {
    /**
     * Specifies the timestamp after which an entry must have been used in order to be retained in the cache.
     * Any entries not used more recently than this timestamp will be candidates for eviction.
     */
    void setRemoveUnusedEntriesOlderThan(long timestamp);

    /**
     * Configures the retention strategy that determines when an unused entry can be removed from the cache.
     *
     * The retention can either be:
     * - Absolute: remove any entries not used since this timestamp
     * - Relative: remove any entries not used in the past X milliseconds
     *
     * See {@link #setRemoveUnusedEntriesAfterDays(int)}.
     */
    Property<EntryRetention> getEntryRetention();

    /**
     * Provides the timestamp (in millis since epoch) before which an unused entry can be removed from the cache.
     * This is an absolute-time value and is recalculated from the configuration on each build execution.
     */
    Supplier<Long> getEntryRetentionTimestampSupplier();

    class EntryRetention {
        private final boolean isRelative;
        private final long timeInMillis;

        public EntryRetention(boolean isRelative, long timeInMillis) {
            this.isRelative = isRelative;
            this.timeInMillis = timeInMillis;
        }

        public static EntryRetention relative(long timeInMillis) {
            return new EntryRetention(true, timeInMillis);
        }

        public static EntryRetention absolute(long timeInMillis) {
            return new EntryRetention(false, timeInMillis);
        }

        public boolean isRelative() {
            return isRelative;
        }

        public long getTimeInMillis() {
            return timeInMillis;
        }
    }

}
