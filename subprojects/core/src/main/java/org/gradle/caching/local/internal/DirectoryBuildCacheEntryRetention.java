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

package org.gradle.caching.local.internal;

import org.gradle.api.cache.Cleanup;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.api.internal.cache.CacheResourceConfigurationInternal;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.internal.time.TimestampSuppliers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class DirectoryBuildCacheEntryRetention {
    private final Supplier<Long> entryRetentionTimestampSupplier;
    private final String retentionDescription;
    private final boolean cleanupDisabled;

    public DirectoryBuildCacheEntryRetention(DirectoryBuildCache directoryBuildCache, CacheConfigurationsInternal cacheConfigurations) {
        this.cleanupDisabled = cacheConfigurations.getCleanup().get() == Cleanup.DISABLED;

        @SuppressWarnings("deprecation")
        int deprecatedRemoveUnusedEntriesAfter = directoryBuildCache.getRemoveUnusedEntriesAfterDays();

        // Use the deprecated retention period if configured on `DirectoryBuildCache`, or use the core 'buildCache' cleanup configuration if not.
        // If the deprecated property remains at the default, we can safely use the central value (which has the same default).
        if (deprecatedRemoveUnusedEntriesAfter != CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES) {
            this.entryRetentionTimestampSupplier = TimestampSuppliers.daysAgo(deprecatedRemoveUnusedEntriesAfter);
            this.retentionDescription = "after " + deprecatedRemoveUnusedEntriesAfter + " days";
            return;
        }

        CacheResourceConfigurationInternal buildCacheConfig = cacheConfigurations.getBuildCache();
        this.entryRetentionTimestampSupplier = buildCacheConfig.getEntryRetentionTimestampSupplier();
        this.retentionDescription = describeEntryRetention(buildCacheConfig.getEntryRetention().get());
    }

    public Supplier<Long> getEntryRetentionTimestampSupplier() {
        return entryRetentionTimestampSupplier;
    }

    public String getDescription() {
        if (cleanupDisabled) {
            return "disabled";
        }
        return retentionDescription;
    }

    private static String describeEntryRetention(CacheResourceConfigurationInternal.EntryRetention entryRetention) {
        long entryRetentionMillis = entryRetention.getTimeInMillis();

        if (entryRetention.isRelative()) {
            long expiryDays = TimeUnit.MILLISECONDS.toDays(entryRetentionMillis);
            if (expiryDays >= 2) {
                return "after " + expiryDays + " days";
            } else {
                return "after " + TimeFormatting.formatDurationTerse(entryRetentionMillis);
            }
        }

        // Always render the timestamp in UTC
        return "older than " + Instant.ofEpochMilli(entryRetentionMillis).atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }
}
