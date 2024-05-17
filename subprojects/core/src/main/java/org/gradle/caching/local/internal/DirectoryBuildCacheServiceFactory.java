/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.cache.CacheConfigurationsInternal;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.DefaultCacheCleanupStrategy;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.file.impl.SingleDepthFileAccessTracker;
import org.gradle.internal.time.TimestampSuppliers;

import javax.inject.Inject;
import java.io.File;
import java.util.function.Supplier;

import static org.gradle.cache.FileLockManager.LockMode.OnDemand;

public class DirectoryBuildCacheServiceFactory implements BuildCacheServiceFactory<DirectoryBuildCache> {
    public static final String FAILED_READ_SUFFIX = ".failed";

    private static final String BUILD_CACHE_VERSION = "1";
    private static final String BUILD_CACHE_KEY = "build-cache-" + BUILD_CACHE_VERSION;
    private static final String DIRECTORY_BUILD_CACHE_TYPE = "directory";
    private static final int FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP = 1;

    private final UnscopedCacheBuilderFactory unscopedCacheBuilderFactory;
    private final GlobalScopedCacheBuilderFactory cacheBuilderFactory;
    private final PathToFileResolver resolver;
    private final FileAccessTimeJournal fileAccessTimeJournal;
    private final CacheConfigurationsInternal cacheConfigurations;

    @Inject
    public DirectoryBuildCacheServiceFactory(
        UnscopedCacheBuilderFactory unscopedCacheBuilderFactory,
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        PathToFileResolver resolver,
        FileAccessTimeJournal fileAccessTimeJournal,
        CacheConfigurationsInternal cacheConfigurations
    ) {
        this.unscopedCacheBuilderFactory = unscopedCacheBuilderFactory;
        this.cacheBuilderFactory = cacheBuilderFactory;
        this.resolver = resolver;
        this.fileAccessTimeJournal = fileAccessTimeJournal;
        this.cacheConfigurations = cacheConfigurations;
    }

    @Override
    public BuildCacheService createBuildCacheService(DirectoryBuildCache configuration, Describer describer) {
        Object cacheDirectory = configuration.getDirectory();
        File target;
        if (cacheDirectory != null) {
            target = resolver.resolve(cacheDirectory);
        } else {
            target = cacheBuilderFactory.baseDirForCrossVersionCache(BUILD_CACHE_KEY);
        }
        checkDirectory(target);

        @SuppressWarnings("deprecation")
        int removeUnusedEntriesAfterDays = configuration.getRemoveUnusedEntriesAfterDays();

        describer.type(DIRECTORY_BUILD_CACHE_TYPE).
            config("location", target.getAbsolutePath()).
            config("removeUnusedEntriesAfter", removeUnusedEntriesAfterDays + " days");

        // Use the deprecated retention period if configured on `DirectoryBuildCache`, or use the central 'buildCache' cleanup config if not.
        // If the deprecated property remains at the default, we can safely use the central value (which has the same default).
        Supplier<Long> removeUnusedEntriesOlderThan = removeUnusedEntriesAfterDays == CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_BUILD_CACHE_ENTRIES
            ? cacheConfigurations.getBuildCache().getRemoveUnusedEntriesOlderThanAsSupplier()
            : TimestampSuppliers.daysAgo(removeUnusedEntriesAfterDays);

        PersistentCache persistentCache = unscopedCacheBuilderFactory
            .cache(target)
            .withCleanupStrategy(createCacheCleanupStrategy(removeUnusedEntriesOlderThan))
            .withDisplayName("Build cache")
            .withInitialLockMode(OnDemand)
            .open();
        FileAccessTracker fileAccessTracker = new SingleDepthFileAccessTracker(fileAccessTimeJournal, target, FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP);

        return new DirectoryBuildCacheService(persistentCache, fileAccessTracker, FAILED_READ_SUFFIX);
    }

    private CacheCleanupStrategy createCacheCleanupStrategy(Supplier<Long> removeUnusedEntriesTimestamp) {
        return DefaultCacheCleanupStrategy.from(
            createCleanupAction(removeUnusedEntriesTimestamp),
            cacheConfigurations.getCleanupFrequency()::get
        );
    }

    private LeastRecentlyUsedCacheCleanup createCleanupAction(Supplier<Long> removeUnusedEntriesTimestamp) {
        return new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(FILE_TREE_DEPTH_TO_TRACK_AND_CLEANUP), fileAccessTimeJournal, removeUnusedEntriesTimestamp);
    }

    private static void checkDirectory(File directory) {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be a directory", directory));
            }
            if (!directory.canRead()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be readable", directory));
            }
            if (!directory.canWrite()) {
                throw new IllegalArgumentException(String.format("Cache directory %s must be writable", directory));
            }
        } else {
            if (!directory.mkdirs()) {
                throw new UncheckedIOException(String.format("Could not create cache directory: %s", directory));
            }
        }
    }
}
