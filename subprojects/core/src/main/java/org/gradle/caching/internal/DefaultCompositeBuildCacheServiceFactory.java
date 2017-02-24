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

import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.CollectionUtils;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public BuildCacheService build(CompositeBuildCache compositeBuildCache) {
        List<BuildCache> delegateBuildCaches = compositeBuildCache.getDelegates();
        if (delegateBuildCaches.isEmpty()) {
            return new NoOpBuildCacheService();
        }
        Map<BuildCache, BuildCacheService> services = createDecoratedServices(delegateBuildCaches);
        if (services.size() == 1) {
            Map.Entry<BuildCache, BuildCacheService> buildCache = CollectionUtils.single(services.entrySet());
            emitUsageMessage(buildCache.getKey(), buildCache.getValue());
            return buildCache.getValue();
        }
        BuildCacheService buildCacheService = createCompositeBuildCacheService(compositeBuildCache, services);
        emitUsageMessage(compositeBuildCache, buildCacheService);
        return buildCacheService;
    }

    private void emitUsageMessage(BuildCache buildCache, BuildCacheService buildCacheService) {
        if (!buildCache.isPush()) {
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

    private BuildCacheService createCompositeBuildCacheService(CompositeBuildCache compositeBuildCache, Map<BuildCache, BuildCacheService> services) {
        return decorateBuildCacheService(
            !compositeBuildCache.isPush(),
            new CompositeBuildCacheService(services.get(compositeBuildCache.getPushToCache()), services.values()));
    }

    private Map<BuildCache, BuildCacheService> createDecoratedServices(List<BuildCache> buildCaches) {
        Map<BuildCache, BuildCacheService> services = new LinkedHashMap<BuildCache, BuildCacheService>(buildCaches.size());
        for (BuildCache buildCache : buildCaches) {
            BuildCacheService buildCacheService = createDecoratedBuildCacheService(buildCache);
            services.put(buildCache, buildCacheService);
        }
        return services;
    }

    private BuildCacheService createDecoratedBuildCacheService(BuildCache buildCache) {
        BuildCacheService buildCacheService = createBuildCacheService(buildCache);
        return decorateBuildCacheService(!buildCache.isPush(), buildCacheService);
    }

    private BuildCacheService decorateBuildCacheService(boolean pushDisabled, BuildCacheService buildCacheService) {
        return new PushOrPullPreventingBuildCacheServiceDecorator(
            pushDisabled,
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

    private <T extends BuildCache> BuildCacheService createBuildCacheService(final T configuration) {
        Class<? extends BuildCacheServiceFactory<T>> buildCacheServiceFactoryType = Cast.uncheckedCast(buildCacheConfiguration.getBuildCacheServiceFactoryType(configuration.getClass()));
        return instantiator.newInstance(buildCacheServiceFactoryType).build(configuration);
    }
}
