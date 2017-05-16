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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import org.gradle.StartParameter;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.tasks.GeneratedSubclasses;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.configuration.internal.BuildCacheConfigurationInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.Path;
import org.gradle.util.SingleMessageLogger;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class BuildCacheServiceProvider {
    private static final Logger LOGGER = Logging.getLogger(BuildCacheServiceProvider.class);
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

    public BuildCacheService createBuildCacheService(final Path buildIdentityPath) {
        return buildOperationExecutor.call(new CallableBuildOperation<BuildCacheService>() {
            @Override
            public BuildCacheService call(BuildOperationContext context) {
                if (!startParameter.isBuildCacheEnabled()) {
                    context.setResult(FinalizeBuildCacheConfigurationBuildOperationType.ResultImpl.disabled());
                    return new NoOpBuildCacheService();
                }

                if (startParameter.isBuildCacheEnabled()) {
                    SingleMessageLogger.incubatingFeatureUsed("Build cache");
                }

                BuildCache local = buildCacheConfiguration.getLocal();
                BuildCache remote = buildCacheConfiguration.getRemote();

                boolean localEnabled = local != null && local.isEnabled();
                boolean remoteEnabled = remote != null && remote.isEnabled();

                if (remoteEnabled && startParameter.isOffline()) {
                    remoteEnabled = false;
                    LOGGER.warn("Remote build cache is disabled when running with --offline.");
                }

                DescribedBuildCacheService localDescribedService = localEnabled
                    ? createRawBuildCacheService(local, "local", buildIdentityPath)
                    : null;

                DescribedBuildCacheService remoteDescribedService = remoteEnabled
                    ? createRawBuildCacheService(remote, "remote", buildIdentityPath)
                    : null;

                context.setResult(new FinalizeBuildCacheConfigurationBuildOperationType.ResultImpl(
                    true,
                    local != null && local.isEnabled(),
                    remote != null && remote.isEnabled() && !startParameter.isOffline(),
                    localDescribedService == null ? null : localDescribedService.description,
                    remoteDescribedService == null ? null : remoteDescribedService.description
                ));

                //noinspection ConstantConditions
                RoleAwareBuildCacheService localRoleAware = localEnabled
                    ? decorate(localDescribedService.service, "local")
                    : null;

                //noinspection ConstantConditions
                RoleAwareBuildCacheService remoteRoleAware = remoteEnabled
                    ? decorate(remoteDescribedService.service, "remote")
                    : null;

                if (localEnabled && remoteEnabled) {
                    return new DispatchingBuildCacheService(localRoleAware, local.isPush(), remoteRoleAware, remote.isPush(), temporaryFileProvider);
                } else if (localEnabled) {
                    return preventPushIfNecessary(localRoleAware, local.isPush());
                } else if (remoteEnabled) {
                    return preventPushIfNecessary(remoteRoleAware, remote.isPush());
                } else if (!startParameter.isBuildCacheEnabled()) {
                    return new NoOpBuildCacheService();
                } else {
                    LOGGER.warn("Task output caching is enabled, but no build caches are configured or enabled.");
                    return new NoOpBuildCacheService();
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Finalize build cache configuration")
                    .details(new FinalizeBuildCacheConfigurationBuildOperationType.DetailsImpl());
            }
        });
    }


    private static RoleAwareBuildCacheService preventPushIfNecessary(RoleAwareBuildCacheService buildCacheService, boolean pushEnabled) {
        return pushEnabled ? buildCacheService : preventPush(buildCacheService);
    }

    private static PushOrPullPreventingBuildCacheServiceDecorator preventPush(RoleAwareBuildCacheService buildCacheService) {
        return new PushOrPullPreventingBuildCacheServiceDecorator(true, false, buildCacheService);
    }

    private RoleAwareBuildCacheService decorate(BuildCacheService rawService, String role) {
        RoleAwareBuildCacheService decoratedService = new BuildCacheServiceWithRole(role, rawService);
        decoratedService = new BuildOperationFiringBuildCacheServiceDecorator(buildOperationExecutor, decoratedService);
        decoratedService = new ShortCircuitingErrorHandlerBuildCacheServiceDecorator(MAX_ERROR_COUNT_FOR_BUILD_CACHE, startParameter, decoratedService);
        return decoratedService;
    }

    private <T extends BuildCache> DescribedBuildCacheService createRawBuildCacheService(final T configuration, String role, Path buildIdentityPath) {
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

    private static void logConfig(Path buildIdentityPath, String role, BuildCacheDescription description) {
        if (LOGGER.isLifecycleEnabled()) {
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

            LOGGER.lifecycle("Using {} {} build cache for {}{}.",
                role,
                description.type == null ? description.className : description.type,
                buildDescription,
                config
            );
        }
    }

    private static class BuildCacheServiceWithRole extends ForwardingBuildCacheService implements RoleAwareBuildCacheService {
        private final String role;
        private final BuildCacheService delegate;

        private BuildCacheServiceWithRole(String role, BuildCacheService delegate) {
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

    private static final class BuildCacheDescription implements FinalizeBuildCacheConfigurationBuildOperationType.ResultImpl.BuildCacheDescription {

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

}
