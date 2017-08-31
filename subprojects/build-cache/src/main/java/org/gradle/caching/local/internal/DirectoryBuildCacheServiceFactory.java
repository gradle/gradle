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

import org.apache.commons.io.FileUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.CacheBuilder;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.FixedSizeOldestCacheCleanup;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resource.local.PathKeyFileStore;

import javax.inject.Inject;
import java.io.File;

import static org.gradle.cache.FileLockManager.LockMode.None;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DirectoryBuildCacheServiceFactory implements BuildCacheServiceFactory<DirectoryBuildCache> {
    public static final String FAILED_READ_SUFFIX = ".failed";

    private static final String BUILD_CACHE_VERSION = "1";
    private static final String BUILD_CACHE_KEY = "build-cache-" + BUILD_CACHE_VERSION;
    private static final String DIRECTORY_BUILD_CACHE_TYPE = "directory";

    private final CacheRepository cacheRepository;
    private final CacheScopeMapping cacheScopeMapping;
    private final PathToFileResolver resolver;
    private final BuildOperationExecutor buildOperationExecutor;
    private final DirectoryBuildCacheFileStoreFactory fileStoreFactory;

    @Inject
    public DirectoryBuildCacheServiceFactory(CacheRepository cacheRepository, CacheScopeMapping cacheScopeMapping, PathToFileResolver resolver, BuildOperationExecutor buildOperationExecutor, DirectoryBuildCacheFileStoreFactory fileStoreFactory) {
        this.cacheRepository = cacheRepository;
        this.cacheScopeMapping = cacheScopeMapping;
        this.resolver = resolver;
        this.buildOperationExecutor = buildOperationExecutor;
        this.fileStoreFactory = fileStoreFactory;
    }

    @Override
    public BuildCacheService createBuildCacheService(DirectoryBuildCache configuration, Describer describer) {
        Object cacheDirectory = configuration.getDirectory();
        File target;
        if (cacheDirectory != null) {
            target = resolver.resolve(cacheDirectory);
        } else {
            target = cacheScopeMapping.getBaseDirectory(null, BUILD_CACHE_KEY, VersionStrategy.SharedCache);
        }
        checkDirectory(target);

        long targetSizeInMB = configuration.getTargetSizeInMB();
        String humanReadableCacheSize = FileUtils.byteCountToDisplaySize(targetSizeInMB *1024*1024);
        describer.type(DIRECTORY_BUILD_CACHE_TYPE).
            config("location", target.getAbsolutePath()).
            config("targetSize", humanReadableCacheSize);

        PathKeyFileStore fileStore = fileStoreFactory.createFileStore(target);
        PersistentCache persistentCache = cacheRepository
            .cache(target)
            .withCleanup(new FixedSizeOldestCacheCleanup(buildOperationExecutor, targetSizeInMB, BuildCacheTempFileStore.PARTIAL_FILE_SUFFIX))
            .withDisplayName("Build cache")
            .withLockOptions(mode(None))
            .withCrossVersionCache(CacheBuilder.LockTarget.DefaultTarget)
            .open();
        BuildCacheTempFileStore tempFileStore = new DefaultBuildCacheTempFileStore(target, BuildCacheTempFileStore.PARTIAL_FILE_SUFFIX);

        return new DirectoryBuildCacheService(fileStore, persistentCache, tempFileStore, FAILED_READ_SUFFIX);
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
