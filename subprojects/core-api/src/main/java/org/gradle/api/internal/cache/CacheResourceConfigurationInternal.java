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
     * Configures the age (in milliseconds) when an unused entry can be removed from the cache.
     * This is a relative-time value and is persisted in the build configuration.
     *
     * See {@link #setRemoveUnusedEntriesAfterDays(int)}.
     */
    Property<Long> getEntryRetentionAge();

    /**
     * Configures the timestamp at which unused entries will be considered stale and removed from the cache.
     * Any entries not used since this timestamp will be candidates for eviction.
     * This is an absolute time value and is persisted in the build configuration.
     *
     * A value configured for this property will take precedence over `entryRetentionAge`.
     */
    Property<Long> getEntryRetentionTimestamp();

    /**
     * Provides the timestamp (in millis since epoch) before which an unused entry can be removed from the cache.
     * If {@link #getEntryRetentionTimestamp()} is configured, then this value is used.
     * Otherwise, the value of {@link #getEntryRetentionAge()} is subtracted from the current time.
     *
     * This is an absolute-time value and is recalculated from the configuration on each build execution.
     */
    Supplier<Long> getEntryRetentionTimestampSupplier();

}
