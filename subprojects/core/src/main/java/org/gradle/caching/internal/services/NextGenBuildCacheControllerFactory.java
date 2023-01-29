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

package org.gradle.caching.internal.services;

import org.gradle.StartParameter;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.internal.NoOpBuildCacheService;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.DefaultNextGenBuildCacheAccess;
import org.gradle.caching.internal.controller.GZipNextGenBuildCacheAccess;
import org.gradle.caching.internal.controller.NextGenBuildCacheController;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.caching.local.internal.H2LocalCacheService;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.vfs.FileSystemAccess;

import javax.annotation.Nullable;
import java.io.IOException;

public final class NextGenBuildCacheControllerFactory extends AbstractBuildCacheControllerFactory<H2LocalCacheService> {

    private static final String NEXT_GEN_CACHE_SYSTEM_PROPERTY = "org.gradle.unsafe.cache.ng";
    private final Deleter deleter;

    public static boolean isNextGenCachingEnabled() {
        return Boolean.getBoolean(NEXT_GEN_CACHE_SYSTEM_PROPERTY) == Boolean.TRUE;
    }

    public NextGenBuildCacheControllerFactory(
        StartParameter startParameter,
        BuildOperationExecutor buildOperationExecutor,
        OriginMetadataFactory originMetadataFactory,
        FileSystemAccess fileSystemAccess,
        StringInterner stringInterner,
        Deleter deleter
    ) {
        super(
            startParameter,
            buildOperationExecutor,
            originMetadataFactory,
            fileSystemAccess,
            stringInterner);
        this.deleter = deleter;
    }

    @Override
    protected BuildCacheController doCreateController(
        @Nullable DescribedBuildCacheService<DirectoryBuildCache, H2LocalCacheService> localDescribedService,
        @Nullable DescribedBuildCacheService<BuildCache, BuildCacheService> remoteDescribedService
    ) {

        BuildCacheService local = resolveService(localDescribedService);
        BuildCacheService remote = resolveService(remoteDescribedService);

        return new NextGenBuildCacheController(
            deleter,
            fileSystemAccess,
            new GZipNextGenBuildCacheAccess(
                new DefaultNextGenBuildCacheAccess(
                    local,
                    remote
                )
            )
        );
    }

    private static BuildCacheService resolveService(@Nullable DescribedBuildCacheService<? extends BuildCache, ? extends BuildCacheService> describedService) {
        if (describedService == null || !describedService.config.isEnabled()) {
            return NoOpBuildCacheService.INSTANCE;
        }
        return describedService.config.isPush()
            ? describedService.service
            : new NoPushBuildCacheService(describedService.service);
    }

    private static class NoPushBuildCacheService implements BuildCacheService {
        private final BuildCacheService delegate;

        public NoPushBuildCacheService(BuildCacheService delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
            return delegate.load(key, reader);
        }

        @Override
        public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
            // Do nothing
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static class Describer implements BuildCacheServiceFactory.Describer {

        @Override
        public BuildCacheServiceFactory.Describer type(String type) {
            return this;
        }

        @Override
        public BuildCacheServiceFactory.Describer config(String name, String value) {
            return this;
        }
    }
}
