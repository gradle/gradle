/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.vcs.internal.services;

import org.gradle.cache.CacheCleanupStrategyFactory;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.LeastRecentlyUsedCacheCleanup;
import org.gradle.cache.internal.SingleDepthFilesFinder;
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory;
import org.gradle.internal.file.nio.ModificationTimeFileAccessTimeJournal;
import org.gradle.internal.service.scopes.ListenerService;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.session.BuildSessionLifecycleListener;
import org.gradle.internal.watch.vfs.impl.FileWatchingFilter;
import org.jspecify.annotations.NullMarked;

import static org.gradle.api.internal.cache.CacheConfigurationsInternal.DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES;
import static org.gradle.internal.time.TimestampSuppliers.daysAgo;

/**
 * Creates the cache that holds source-dependency VCS checkouts, and manages its watchability.
 *
 * <p>The cache lives under the project cache directory, which file-system watching otherwise treats as an
 * immutable location. Checkouts are build directories whose contents can change externally, so they should
 * be watched like any other build directory. This service registers the cache directory as a watchable
 * exemption at the start of the build session — before the configuration cache checks its fingerprint and
 * registers the stored build root directories as watchable hierarchies. Registering the exemption is a pure
 * path computation, so it does not create the cache; the cache is created on demand via {@link #createCache()}
 * only when a build actually uses source dependencies.
 */
@NullMarked
@ServiceScope(Scope.BuildSession.class)
@ListenerService
public class VersionControlRepositoryCacheFactory implements BuildSessionLifecycleListener {
    private static final String WORKING_DIRS_CACHE_KEY = "vcs-1";

    private final BuildTreeScopedCacheBuilderFactory cacheBuilderFactory;
    private final CacheCleanupStrategyFactory cacheCleanupStrategyFactory;
    private final FileWatchingFilter fileWatchingFilter;

    public VersionControlRepositoryCacheFactory(
        BuildTreeScopedCacheBuilderFactory cacheBuilderFactory,
        CacheCleanupStrategyFactory cacheCleanupStrategyFactory,
        FileWatchingFilter fileWatchingFilter
    ) {
        this.cacheBuilderFactory = cacheBuilderFactory;
        this.cacheCleanupStrategyFactory = cacheCleanupStrategyFactory;
        this.fileWatchingFilter = fileWatchingFilter;
    }

    @Override
    public void afterStart() {
        // Registering the exemption only needs the cache directory location, which is a pure path
        // computation and does not create the cache. It must happen before the configuration cache
        // fingerprint check registers the stored build root directories as watchable hierarchies.
        fileWatchingFilter.addWatchableExemption(cacheBuilderFactory.baseDirForCrossVersionCache(WORKING_DIRS_CACHE_KEY));
    }

    public PersistentCache createCache() {
        return cacheBuilderFactory
            .createCrossVersionCacheBuilder(WORKING_DIRS_CACHE_KEY)
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .withDisplayName("VCS Checkout Cache")
            .withCleanupStrategy(cacheCleanupStrategyFactory.daily(
                new LeastRecentlyUsedCacheCleanup(new SingleDepthFilesFinder(1), new ModificationTimeFileAccessTimeJournal(), daysAgo(DEFAULT_MAX_AGE_IN_DAYS_FOR_CREATED_CACHE_ENTRIES))
            ))
            .open();
    }
}
