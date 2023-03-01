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
import org.gradle.internal.enterprise.GradleEnterprisePluginService;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceFactory;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceRef;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginAdapter;
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar;

import javax.annotation.Nullable;

public class DefaultGradleEnterprisePluginAdapter implements GradleEnterprisePluginAdapter {

    private final GradleEnterprisePluginConfig config;
    private final DefaultGradleEnterprisePluginRequiredServices requiredServices;
    private final GradleEnterprisePluginBuildState buildState;
    private final DefaultGradleEnterprisePluginServiceRef pluginServiceRef;

    private final BuildOperationNotificationListenerRegistrar buildOperationNotificationListenerRegistrar;

    private GradleEnterprisePluginServiceFactory pluginServiceFactory;

    private transient GradleEnterprisePluginService pluginService;

    public DefaultGradleEnterprisePluginAdapter(
        GradleEnterprisePluginConfig config,
        DefaultGradleEnterprisePluginRequiredServices requiredServices,
        GradleEnterprisePluginBuildState buildState,
        DefaultGradleEnterprisePluginServiceRef pluginServiceRef,
        BuildOperationNotificationListenerRegistrar buildOperationNotificationListenerRegistrar
    ) {
        this.config = config;
        this.requiredServices = requiredServices;
        this.buildState = buildState;
        this.pluginServiceRef = pluginServiceRef;
        this.buildOperationNotificationListenerRegistrar = buildOperationNotificationListenerRegistrar;
    }

    public GradleEnterprisePluginServiceRef register(GradleEnterprisePluginServiceFactory pluginServiceFactory) {
        this.pluginServiceFactory = pluginServiceFactory;
        createPluginService();
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
        requiredServices.getBackgroundJobExecutors().stop();

        if (pluginService != null) {
            pluginService.getEndOfBuildListener().buildFinished(new GradleEnterprisePluginEndOfBuildListener.BuildResult() {
                @Nullable
                @Override
                public Throwable getFailure() {
                    return buildFailure;
                }
            });
        }
    }

    private void createPluginService() {
        pluginService = pluginServiceFactory.create(config, requiredServices, buildState);
        pluginServiceRef.set(pluginService);
        buildOperationNotificationListenerRegistrar.register(pluginService.getBuildOperationNotificationListener());
    }

}
