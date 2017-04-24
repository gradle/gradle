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

        BuildCache local = buildCacheConfiguration.getLocal();
        BuildCache remote = buildCacheConfiguration.getRemote();

        boolean canUseRemoteBuildCache = remote != null && remote.isEnabled();

        if (canUseRemoteBuildCache && startParameter.isOffline()) {
            LOGGER.warn("Remote build cache is disabled when running with --offline.");
        }

        RoleAwareBuildCacheService buildCacheService;
        if (local.isEnabled()) {
            if (canUseRemoteBuildCache && !startParameter.isOffline()) {
                buildCacheService = createDispatchingBuildCacheService(local, remote);
            } else {
                buildCacheService = createStandaloneLocalBuildService(local);
            }
        } else if (canUseRemoteBuildCache && !startParameter.isOffline()) {
            buildCacheService = createStandaloneRemoteBuildService(remote);
        } else {
            LOGGER.warn("Task output caching is enabled, but no build caches are configured or enabled.");
            return new NoOpBuildCacheService();
        }

        return buildCacheService;
    }

    private RoleAwareBuildCacheService createDispatchingBuildCacheService(BuildCache local, BuildCache remote) {
        return new DispatchingBuildCacheService(
            createDecoratedBuildCacheService("local", local), local.isPush(),
            createDecoratedBuildCacheService("remote", remote), remote.isPush(),
            temporaryFileProvider
        );
    }

    private RoleAwareBuildCacheService createStandaloneLocalBuildService(BuildCache local) {
        return preventPushIfNecessary(createDecoratedBuildCacheService("local", local), local.isPush());
    }

    private RoleAwareBuildCacheService createStandaloneRemoteBuildService(BuildCache remote) {
        return preventPushIfNecessary(createDecoratedBuildCacheService("remote", remote), remote.isPush());
    }

    private RoleAwareBuildCacheService preventPushIfNecessary(RoleAwareBuildCacheService buildCacheService, boolean pushEnabled) {
        return pushEnabled
            ? buildCacheService
            : new PushOrPullPreventingBuildCacheServiceDecorator(true, false, buildCacheService);
    }

    @VisibleForTesting
    RoleAwareBuildCacheService createDecoratedBuildCacheService(String role, BuildCache buildCache) {
        RoleAwareBuildCacheService buildCacheService = new BuildCacheServiceWithRole(role, createRawBuildCacheService(buildCache));
        LOGGER.warn("Using {} as {} build cache, push is {}.", buildCacheService.getDescription(), role, buildCache.isPush() ? "enabled" : "disabled");
        buildCacheService = new BuildOperationFiringBuildCacheServiceDecorator(buildOperationExecutor, buildCacheService);
        buildCacheService = new LoggingBuildCacheServiceDecorator(buildCacheService);
        buildCacheService = new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(MAX_ERROR_COUNT_FOR_BUILD_CACHE, buildCacheService);
        return buildCacheService;
    }

    private <T extends BuildCache> BuildCacheService createRawBuildCacheService(final T configuration) {
        Class<? extends BuildCacheServiceFactory<T>> buildCacheServiceFactoryType = Cast.uncheckedCast(buildCacheConfiguration.getBuildCacheServiceFactoryType(configuration.getClass()));
        return instantiator.newInstance(buildCacheServiceFactoryType).createBuildCacheService(configuration);
    }

    private static class BuildCacheServiceWithRole extends ForwardingBuildCacheService implements RoleAwareBuildCacheService {
        private final String role;
        private final BuildCacheService delegate;

        public BuildCacheServiceWithRole(String role, BuildCacheService delegate) {
            this.role = role;
            this.delegate = delegate;
        }

        @Override
        protected BuildCacheService delegate() {
            return delegate;
        }

        @Override
        public String getRole() {
            return role;
        }
    }
}
