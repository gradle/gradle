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

package org.gradle.caching.internal.controller;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.FinalizeBuildCacheConfigurationBuildOperationType;
import org.gradle.caching.internal.controller.service.BuildCacheServiceRole;
import org.gradle.caching.internal.controller.service.BuildCacheServicesConfiguration;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BuildCacheControllerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildCacheControllerFactory.class);

    public enum BuildCacheMode {
        ENABLED, DISABLED
    }

    public enum RemoteAccessMode {
        ONLINE, OFFLINE
    }

    public static BuildCacheController create(
        final BuildOperationExecutor buildOperationExecutor,
        final Path buildIdentityPath,
        final File gradleUserHomeDir,
        final BuildCacheConfigurationInternal buildCacheConfiguration,
        final BuildCacheMode buildCacheState,
        final RemoteAccessMode remoteAccessMode,
        final boolean logStackTraces,
        final Instantiator instantiator
    ) {
        return buildOperationExecutor.call(new CallableBuildOperation<BuildCacheController>() {
            @Override
            public BuildCacheController call(BuildOperationContext context) {
                if (buildCacheState == BuildCacheMode.DISABLED) {
                    context.setResult(ResultImpl.disabled());
                    return NoOpBuildCacheController.INSTANCE;
                }

                BuildCache local = buildCacheConfiguration.getLocal();
                BuildCache remote = buildCacheConfiguration.getRemote();

                boolean localEnabled = local != null && local.isEnabled();
                boolean remoteEnabled = remote != null && remote.isEnabled();

                if (remoteEnabled && remoteAccessMode == RemoteAccessMode.OFFLINE) {
                    remoteEnabled = false;
                    LOGGER.warn("Remote build cache is disabled when running with --offline.");
                }

                DescribedBuildCacheService localDescribedService = localEnabled
                    ? createBuildCacheService(local, BuildCacheServiceRole.LOCAL, buildIdentityPath, buildCacheConfiguration, instantiator)
                    : null;

                DescribedBuildCacheService remoteDescribedService = remoteEnabled
                    ? createBuildCacheService(remote, BuildCacheServiceRole.REMOTE, buildIdentityPath, buildCacheConfiguration, instantiator)
                    : null;

                context.setResult(new ResultImpl(
                    true,
                    local != null && local.isEnabled(),
                    remote != null && remote.isEnabled() && remoteAccessMode == RemoteAccessMode.ONLINE,
                    localDescribedService == null ? null : localDescribedService.description,
                    remoteDescribedService == null ? null : remoteDescribedService.description
                ));

                if (!localEnabled && !remoteEnabled) {
                    LOGGER.warn("Task output caching is enabled, but no build caches are configured or enabled.");
                    return NoOpBuildCacheController.INSTANCE;
                } else {
                    BuildCacheServicesConfiguration config = toConfiguration(
                        local, localDescribedService == null ? null : localDescribedService.service,
                        remote, remoteDescribedService == null ? null : remoteDescribedService.service
                    );

                    return new DefaultBuildCacheController(
                        config,
                        buildOperationExecutor,
                        gradleUserHomeDir,
                        logStackTraces
                    );
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Finalize build cache configuration")
                    .details(DetailsImpl.INSTANCE);
            }
        });
    }

    private static BuildCacheServicesConfiguration toConfiguration(BuildCache local, BuildCacheService localService, BuildCache remote, BuildCacheService remoteService) {
        boolean remotePush = remote != null && remote.isPush();
        boolean localPush = local != null && local.isPush();
        return new BuildCacheServicesConfiguration(localService, localPush, remoteService, remotePush);
    }


    private static <T extends BuildCache> DescribedBuildCacheService createBuildCacheService(final T configuration, BuildCacheServiceRole role, Path buildIdentityPath, BuildCacheConfigurationInternal buildCacheConfiguration, Instantiator instantiator) {
        Class<? extends BuildCacheServiceFactory<T>> castFactoryType = Cast.uncheckedCast(
            buildCacheConfiguration.getBuildCacheServiceFactoryType(configuration.getClass())
        );

        BuildCacheServiceFactory<T> factory = instantiator.newInstance(castFactoryType);
        Describer describer = new Describer();
        BuildCacheService service = factory.createBuildCacheService(configuration, describer);
        ImmutableSortedMap<String, String> config = ImmutableSortedMap.copyOf(describer.configParams);
        BuildCacheDescription description = new BuildCacheDescription(configuration, describer.type, config);

        logConfig(buildIdentityPath, role, description);

        return new DescribedBuildCacheService(service, description);
    }

    private static void logConfig(Path buildIdentityPath, BuildCacheServiceRole role, BuildCacheDescription description) {
        if (LOGGER.isInfoEnabled()) {
            StringBuilder config = new StringBuilder();
            boolean pullOnly = !description.isPush();
            if (!description.config.isEmpty() || pullOnly) {
                Map<String, String> configMap;
                if (pullOnly) {
                    configMap = new LinkedHashMap<String, String>();
                    // Pull-only always comes first
                    configMap.put("pull-only", null);
                    configMap.putAll(description.config);
                } else {
                    configMap = description.config;
                }
                config.append(" (");
                Joiner.on(", ").appendTo(config, Iterables.transform(configMap.entrySet(), new Function<Map.Entry<String, String>, String>() {
                    @Override
                    public String apply(Map.Entry<String, String> input) {
                        if (input.getValue() == null) {
                            return input.getKey();
                        } else {
                            return input.getKey() + " = " + input.getValue();
                        }
                    }
                }));
                config.append(")");
            }

            String buildDescription;
            if (buildIdentityPath.equals(Path.ROOT)) {
                buildDescription = "the root build";
            } else {
                buildDescription = "build '" + buildIdentityPath + "'";
            }

            LOGGER.info("Using {} {} build cache for {}{}.",
                role.getDisplayName(),
                description.type == null ? description.className : description.type,
                buildDescription,
                config
            );
        }
    }

    private static final class BuildCacheDescription implements FinalizeBuildCacheConfigurationBuildOperationType.Result.BuildCacheDescription {

        private final String className;
        private final boolean push;
        private final String type;
        private final ImmutableSortedMap<String, String> config;

        private BuildCacheDescription(BuildCache buildCache, String type, ImmutableSortedMap<String, String> config) {
            this.className = GeneratedSubclasses.unpack(buildCache.getClass()).getName();
            this.push = buildCache.isPush();
            this.type = type;
            this.config = config;
        }

        public String getClassName() {
            return className;
        }

        public boolean isPush() {
            return push;
        }

        public String getType() {
            return type;
        }

        public Map<String, String> getConfig() {
            return config;
        }
    }

    private static class Describer implements BuildCacheServiceFactory.Describer {

        private String type;
        private Map<String, String> configParams = new HashMap<String, String>();

        @Override
        public BuildCacheServiceFactory.Describer type(String type) {
            if (type == null) {
                throw new IllegalArgumentException("'type' argument cannot be null");
            }

            this.type = type;
            return this;
        }

        @Override
        public BuildCacheServiceFactory.Describer config(String name, String value) {
            if (name == null) {
                throw new IllegalArgumentException("'name' argument cannot be null");
            }
            if (value == null) {
                throw new IllegalArgumentException("'value' argument cannot be null");
            }

            configParams.put(name, value);
            return this;
        }
    }

    private static class DescribedBuildCacheService {

        private final BuildCacheService service;
        private final BuildCacheDescription description;

        private DescribedBuildCacheService(BuildCacheService service, BuildCacheDescription description) {
            this.service = service;
            this.description = description;
        }
    }

    private static class DetailsImpl implements FinalizeBuildCacheConfigurationBuildOperationType.Details {
        public static final FinalizeBuildCacheConfigurationBuildOperationType.Details INSTANCE = new DetailsImpl();

        private DetailsImpl() {
        }
    }

    private static class ResultImpl implements FinalizeBuildCacheConfigurationBuildOperationType.Result {

        private final boolean enabled;
        private final boolean localEnabled;
        private final BuildCacheDescription local;
        private final boolean remoteEnabled;
        private final BuildCacheDescription remote;

        ResultImpl(boolean enabled, boolean localEnabled, boolean remoteEnabled, @Nullable BuildCacheDescription local, @Nullable BuildCacheDescription remote) {
            this.enabled = enabled;
            this.localEnabled = localEnabled;
            this.remoteEnabled = remoteEnabled;
            this.local = local;
            this.remote = remote;
        }

        static FinalizeBuildCacheConfigurationBuildOperationType.Result disabled() {
            return new ResultImpl(false, false, false, null, null);
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean isLocalEnabled() {
            return localEnabled;
        }

        @Override
        public boolean isRemoteEnabled() {
            return remoteEnabled;
        }

        @Override
        @Nullable
        public BuildCacheDescription getLocal() {
            return local;
        }

        @Override
        @Nullable
        public BuildCacheDescription getRemote() {
            return remote;
        }

    }
}
