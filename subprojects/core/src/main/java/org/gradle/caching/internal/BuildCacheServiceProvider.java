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
import org.gradle.caching.local.LocalBuildCache;
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
        LocalBuildCache local = buildCacheConfiguration.getLocal();
        BuildCache remote = buildCacheConfiguration.getRemote();
        // Have both local and remote, composite build cache
        if (local.isEnabled() && remote != null && remote.isEnabled()) {
            BuildCacheService buildCacheService = createDispatchingBuildCacheService(local, remote);
            emitUsageMessage(local.isPush() || remote.isPush(), buildCacheService);
            return buildCacheService;
        }

        // Only have a local build cache
        if (local.isEnabled()) {
            BuildCacheService buildCacheService = createDecoratedBuildCacheService(local);
            emitUsageMessage(local.isPush(), buildCacheService);
            return buildCacheService;
        }

        // Only have a remote build cache
        if (remote != null && remote.isEnabled()) {
            BuildCacheService buildCacheService = createDecoratedBuildCacheService(remote);
            emitUsageMessage(remote.isPush(), buildCacheService);
            return buildCacheService;
        }

        LOGGER.warn("Task output caching is enabled, but no build caches are configured or enabled.");
        return new NoOpBuildCacheService();
    }

    private void emitUsageMessage(boolean pushEnabled, BuildCacheService buildCacheService) {
        if (!pushEnabled || buildCacheConfiguration.isPushDisabled()) {
            if (buildCacheConfiguration.isPullDisabled()) {
                LOGGER.warn("No build caches are allowed to push or pull task outputs, but task output caching is enabled.");
            } else {
                SingleMessageLogger.incubatingFeatureUsed("Retrieving task output from " + buildCacheService.getDescription());
            }
        } else if (buildCacheConfiguration.isPullDisabled()) {
            SingleMessageLogger.incubatingFeatureUsed("Pushing task output to " + buildCacheService.getDescription());
        } else {
            SingleMessageLogger.incubatingFeatureUsed("Using " + buildCacheService.getDescription());
        }
    }

    private BuildCacheService createDispatchingBuildCacheService(LocalBuildCache local, BuildCache remote) {
        return new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(
            MAX_ERROR_COUNT_FOR_BUILD_CACHE,
            new DispatchingBuildCacheService(
                createDecoratedBuildCacheService(local), local.isPush(),
                createDecoratedBuildCacheService(remote), remote.isPush(),
                temporaryFileProvider
            )
        );
    }

    @VisibleForTesting
    BuildCacheService createDecoratedBuildCacheService(BuildCache buildCache) {
        BuildCacheService buildCacheService = createRawBuildCacheService(buildCache);
        return decorateBuildCacheService(!buildCache.isPush(), buildCacheService);
    }

    private <T extends BuildCache> BuildCacheService createRawBuildCacheService(final T configuration) {
        Class<? extends BuildCacheServiceFactory<T>> buildCacheServiceFactoryType = Cast.uncheckedCast(buildCacheConfiguration.getBuildCacheServiceFactoryType(configuration.getClass()));
        return instantiator.newInstance(buildCacheServiceFactoryType).createBuildCacheService(configuration);
    }

    private BuildCacheService decorateBuildCacheService(boolean pushDisabled, BuildCacheService buildCacheService) {
        return new PushOrPullPreventingBuildCacheServiceDecorator(
            pushDisabled || buildCacheConfiguration.isPushDisabled(),
            buildCacheConfiguration.isPullDisabled(),
            new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(
                MAX_ERROR_COUNT_FOR_BUILD_CACHE,
                new LoggingBuildCacheServiceDecorator(
                    new BuildOperationFiringBuildCacheServiceDecorator(
                        buildOperationExecutor,
                        buildCacheService
                    )
                )
            )
        );
    }
}
