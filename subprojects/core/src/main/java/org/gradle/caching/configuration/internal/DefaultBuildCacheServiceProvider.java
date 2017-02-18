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

package org.gradle.caching.configuration.internal;

import org.gradle.StartParameter;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.BuildCacheConfiguration;
import org.gradle.caching.configuration.BuildCacheServiceFactory;
import org.gradle.caching.internal.BuildOperationFiringBuildCacheServiceDecorator;
import org.gradle.caching.internal.LenientBuildCacheServiceDecorator;
import org.gradle.caching.internal.LoggingBuildCacheServiceDecorator;
import org.gradle.caching.internal.PullPreventingBuildCacheServiceDecorator;
import org.gradle.caching.internal.PushPreventingBuildCacheServiceDecorator;
import org.gradle.caching.internal.ShortCircuitingErrorHandlerBuildCacheServiceDecorator;
import org.gradle.internal.Cast;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

// TODO: Add some coverage
public class DefaultBuildCacheServiceProvider implements BuildCacheServiceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheServiceProvider.class);
    private final BuildCacheConfiguration buildCacheConfiguration;
    private final StartParameter startParameter;
    private final BuildCacheServiceFactoryRegistry buildCacheServiceFactoryRegistry;
    private final BuildOperationExecutor buildOperationExecutor;

    public DefaultBuildCacheServiceProvider(BuildCacheConfiguration buildCacheConfiguration, StartParameter startParameter, BuildCacheServiceFactoryRegistry buildCacheServiceFactoryRegistry, BuildOperationExecutor buildOperationExecutor) {
        this.buildCacheConfiguration = buildCacheConfiguration;
        this.startParameter = startParameter;
        this.buildCacheServiceFactoryRegistry = buildCacheServiceFactoryRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public BuildCacheService getBuildCacheService() {
        if (startParameter.isTaskOutputCacheEnabled()) {
            return new LenientBuildCacheServiceDecorator(
                new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(
                    3,
                    new LoggingBuildCacheServiceDecorator(
                        new BuildOperationFiringBuildCacheServiceDecorator(
                            buildOperationExecutor,
                            build()
                        )
                    )
                )
            );
        } else {
            return new NoOpBuildCacheService();
        }
    }

    private BuildCacheService build() {
        BuildCache remote = buildCacheConfiguration.getRemote();
        BuildCache local = buildCacheConfiguration.getLocal();
        if (remote != null && remote.isEnabled()) {
            return filterPushAndPullWhenNeeded(startParameter, remote);
        } else if (local.isEnabled()) {
            return filterPushAndPullWhenNeeded(startParameter, local);
        } else {
            return new NoOpBuildCacheService();
        }
    }

    // TODO: Drop these system properties
    private BuildCacheService filterPushAndPullWhenNeeded(StartParameter startParameter, BuildCache configuration) {
        boolean pushDisabled = !configuration.isPush()
            || isDisabled(startParameter, "org.gradle.cache.tasks.push");
        boolean pullDisabled = isDisabled(startParameter, "org.gradle.cache.tasks.pull");
        return filterPushAndPullWhenNeeded(pushDisabled, pullDisabled, configuration);
    }

    private BuildCacheService filterPushAndPullWhenNeeded(boolean pushDisabled, boolean pullDisabled, BuildCache configuration) {
        BuildCacheService service;
        if (pushDisabled) {
            if (pullDisabled) {
                LOGGER.warn("Neither pushing nor pulling from cache is enabled");
                service = new NoOpBuildCacheService();
            } else {
                service = new PushPreventingBuildCacheServiceDecorator(createUnderlyingBuildCacheService(configuration));
                SingleMessageLogger.incubatingFeatureUsed("Retrieving task output from " + service.getDescription());
            }
        } else if (pullDisabled) {
            service = new PullPreventingBuildCacheServiceDecorator(createUnderlyingBuildCacheService(configuration));
            SingleMessageLogger.incubatingFeatureUsed("Pushing task output to " + service.getDescription());
        } else {
            service = createUnderlyingBuildCacheService(configuration);
            SingleMessageLogger.incubatingFeatureUsed("Using " + service.getDescription());
        }
        return service;
    }

    private <T extends BuildCache> BuildCacheService createUnderlyingBuildCacheService(T configuration) {
        BuildCacheServiceFactory<T> buildCacheServiceFactory = Cast.uncheckedCast(buildCacheServiceFactoryRegistry.getBuildCacheServiceFactory(configuration.getClass()));
        return buildCacheServiceFactory.build(configuration);
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
    };
}
