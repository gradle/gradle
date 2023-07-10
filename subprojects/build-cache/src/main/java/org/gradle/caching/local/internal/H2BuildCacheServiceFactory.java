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
import org.gradle.api.internal.cache.DefaultCacheCleanupStrategy;
import org.gradle.cache.CacheCleanupStrategy;
import org.gradle.cache.CleanupAction;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.HasCleanupAction;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.CleanupActionDecorator;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.time.Time;

import javax.inject.Inject;
import java.io.File;
import java.util.function.Function;

import static org.gradle.cache.FileLockManager.LockMode.None;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class H2BuildCacheServiceFactory implements BuildCacheServiceFactory<DirectoryBuildCache> {
    private static final String BUILD_CACHE_VERSION = "2";
    private static final String BUILD_CACHE_KEY = "build-cache-" + BUILD_CACHE_VERSION;
    private static final String H2_BUILD_CACHE_TYPE = "h2";

    private final FileLockManager lockManager;
    private final UnscopedCacheBuilderFactory unscopedCacheBuilderFactory;
    private final GlobalScopedCacheBuilderFactory cacheBuilderFactory;
    private final ParallelismConfiguration parallelismConfiguration;
    private final PathToFileResolver resolver;
    private final CleanupActionDecorator cleanupActionDecorator;

    @Inject
    public H2BuildCacheServiceFactory(
        FileLockManager lockManager,
        CleanupActionDecorator cleanupActionDecorator,
        UnscopedCacheBuilderFactory unscopedCacheBuilderFactory,
        GlobalScopedCacheBuilderFactory cacheBuilderFactory,
        ParallelismConfiguration parallelismConfiguration,
        PathToFileResolver resolver
    ) {
        this.lockManager = lockManager;
        this.cleanupActionDecorator = cleanupActionDecorator;
        this.unscopedCacheBuilderFactory = unscopedCacheBuilderFactory;
        this.cacheBuilderFactory = cacheBuilderFactory;
        this.parallelismConfiguration = parallelismConfiguration;
        this.resolver = resolver;
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

        int removeUnusedEntriesAfterDays = configuration.getRemoveUnusedEntriesAfterDays();
        describer.type(H2_BUILD_CACHE_TYPE)
            .config("location", target.getAbsolutePath())
            .config("removeUnusedEntriesAfter", removeUnusedEntriesAfterDays + " days");
        // TODO: H2Cache could be provided by PersistentCache
        // TODO: Add open/close functionality to LockOptionsBuilder, so we can open and close database when process acquires a lock
        //  and we can remove crossProcessCacheAccess logic from LockOnDemandCrossProcessBuildCacheService
        // For now PersistentCache is used just so we can reuse cleanup infrastructure, but in future H2Cache could be provided by PersistentCache
        Function<HasCleanupAction, PersistentCache> persistentCacheFactory = buildCacheService -> unscopedCacheBuilderFactory
            .cache(target)
            .withCleanupStrategy(createCacheCleanupStrategy((cleanableStore, progressMonitor) -> buildCacheService.cleanup()))
            .withDisplayName("Build cache NG")
            .withLockOptions(mode(None))
            .open();
        H2BuildCacheService h2Service = new H2BuildCacheService(target.toPath(), parallelismConfiguration.getMaxWorkerCount(), removeUnusedEntriesAfterDays, Time.clock());
        return new LockOnDemandCrossProcessBuildCacheService("build-cache-2", target, lockManager, h2Service, persistentCacheFactory);
    }

    private CacheCleanupStrategy createCacheCleanupStrategy(CleanupAction cleanupAction) {
        return DefaultCacheCleanupStrategy.from(cleanupActionDecorator.decorate(cleanupAction));
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
