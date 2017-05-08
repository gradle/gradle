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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.internal.operations.BuildOperationExecutor;

import javax.inject.Inject;
import java.io.File;

public class DirectoryBuildCacheServiceFactory implements BuildCacheServiceFactory<DirectoryBuildCache> {
    private static final String BUILD_CACHE_VERSION = "1";
    private static final String BUILD_CACHE_KEY = "build-cache-" + BUILD_CACHE_VERSION;
    private static final String DIRECTORY_BUILD_CACHE_TYPE = "directory";

    private final CacheRepository cacheRepository;
    private final CacheScopeMapping cacheScopeMapping;
    private final FileResolver resolver;
    private final BuildOperationExecutor buildOperationExecutor;

    @Inject
    public DirectoryBuildCacheServiceFactory(CacheRepository cacheRepository, CacheScopeMapping cacheScopeMapping, FileResolver resolver, BuildOperationExecutor buildOperationExecutor) {
        this.cacheRepository = cacheRepository;
        this.cacheScopeMapping = cacheScopeMapping;
        this.resolver = resolver;
        this.buildOperationExecutor = buildOperationExecutor;
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

        describer.type(DIRECTORY_BUILD_CACHE_TYPE).config("location", target.getAbsolutePath());

        return new DirectoryBuildCacheService(cacheRepository, buildOperationExecutor, target, configuration.getTargetSizeInMB());
    }
}
