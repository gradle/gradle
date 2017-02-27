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
import org.gradle.api.GradleException;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class DefaultCompositeBuildCacheServiceFactory implements BuildCacheServiceFactory<CompositeBuildCache> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCompositeBuildCacheServiceFactory.class);

    private final BuildCacheConfigurationInternal buildCacheConfiguration;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Instantiator instantiator;

    @Inject
    public DefaultCompositeBuildCacheServiceFactory(BuildCacheConfigurationInternal buildCacheConfiguration, Instantiator instantiator, BuildOperationExecutor buildOperationExecutor) {
        this.buildCacheConfiguration = buildCacheConfiguration;
        this.buildOperationExecutor = buildOperationExecutor;
        this.instantiator = instantiator;
    }

    @Override
    public BuildCacheService createBuildCacheService(CompositeBuildCache compositeBuildCache) {
        if (compositeBuildCache.isEnabled()) {
            // Have both local and remote, composite build cache
            if (compositeBuildCache.getLocal().isEnabled() && compositeBuildCache.getRemote() != null && compositeBuildCache.getRemote().isEnabled()) {
                BuildCacheService buildCacheService = createCompositeBuildCacheService(compositeBuildCache);
                emitUsageMessage(compositeBuildCache, buildCacheService);
                return buildCacheService;
            }

            // Only have a local build cache
            if (compositeBuildCache.getLocal().isEnabled()) {
                BuildCache buildCache = compositeBuildCache.getLocal();
                BuildCacheService buildCacheService = createDecoratedBuildCacheService(buildCache);
                emitUsageMessage(buildCache, buildCacheService);
                return buildCacheService;
            }

            // Only have a remote build cache
            if (compositeBuildCache.getRemote() != null && compositeBuildCache.getRemote().isEnabled()) {
                BuildCache buildCache = compositeBuildCache.getRemote();
                BuildCacheService buildCacheService = createDecoratedBuildCacheService(buildCache);
                emitUsageMessage(buildCache, buildCacheService);
                return buildCacheService;
            }

            LOGGER.warn("Task output caching is enabled, but no build caches are configured or enabled.");
        }
        return new NoOpBuildCacheService();
    }

    private void emitUsageMessage(BuildCache buildCache, BuildCacheService buildCacheService) {
        if (!buildCache.isPush() || buildCacheConfiguration.isPushDisabled()) {
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

    private BuildCacheService createCompositeBuildCacheService(CompositeBuildCache compositeBuildCache) {
        if (compositeBuildCache.getLocal().isPush() && compositeBuildCache.getRemote().isPush()) {
            throw new GradleException("Gradle only allows one build cache to be configured to push at a time. Disable push for one of the build caches.");
        }
        boolean pushToRemote = compositeBuildCache.getRemote().isPush();
        return decorateBuildCacheService(
            !compositeBuildCache.isPush(),
            new CompositeBuildCacheService(
                createDecoratedBuildCacheService(compositeBuildCache.getLocal()),
                createDecoratedBuildCacheService(compositeBuildCache.getRemote()),
                pushToRemote)
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
            new LenientBuildCacheServiceDecorator(
                new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(
                    3,
                    new LoggingBuildCacheServiceDecorator(
                        new BuildOperationFiringBuildCacheServiceDecorator(
                            buildOperationExecutor,
                            buildCacheService
                        )
                    )
                )
            )
        );
    }
}
