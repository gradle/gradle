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
    Property<Long> getEntryRetentionMillis();

    /**
     * Provides the timestamp (in millis since epoch) before which an unused entry can be removed from the cache.
     * This is an absolute-time value and is recalculated from the configuration on each build execution.
     */
    Supplier<Long> getEntryRetentionTimestampSupplier();

}
