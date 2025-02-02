/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.impl;

import org.gradle.internal.enterprise.GradleEnterprisePluginBuildState;
import org.gradle.internal.enterprise.GradleEnterprisePluginConfig;
import org.gradle.internal.enterprise.GradleEnterprisePluginEndOfBuildListener;
import org.gradle.internal.enterprise.GradleEnterprisePluginRequiredServices;
import org.gradle.internal.enterprise.GradleEnterprisePluginService;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceFactory;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceRef;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter;
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar;

import javax.annotation.Nullable;

/**
 * Captures the state to recreate the {@link GradleEnterprisePluginService} instance.
 * <p>
 * The adapter is created on check-in in {@link DefaultGradleEnterprisePluginCheckInService} via {@link DefaultGradleEnterprisePluginAdapterFactory}.
 * Then the adapter is stored on the {@link org.gradle.internal.enterprise.core.GradleEnterprisePluginManager}.
 * <p>
 * There is some custom logic to store the adapter from the manager in the configuration cache and restore it afterward.
 * The pluginServices need to be recreated when loading from the configuration cache.
 * <p>
 * This must not be a service, since the configuration cache will not serialize services with state to the configuration cache.
 * Instead, it would re-use the newly registered services in the new build that causes the loss of pluginServiceFactory.
 */
public class DefaultGradleEnterprisePluginAdapter implements GradleEnterprisePluginAdapter {

    private final GradleEnterprisePluginServiceFactory pluginServiceFactory;
    private final GradleEnterprisePluginConfig config;
    private final GradleEnterprisePluginRequiredServices requiredServices;
    private final GradleEnterprisePluginBuildState buildState;
    private final GradleEnterprisePluginBackgroundJobExecutorsInternal backgroundJobExecutors;
    private final GradleEnterprisePluginServiceRefInternal pluginServiceRef;

    private final BuildOperationNotificationListenerRegistrar buildOperationNotificationListenerRegistrar;

    private transient GradleEnterprisePluginService pluginService;

    public DefaultGradleEnterprisePluginAdapter(
        GradleEnterprisePluginServiceFactory pluginServiceFactory,
        GradleEnterprisePluginConfig config,
        GradleEnterprisePluginRequiredServices requiredServices,
        GradleEnterprisePluginBuildState buildState,
        GradleEnterprisePluginBackgroundJobExecutorsInternal backgroundJobExecutors,
        GradleEnterprisePluginServiceRefInternal pluginServiceRef,
        BuildOperationNotificationListenerRegistrar buildOperationNotificationListenerRegistrar
    ) {
        this.pluginServiceFactory = pluginServiceFactory;
        this.config = config;
        this.requiredServices = requiredServices;
        this.buildState = buildState;
        this.backgroundJobExecutors = backgroundJobExecutors;
        this.pluginServiceRef = pluginServiceRef;
        this.buildOperationNotificationListenerRegistrar = buildOperationNotificationListenerRegistrar;

        createPluginService();
    }

    public GradleEnterprisePluginServiceRef getPluginServiceRef() {
        return pluginServiceRef;
    }

    @Override
    public boolean shouldSaveToConfigurationCache() {
        return true;
    }

    @Override
    public void onLoadFromConfigurationCache() {
        createPluginService();
    }

    @Override
    public void buildFinished(@Nullable Throwable buildFailure) {
        // Ensure that all tasks are complete prior to the buildFinished callback.
        backgroundJobExecutors.shutdown();

        if (pluginService != null) {
            pluginService.getEndOfBuildListener().buildFinished(new DefaultDevelocityPluginResult(buildFailure));
        }
    }

    private void createPluginService() {
        pluginService = pluginServiceFactory.create(config, requiredServices, buildState);
        pluginServiceRef.set(pluginService);
        buildOperationNotificationListenerRegistrar.register(pluginService.getBuildOperationNotificationListener());
    }

    private static class DefaultDevelocityPluginResult implements GradleEnterprisePluginEndOfBuildListener.BuildResult {
        private final Throwable buildFailure;

        public DefaultDevelocityPluginResult(@Nullable Throwable buildFailure) {
            this.buildFailure = buildFailure;
        }

        @Nullable
        @Override
        public Throwable getFailure() {
            return buildFailure;
        }
    }
}
