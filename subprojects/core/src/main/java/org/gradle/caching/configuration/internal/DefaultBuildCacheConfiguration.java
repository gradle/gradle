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
import org.gradle.api.Action;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.configuration.AbstractBuildCache;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.BuildCacheServiceBuilder;
import org.gradle.caching.configuration.LocalBuildCache;
import org.gradle.caching.internal.PullPreventingBuildCacheServiceDecorator;
import org.gradle.caching.internal.PushPreventingBuildCacheServiceDecorator;
import org.gradle.internal.Actions;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultBuildCacheConfiguration implements BuildCacheConfigurationInternal {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheConfiguration.class);

    private static final BuildCacheServiceBuilder<BuildCache> NON_CONFIGURABLE_REMOTE_BUILDER = new BuildCacheServiceBuilder<BuildCache>() {
        @Override
        public BuildCache getConfiguration() {
            return new BuildCache() {
                @Override
                public boolean isEnabled() {
                    return false;
                }

                @Override
                public boolean isPush() {
                    return false;
                }

                @Override
                public void setEnabled(boolean enabled) {
                    throw new IllegalStateException("Remote build cache must be specified before configuration can be changed");
                }

                @Override
                public void setPush(boolean enabled) {
                    throw new IllegalStateException("Remote build cache must be specified before configuration can be changed");
                }
            };
        }

        @Override
        public BuildCacheService build() {
            return BuildCacheService.NO_OP;
        }
    };
    private final BuildCacheServiceBuilder<? extends LocalBuildCache> local;
    private final StartParameter startParameter;
    private final BuildCacheServiceFactoryRegistry buildCacheServiceFactoryRegistry;
    private BuildCacheServiceBuilder<?> remote = NON_CONFIGURABLE_REMOTE_BUILDER;

    public DefaultBuildCacheConfiguration(StartParameter startParameter, BuildCacheServiceFactoryRegistry buildCacheServiceFactoryRegistry) {
        this.startParameter = startParameter;
        this.buildCacheServiceFactoryRegistry = buildCacheServiceFactoryRegistry;
        this.local = buildCacheServiceFactoryRegistry.createServiceBuilder(LocalBuildCache.class);
    }

    @Override
    public LocalBuildCache getLocal() {
        return local.getConfiguration();
    }

    @Override
    public void local(Action<? super LocalBuildCache> configuration) {
        LocalBuildCache cfg = local.getConfiguration();
        configuration.execute(cfg);
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type) {
        return remote(type, Actions.doNothing());
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type, Action<? super T> configuration) {
        BuildCacheServiceBuilder<? extends T> builder = buildCacheServiceFactoryRegistry.createServiceBuilder(type);
        T config = builder.getConfiguration();
        configuration.execute(config);
        this.remote = builder;
        return config;
    }

    @Override
    public BuildCache getRemote() {
        return remote.getConfiguration();
    }

    @Override
    public void remote(Action<? super BuildCache> configuration) {
        configuration.execute(remote.getConfiguration());
    }

    @Override
    public void remote(final BuildCacheService cacheService) {
        this.remote = new BuildCacheServiceBuilder<BuildCache>() {
            @Override
            public BuildCache getConfiguration() {
                return new AbstractBuildCache() {};
            }

            @Override
            public BuildCacheService build() {
                return cacheService;
            }
        };
    }

    @Override
    public BuildCacheService build() {
        if (remote.getConfiguration().isEnabled()) {
            return filterPushAndPullWhenNeeded(startParameter, remote);
        } else if (local.getConfiguration().isEnabled()) {
            return filterPushAndPullWhenNeeded(startParameter, local);
        } else {
            return BuildCacheService.NO_OP;
        }
    }

    private static BuildCacheService filterPushAndPullWhenNeeded(StartParameter startParameter, BuildCacheServiceBuilder<? extends BuildCache> builder) {
        boolean pushDisabled = !builder.getConfiguration().isPush()
            || isDisabled(startParameter, "org.gradle.cache.tasks.push");
        boolean pullDisabled = isDisabled(startParameter, "org.gradle.cache.tasks.pull");

        BuildCacheService service;
        if (pushDisabled) {
            if (pullDisabled) {
                LOGGER.warn("Neither pushing nor pulling from cache is enabled");
                service = BuildCacheService.NO_OP;
            } else {
                service = new PushPreventingBuildCacheServiceDecorator(builder.build());
                SingleMessageLogger.incubatingFeatureUsed("Retrieving task output from " + service.getDescription());
            }
        } else if (pullDisabled) {
            service = new PullPreventingBuildCacheServiceDecorator(builder.build());
            SingleMessageLogger.incubatingFeatureUsed("Pushing task output to " + service.getDescription());
        } else {
            service = builder.build();
            SingleMessageLogger.incubatingFeatureUsed("Using " + service.getDescription());
        }

        return service;
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
}
