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
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.file.PathToFileResolver;

import javax.inject.Inject;
import java.io.File;

public class H2BuildCacheServiceFactory implements BuildCacheServiceFactory<DirectoryBuildCache> {
    private static final String BUILD_CACHE_VERSION = "2";
    private static final String BUILD_CACHE_KEY = "build-cache-" + BUILD_CACHE_VERSION;
    private static final String H2_BUILD_CACHE_TYPE = "h2";

    private final GlobalScopedCacheBuilderFactory cacheBuilderFactory;
    private final ParallelismConfiguration parallelismConfiguration;
    private final PathToFileResolver resolver;

    @Inject
    public H2BuildCacheServiceFactory(
            GlobalScopedCacheBuilderFactory cacheBuilderFactory,
            ParallelismConfiguration parallelismConfiguration,
            PathToFileResolver resolver
    ) {
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
        describer.type(H2_BUILD_CACHE_TYPE).
            config("location", target.getAbsolutePath());

        return new H2BuildCacheService(target.toPath(), parallelismConfiguration.getMaxWorkerCount());
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
