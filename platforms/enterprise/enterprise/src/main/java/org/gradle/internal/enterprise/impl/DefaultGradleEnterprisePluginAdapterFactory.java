/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.internal.enterprise.GradleEnterprisePluginRequiredServices;
import org.gradle.internal.enterprise.GradleEnterprisePluginServiceFactory;
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scope.Build.class)
public class DefaultGradleEnterprisePluginAdapterFactory {

    private final GradleEnterprisePluginConfig config;
    private final GradleEnterprisePluginRequiredServices requiredServices;
    private final GradleEnterprisePluginBuildState buildState;
    private final GradleEnterprisePluginBackgroundJobExecutorsInternal backgroundJobExecutors;
    private final GradleEnterprisePluginServiceRefInternal pluginServiceRef;
    private final BuildOperationNotificationListenerRegistrar buildOperationNotificationListenerRegistrar;

    public DefaultGradleEnterprisePluginAdapterFactory(
        GradleEnterprisePluginConfig config,
        GradleEnterprisePluginRequiredServices requiredServices,
        GradleEnterprisePluginBuildState buildState,
        GradleEnterprisePluginBackgroundJobExecutorsInternal backgroundJobExecutors,
        GradleEnterprisePluginServiceRefInternal pluginServiceRef,
        BuildOperationNotificationListenerRegistrar buildOperationNotificationListenerRegistrar
    ) {
        this.config = config;
        this.requiredServices = requiredServices;
        this.buildState = buildState;
        this.backgroundJobExecutors = backgroundJobExecutors;
        this.pluginServiceRef = pluginServiceRef;
        this.buildOperationNotificationListenerRegistrar = buildOperationNotificationListenerRegistrar;
    }

    public DefaultGradleEnterprisePluginAdapter create(GradleEnterprisePluginServiceFactory pluginServiceFactory) {
        return new DefaultGradleEnterprisePluginAdapter(
            pluginServiceFactory,
            config,
            requiredServices,
            buildState,
            backgroundJobExecutors,
            pluginServiceRef,
            buildOperationNotificationListenerRegistrar
        );
    }
}
