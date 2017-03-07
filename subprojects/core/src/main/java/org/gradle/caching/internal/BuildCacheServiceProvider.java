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

package org.gradle.caching.internal;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.StartParameter;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.internal.Cast;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class BuildCacheServiceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheServiceProvider.class);
    private static final int MAX_ERROR_COUNT_FOR_BUILD_CACHE = 3;

    private final BuildCacheConfigurationInternal buildCacheConfiguration;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Instantiator instantiator;
    private final StartParameter startParameter;
    private final TemporaryFileProvider temporaryFileProvider;

    @Inject
    public BuildCacheServiceProvider(BuildCacheConfigurationInternal buildCacheConfiguration, StartParameter startParameter, Instantiator instantiator, BuildOperationExecutor buildOperationExecutor, TemporaryFileProvider temporaryFileProvider) {
        this.buildCacheConfiguration = buildCacheConfiguration;
        this.startParameter = startParameter;
        this.instantiator = instantiator;
        this.buildOperationExecutor = buildOperationExecutor;
        this.temporaryFileProvider = temporaryFileProvider;
    }

    public BuildCacheService createBuildCacheService() {
        if (!startParameter.isBuildCacheEnabled()) {
            return new NoOpBuildCacheService();
        }

        SingleMessageLogger.incubatingFeatureUsed("Build cache");

        DirectoryBuildCache local = buildCacheConfiguration.getLocal();
        BuildCache remote = buildCacheConfiguration.getRemote();

        BuildCacheService buildCacheService;
        if (local.isEnabled()) {
            if (remote != null && remote.isEnabled()) {
                // Have both local and remote, composite build cache
                buildCacheService = createDispatchingBuildCacheService(local, remote);
            } else {
                // Only have a local build cache
                buildCacheService = createStandaloneLocalBuildService(local);
            }
        } else if (remote != null && remote.isEnabled()) {
            // Only have a remote build cache
            buildCacheService = createStandaloneRemoteBuildService(remote);
        } else {
            LOGGER.warn("Task output caching is enabled, but no build caches are configured or enabled.");
            return new NoOpBuildCacheService();
        }

        // TODO Remove this when the system properties are removed
        if (buildCacheConfiguration.isPullDisabled() || buildCacheConfiguration.isPushDisabled()) {
            if (buildCacheConfiguration.isPushDisabled()) {
                LOGGER.warn("Pushing to any build cache is globally disabled.");
            }
            if (buildCacheConfiguration.isPullDisabled()) {
                LOGGER.warn("Pulling from any build cache is globally disabled.");
            }
            buildCacheService = new PushOrPullPreventingBuildCacheServiceDecorator(
                buildCacheConfiguration.isPushDisabled(),
                buildCacheConfiguration.isPullDisabled(),
                buildCacheService
            );
        }

        return buildCacheService;
    }

    private BuildCacheService createDispatchingBuildCacheService(DirectoryBuildCache local, BuildCache remote) {
        return new DispatchingBuildCacheService(
            createStandaloneLocalBuildService(local), local.isPush(),
            createStandaloneRemoteBuildService(remote), remote.isPush(),
            temporaryFileProvider
        );
    }

    private BuildCacheService createStandaloneLocalBuildService(DirectoryBuildCache local) {
        return createDecoratedBuildCacheService("local", local);
    }

    private BuildCacheService createStandaloneRemoteBuildService(BuildCache remote) {
        return createDecoratedBuildCacheService("remote", remote);
    }

    @VisibleForTesting
    BuildCacheService createDecoratedBuildCacheService(String role, BuildCache buildCache) {
        BuildCacheService buildCacheService = createRawBuildCacheService(buildCache);
        LOGGER.warn("Using {} as {} cache, push is {}.", buildCacheService.getDescription(), role, buildCache.isPush() ? "enabled" : "disabled");
        return decorateBuildCacheService(role, !buildCache.isPush(), buildCacheService);
    }

    private <T extends BuildCache> BuildCacheService createRawBuildCacheService(final T configuration) {
        Class<? extends BuildCacheServiceFactory<T>> buildCacheServiceFactoryType = Cast.uncheckedCast(buildCacheConfiguration.getBuildCacheServiceFactoryType(configuration.getClass()));
        return instantiator.newInstance(buildCacheServiceFactoryType).createBuildCacheService(configuration);
    }

    private BuildCacheService decorateBuildCacheService(String role, boolean pushDisabled, BuildCacheService buildCacheService) {
        BuildCacheService decoratedService = buildCacheService;
        decoratedService = new BuildOperationFiringBuildCacheServiceDecorator(role, buildOperationExecutor, decoratedService);
        decoratedService = new LoggingBuildCacheServiceDecorator(role, decoratedService);
        if (pushDisabled) {
            decoratedService = new PushOrPullPreventingBuildCacheServiceDecorator(true, false, decoratedService);
        }
        decoratedService = new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(MAX_ERROR_COUNT_FOR_BUILD_CACHE, decoratedService);
        return decoratedService;
    }
}
