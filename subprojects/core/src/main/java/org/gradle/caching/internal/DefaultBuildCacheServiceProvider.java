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

import org.gradle.StartParameter;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
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

import java.io.IOException;

public class DefaultBuildCacheServiceProvider implements BuildCacheServiceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheServiceProvider.class);

    private final BuildCacheConfigurationInternal buildCacheConfiguration;
    private final StartParameter startParameter;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Instantiator instantiator;

    public DefaultBuildCacheServiceProvider(BuildCacheConfigurationInternal buildCacheConfiguration, StartParameter startParameter, Instantiator instantiator, BuildOperationExecutor buildOperationExecutor) {
        this.buildCacheConfiguration = buildCacheConfiguration;
        this.startParameter = startParameter;
        this.instantiator = instantiator;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public BuildCacheService create() {
        BuildCache configuration = selectConfiguration();
        if (configuration != null) {
            // TODO: Drop these system properties
            boolean pushDisabled = !configuration.isPush()
                || isDisabled(startParameter, "org.gradle.cache.tasks.push");
            boolean pullDisabled = isDisabled(startParameter, "org.gradle.cache.tasks.pull");

            BuildCacheService buildCacheService = new LenientBuildCacheServiceDecorator(
                new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(
                    3,
                    new LoggingBuildCacheServiceDecorator(
                        new PushOrPullPreventingBuildCacheServiceDecorator(
                            pushDisabled,
                            pullDisabled,
                            new BuildOperationFiringBuildCacheServiceDecorator(
                                buildOperationExecutor,
                                createBuildCacheService(configuration)
                            )
                        )
                    )
                )
            );

            emitUsageMessage(pushDisabled, pullDisabled, buildCacheService);
            return buildCacheService;
        } else {
            return new NoOpBuildCacheService();
        }
    }

    private BuildCache selectConfiguration() {
        if (startParameter.isTaskOutputCacheEnabled()) {
            BuildCache remote = buildCacheConfiguration.getRemote();
            BuildCache local = buildCacheConfiguration.getLocal();
            if (remote != null && remote.isEnabled()) {
                return remote;
            } else if (local.isEnabled()) {
                return local;
            }
            LOGGER.warn("Task output caching is enabled, but no build caches are configured or enabled.");
        }
        return null;
    }

    private <T extends BuildCache> BuildCacheService createBuildCacheService(final T configuration) {
        Class<? extends BuildCacheServiceFactory<T>> buildCacheServiceFactoryType = Cast.uncheckedCast(buildCacheConfiguration.getBuildCacheServiceFactoryType(configuration.getClass()));
        return instantiator.newInstance(buildCacheServiceFactoryType).build(configuration);
    }

    private void emitUsageMessage(boolean pushDisabled, boolean pullDisabled, BuildCacheService buildCacheService) {
        if (pushDisabled) {
            if (pullDisabled) {
                LOGGER.warn("No build caches are allowed to push or pull task outputs, but task output caching is enabled.");
            } else {
                SingleMessageLogger.incubatingFeatureUsed("Retrieving task output from " + buildCacheService.getDescription());
            }
        } else if (pullDisabled) {
            SingleMessageLogger.incubatingFeatureUsed("Pushing task output to " + buildCacheService.getDescription());
        } else {
            SingleMessageLogger.incubatingFeatureUsed("Using " + buildCacheService.getDescription());
        }
    }

    private static boolean isDisabled(StartParameter startParameter, String property) {
        String value = startParameter.getSystemPropertiesArgs().get(property);
        if (value == null) {
            value = System.getProperty(property);
        }
        if (value == null) {
            return false;
        }
        value = value.toLowerCase().trim();
        return value.equals("false");
    }

    private static class NoOpBuildCacheService implements BuildCacheService {
        @Override
        public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
            return false;
        }

        @Override
        public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        }

        @Override
        public String getDescription() {
            return "NO-OP build cache";
        }

        @Override
        public void close() throws IOException {
            // Do nothing
        }
    }
}
