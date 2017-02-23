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
import org.gradle.api.GradleException;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DefaultBuildCacheServiceProvider implements BuildCacheServiceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheServiceProvider.class);

    private final BuildCacheConfigurationInternal buildCacheConfiguration;
    private final StartParameter startParameter;
    private final BuildCacheServiceInstantiator instantiator;

    public DefaultBuildCacheServiceProvider(BuildCacheConfigurationInternal buildCacheConfiguration, StartParameter startParameter, BuildCacheServiceInstantiator instantiator) {
        this.buildCacheConfiguration = buildCacheConfiguration;
        this.startParameter = startParameter;
        this.instantiator = instantiator;
    }

    @Override
    public BuildCacheService create() {
        List<BuildCache> configurations = enabledConfigurations();
        if (!configurations.isEmpty()) {
            return createDecoratedService(configurations);
        } else {
            return new NoOpBuildCacheService();
        }
    }

    private List<BuildCache> enabledConfigurations() {
        List<BuildCache> configurations = new ArrayList<BuildCache>();
        if (startParameter.isTaskOutputCacheEnabled()) {
            addIfEnabled(configurations, buildCacheConfiguration.getLocal());
            addIfEnabled(configurations, buildCacheConfiguration.getRemote());
            if (configurations.isEmpty()) {
                LOGGER.warn("Task output caching is enabled, but no build caches are configured or enabled.");
            }
        }
        return configurations;
    }

    private void addIfEnabled(List<BuildCache> configurations, BuildCache configuration) {
        if (configuration != null && configuration.isEnabled()) {
            configurations.add(configuration);
        }
    }

    private BuildCacheService createDecoratedService(List<BuildCache> configurations) {
        // TODO: Drop these system properties
        boolean pullGloballyDisabled = isDisabled(startParameter, "org.gradle.cache.tasks.pull");
        boolean pushGloballyDisabled = isDisabled(startParameter, "org.gradle.cache.tasks.push");

        BuildCacheService pushToCache = null;
        List<BuildCacheService> services = new ArrayList<BuildCacheService>(configurations.size());
        for (BuildCache configuration : configurations) {
            boolean pushDisabled = !configuration.isPush() || pushGloballyDisabled;
            BuildCacheService buildCacheService = instantiator.createBuildCacheService(
                configuration,
                pullGloballyDisabled,
                pushDisabled);
            if (!pushDisabled) {
                if (pushToCache != null) {
                    throw new GradleException("It is only allowed to push to a remote or a local build cache, not to both. Disable push for one of the caches.");
                }
                pushToCache = buildCacheService;
            }
            services.add(buildCacheService);
        }
        boolean pushDisabled = pushToCache == null;

        BuildCacheService buildCacheService = CompositeBuildCacheService.create(pushToCache, services);

        emitUsageMessage(pushDisabled, pullGloballyDisabled, buildCacheService);
        return buildCacheService;
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
