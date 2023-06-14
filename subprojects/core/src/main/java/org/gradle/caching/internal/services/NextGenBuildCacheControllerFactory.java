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
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.internal.NextGenBuildCacheService;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.DefaultNextGenBuildCacheAccess;
import org.gradle.caching.internal.controller.GZipNextGenBuildCacheAccess;
import org.gradle.caching.internal.controller.NextGenBuildCacheController;
import org.gradle.caching.internal.controller.RemoteNextGenBuildCacheServiceHandler;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.caching.local.internal.H2BuildCacheService;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.file.BufferProvider;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.util.internal.IncubationLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import javax.annotation.Nullable;
import java.io.IOException;

public final class NextGenBuildCacheControllerFactory extends AbstractBuildCacheControllerFactory<H2BuildCacheService> {

    private final Deleter deleter;
    private final BuildInvocationScopeId buildInvocationScopeId;
    private final ExecutorFactory executorFactory;
    private final BufferProvider bufferProvider;

    public NextGenBuildCacheControllerFactory(
        StartParameter startParameter,
        BuildOperationExecutor buildOperationExecutor,
        OriginMetadataFactory originMetadataFactory,
        FileSystemAccess fileSystemAccess,
        StringInterner stringInterner,
        Deleter deleter,
        BuildInvocationScopeId buildInvocationScopeId,
        ExecutorFactory executorFactory,
        BufferProvider bufferProvider
    ) {
        super(
            startParameter,
            buildOperationExecutor,
            originMetadataFactory,
            fileSystemAccess,
            stringInterner);
        this.deleter = deleter;
        this.buildInvocationScopeId = buildInvocationScopeId;
        this.executorFactory = executorFactory;
        this.bufferProvider = bufferProvider;
    }

    @Override
    protected BuildCacheController doCreateController(
        @Nullable DescribedBuildCacheService<DirectoryBuildCache, H2BuildCacheService> localDescribedService,
        @Nullable DescribedBuildCacheService<BuildCache, BuildCacheService> remoteDescribedService
    ) {
        IncubationLogger.incubatingFeatureUsed("Next generation build cache");

        if (localDescribedService == null) {
            // TODO Make this understandable to the compiler as well
            throw new NullPointerException("Local cache shouldn't be null");
        }
        NextGenBuildCacheService local = localDescribedService.service;
        RemoteNextGenBuildCacheServiceHandler remote = resolveRemoteService(remoteDescribedService);
        Logger logger = startParameter.isBuildCacheDebugLogging()
            ? LoggerFactory.getLogger(NextGenBuildCacheController.class)
            : NOPLogger.NOP_LOGGER;

        return new NextGenBuildCacheController(
            buildInvocationScopeId.getId().asString(),
            logger,
            deleter,
            fileSystemAccess,
            bufferProvider,
            stringInterner,
            buildOperationExecutor,
            new GZipNextGenBuildCacheAccess(
                new DefaultNextGenBuildCacheAccess(
                    local,
                    remote,
                    bufferProvider,
                    executorFactory,
                    logger
                ),
                bufferProvider
            )
        );
    }

    private static RemoteNextGenBuildCacheServiceHandler resolveRemoteService(@Nullable DescribedBuildCacheService<? extends BuildCache, ? extends BuildCacheService> describedService) {
        return describedService != null && describedService.config.isEnabled()
            ? new DefaultRemoteNextGenBuildCacheServiceHandler(makeCompatible(describedService.service), describedService.config.isPush())
            : DISABLED_BUILD_CACHE_SERVICE_HANDLER;
    }

    /**
     * Wraps a legacy {@link BuildCacheService} in a {@link NextGenBuildCacheService}.
     */
    private static NextGenBuildCacheService makeCompatible(BuildCacheService service) {
        if (service instanceof NextGenBuildCacheService) {
            return (NextGenBuildCacheService) service;
        }
        return new NextGenBuildCacheService() {
            @Override
            public boolean contains(BuildCacheKey key) {
                return load(key, __ -> {});
            }

            @Override
            public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                return service.load(key, reader);
            }

            @Override
            public void store(BuildCacheKey key, BuildCacheEntryWriter legacyWriter) throws BuildCacheException {
                service.store(key, legacyWriter);
            }

            @Override
            public void store(BuildCacheKey key, NextGenWriter writer) throws BuildCacheException {
                service.store(key, writer);
            }

            @Override
            public void close() throws IOException {
                service.close();
            }
        };
    }

    private static final RemoteNextGenBuildCacheServiceHandler DISABLED_BUILD_CACHE_SERVICE_HANDLER = new RemoteNextGenBuildCacheServiceHandler() {
        @Override
        public boolean canLoad() {
            return false;
        }

        @Override
        public boolean canStore() {
            return false;
        }

        @Override
        public void disableOnError() {
            // Already disabled
        }

        @Override
        public boolean contains(BuildCacheKey key) {
            return false;
        }

        @Override
        public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
            return false;
        }

        @Override
        public void store(BuildCacheKey key, NextGenBuildCacheService.NextGenWriter writer) throws BuildCacheException {
        }

        @Override
        public void close() {
        }
    };

    private static class DefaultRemoteNextGenBuildCacheServiceHandler implements RemoteNextGenBuildCacheServiceHandler {
        private final NextGenBuildCacheService service;
        private final boolean pushEnabled;
        private volatile boolean disabledOnError;

        public DefaultRemoteNextGenBuildCacheServiceHandler(NextGenBuildCacheService service, boolean pushEnabled) {
            this.service = service;
            this.pushEnabled = pushEnabled;
        }

        @Override
        public boolean canLoad() {
            return !disabledOnError;
        }

        @Override
        public boolean canStore() {
            return !disabledOnError && pushEnabled;
        }

        @Override
        public void disableOnError() {
            this.disabledOnError = true;
        }

        @Override
        public boolean contains(BuildCacheKey key) {
            return canLoad() && service.contains(key);
        }

        @Override
        public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
            return canLoad() && service.load(key, reader);
        }

        @Override
        public void store(BuildCacheKey key, NextGenBuildCacheService.NextGenWriter writer) throws BuildCacheException {
            if (canStore()) {
                service.store(key, writer);
            }
        }

        @Override
        public void close() throws IOException {
            service.close();
        }
    }
}
