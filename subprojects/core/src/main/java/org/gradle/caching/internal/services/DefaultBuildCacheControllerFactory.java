/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.DefaultBuildCacheController;
import org.gradle.caching.internal.controller.service.BuildCacheServicesConfiguration;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.caching.local.internal.DirectoryBuildCacheService;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.util.Path;

import javax.annotation.Nullable;

public class DefaultBuildCacheControllerFactory extends AbstractBuildCacheControllerFactory<DirectoryBuildCacheService> {

    private final TemporaryFileProvider temporaryFileProvider;
    private final BuildCacheEntryPacker packer;
    private final BuildOperationProgressEventEmitter buildOperationProgressEmitter;

    public DefaultBuildCacheControllerFactory(
        StartParameter startParameter,
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter buildOperationProgressEmitter,
        OriginMetadataFactory originMetadataFactory,
        StringInterner stringInterner,
        TemporaryFileProvider temporaryFileProvider,
        BuildCacheEntryPacker packer
    ) {
        super(
            startParameter,
            buildOperationRunner,
            originMetadataFactory,
            stringInterner
        );
        this.temporaryFileProvider = temporaryFileProvider;
        this.packer = packer;
        this.buildOperationProgressEmitter = buildOperationProgressEmitter;
    }

    @Override
    protected BuildCacheController doCreateController(
        Path buildPath,
        @Nullable DescribedBuildCacheService<DirectoryBuildCache, DirectoryBuildCacheService> localDescribedService,
        @Nullable DescribedBuildCacheService<BuildCache, BuildCacheService> remoteDescribedService
    ) {
        BuildCacheServicesConfiguration config = toConfiguration(
            buildPath,
            localDescribedService,
            remoteDescribedService
        );

        boolean logStackTraces = startParameter.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS;

        return new DefaultBuildCacheController(
            config,
            buildOperationRunner,
            buildOperationProgressEmitter,
            temporaryFileProvider::createTemporaryFile,
            logStackTraces,
            !Boolean.getBoolean(REMOTE_CONTINUE_ON_ERROR_PROPERTY),
            packer,
            originMetadataFactory,
            stringInterner
        );
    }

    private static BuildCacheServicesConfiguration toConfiguration(
        Path buildPath,
        @Nullable DescribedBuildCacheService<DirectoryBuildCache, DirectoryBuildCacheService> local,
        @Nullable DescribedBuildCacheService<BuildCache, BuildCacheService> remote
    ) {
        boolean localPush = local != null && local.config.getPush().get();
        boolean remotePush = remote != null && remote.config.getPush().get();
        return new BuildCacheServicesConfiguration(
            buildPath.getPath(),
            local != null ? local.service : null, localPush,
            remote != null ? remote.service : null, remotePush);
    }
}
