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

package org.gradle.caching.internal.services;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.caching.internal.FinalizeBuildCacheConfigurationBuildOperationType;
import org.gradle.caching.internal.controller.BuildCacheController;
import org.gradle.caching.internal.controller.DefaultBuildCacheController;
import org.gradle.caching.internal.controller.NoOpBuildCacheController;
import org.gradle.caching.internal.controller.service.BuildCacheServiceRole;
import org.gradle.caching.internal.controller.service.BuildCacheServicesConfiguration;
import org.gradle.caching.internal.origin.OriginMetadataFactory;
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.caching.local.internal.DirectoryBuildCacheService;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class BuildCacheControllerFactory {

    public static final String REMOTE_CONTINUE_ON_ERROR_PROPERTY = "org.gradle.unsafe.build-cache.remote-continue-on-error";

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
        final TemporaryFileProvider temporaryFileProvider,
        final BuildCacheConfigurationInternal buildCacheConfiguration,
        final BuildCacheMode buildCacheState,
        final RemoteAccessMode remoteAccessMode,
        final boolean logStackTraces,
        final boolean emitDebugLogging,
        final Instantiator instantiator,
        final FileSystemAccess fileSystemAccess,
        final BuildCacheEntryPacker packer,
        final OriginMetadataFactory originMetadataFactory,
        final StringInterner stringInterner
    ) {
        return buildOperationExecutor.call(new CallableBuildOperation<BuildCacheController>() {
            @Override
            public BuildCacheController call(BuildOperationContext context) {
                if (buildCacheState == BuildCacheMode.DISABLED) {
                    context.setResult(ResultImpl.disabled());
                    return NoOpBuildCacheController.INSTANCE;
                }

                DirectoryBuildCache local = buildCacheConfiguration.getLocal();
                BuildCache remote = buildCacheConfiguration.getRemote();

                boolean localEnabled = local.isEnabled();
                boolean remoteEnabled = remote != null && remote.isEnabled();

                if (remoteEnabled && remoteAccessMode == RemoteAccessMode.OFFLINE) {
                    remoteEnabled = false;
                    LOGGER.warn("Remote build cache is disabled when running with --offline.");
                }

                DescribedBuildCacheService<DirectoryBuildCache, DirectoryBuildCacheService> localDescribedService = localEnabled
                    ? createBuildCacheService(local, BuildCacheServiceRole.LOCAL, buildIdentityPath, buildCacheConfiguration, instantiator)
                    : null;

                DescribedBuildCacheService<BuildCache, BuildCacheService> remoteDescribedService = remoteEnabled
                    ? createBuildCacheService(remote, BuildCacheServiceRole.REMOTE, buildIdentityPath, buildCacheConfiguration, instantiator)
                    : null;

                context.setResult(new ResultImpl(
                    true,
                    local.isEnabled(),
                    remote != null && remote.isEnabled() && remoteAccessMode == RemoteAccessMode.ONLINE,
                    localDescribedService == null ? null : localDescribedService.description,
                    remoteDescribedService == null ? null : remoteDescribedService.description
                ));

                if (!localEnabled && !remoteEnabled) {
                    LOGGER.warn("Using the build cache is enabled, but no build caches are configured or enabled.");
                    return NoOpBuildCacheController.INSTANCE;
                } else {
                    BuildCacheServicesConfiguration config = toConfiguration(
                        localDescribedService,
                        remoteDescribedService
                    );

                    return new DefaultBuildCacheController(
                        config,
                        buildOperationExecutor,
                        temporaryFileProvider,
                        logStackTraces,
                        emitDebugLogging,
                        !Boolean.getBoolean(REMOTE_CONTINUE_ON_ERROR_PROPERTY),
                        fileSystemAccess,
                        packer,
                        originMetadataFactory,
                        stringInterner
                    );
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Finalize build cache configuration")
                    .details(new DetailsImpl(buildIdentityPath.getPath()));
            }
        });
    }

    private static BuildCacheServicesConfiguration toConfiguration(
        @Nullable DescribedBuildCacheService<DirectoryBuildCache, DirectoryBuildCacheService> local,
        @Nullable DescribedBuildCacheService<BuildCache, BuildCacheService> remote
    ) {
        boolean localPush = local != null && local.config.isPush();
        boolean remotePush = remote != null && remote.config.isPush();
        return new BuildCacheServicesConfiguration(
            local != null ? local.service : null, localPush,
            remote != null ? remote.service : null, remotePush);
    }

    private static <C extends BuildCache, S> DescribedBuildCacheService<C, S> createBuildCacheService(
        C configuration,
        BuildCacheServiceRole role,
        Path buildIdentityPath,
        BuildCacheConfigurationInternal buildCacheConfiguration,
        Instantiator instantiator
    ) {
        Class<? extends BuildCacheServiceFactory<C>> castFactoryType = Cast.uncheckedNonnullCast(
            buildCacheConfiguration.getBuildCacheServiceFactoryType(configuration.getClass())
        );

        BuildCacheServiceFactory<C> factory = instantiator.newInstance(castFactoryType);
        Describer describer = new Describer();
        S service = Cast.uncheckedNonnullCast(factory.createBuildCacheService(configuration, describer));
        ImmutableSortedMap<String, String> config = ImmutableSortedMap.copyOf(describer.configParams);
        BuildCacheDescription description = new BuildCacheDescription(configuration, describer.type, config);

        logConfig(buildIdentityPath, role, description);

        return new DescribedBuildCacheService<>(configuration, service, description);
    }

    private static void logConfig(Path buildIdentityPath, BuildCacheServiceRole role, BuildCacheDescription description) {
        if (LOGGER.isInfoEnabled()) {
            StringBuilder config = new StringBuilder();
            boolean pullOnly = !description.isPush();
            if (!description.config.isEmpty() || pullOnly) {
                Map<String, String> configMap;
                if (pullOnly) {
                    configMap = new LinkedHashMap<>();
                    // Pull-only always comes first
                    configMap.put("pull-only", null);
                    configMap.putAll(description.config);
                } else {
                    configMap = description.config;
                }
                config.append(" (");
                config.append(configMap.entrySet().stream().map(input -> {
                    if (input.getValue() == null) {
                        return input.getKey();
                    } else {
                        return input.getKey() + " = " + input.getValue();
                    }
                }).collect(Collectors.joining(", ")));
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
            this.className = GeneratedSubclasses.unpackType(buildCache).getName();
            this.push = buildCache.isPush();
            this.type = type;
            this.config = config;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public boolean isPush() {
            return push;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Map<String, String> getConfig() {
            return config;
        }
    }

    private static class Describer implements BuildCacheServiceFactory.Describer {

        private String type;
        private Map<String, String> configParams = new HashMap<>();

        @Override
        public BuildCacheServiceFactory.Describer type(String type) {
            this.type = Preconditions.checkNotNull(type, "'type' argument cannot be null");
            return this;
        }

        @Override
        public BuildCacheServiceFactory.Describer config(String name, String value) {
            Preconditions.checkNotNull(name, "'name' argument cannot be null");
            Preconditions.checkNotNull(value, "'value' argument cannot be null");
            configParams.put(name, value);
            return this;
        }
    }

    private static class DescribedBuildCacheService<C, S> {
        private final C config;
        private final S service;
        private final BuildCacheDescription description;

        public DescribedBuildCacheService(C config, S service, BuildCacheDescription description) {
            this.config = config;
            this.service = service;
            this.description = description;
        }
    }

    private static class DetailsImpl implements FinalizeBuildCacheConfigurationBuildOperationType.Details {
        private final String buildPath;

        private DetailsImpl(String buildPath) {
            this.buildPath = buildPath;
        }

        @Override
        public String getBuildPath() {
            return buildPath;
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
