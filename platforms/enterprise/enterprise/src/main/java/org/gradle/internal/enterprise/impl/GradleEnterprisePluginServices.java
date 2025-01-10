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

import org.gradle.internal.enterprise.DevelocityBuildLifecycleService;
import org.gradle.internal.enterprise.DevelocityPluginUnsafeConfigurationService;
import org.gradle.internal.enterprise.GradleEnterprisePluginBuildState;
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInService;
import org.gradle.internal.enterprise.GradleEnterprisePluginConfig;
import org.gradle.internal.enterprise.GradleEnterprisePluginRequiredServices;
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanBuildStartedTime;
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanClock;
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanScopeIds;
import org.gradle.internal.enterprise.impl.legacy.LegacyGradleEnterprisePluginCheckInService;
import org.gradle.internal.scan.scopeids.BuildScanScopeIds;
import org.gradle.internal.scan.time.BuildScanBuildStartedTime;
import org.gradle.internal.scan.time.BuildScanClock;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

public class GradleEnterprisePluginServices extends AbstractGradleModuleServices {

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.add(GradleEnterpriseAutoAppliedPluginRegistry.class);
        registration.add(GradleEnterprisePluginAutoAppliedStatus.class);
        registration.add(GradleEnterprisePluginServiceRefInternal.class, DefaultGradleEnterprisePluginServiceRef.class);
        registration.add(GradleEnterprisePluginBuildState.class, DefaultGradleEnterprisePluginBuildState.class);
        registration.add(GradleEnterprisePluginConfig.class, DefaultGradleEnterprisePluginConfig.class);
        registration.add(GradleEnterprisePluginBackgroundJobExecutorsInternal.class, DefaultGradleEnterprisePluginBackgroundJobExecutors.class);
        registration.add(DevelocityPluginUnsafeConfigurationService.class, DefaultDevelocityPluginUnsafeConfigurationService.class);

        // legacy
        registration.add(BuildScanClock.class, DefaultBuildScanClock.class);
        registration.add(BuildScanBuildStartedTime.class, DefaultBuildScanBuildStartedTime.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(GradleEnterprisePluginAutoApplicationListener.class);
        registration.add(DefaultGradleEnterprisePluginAdapterFactory.class);
        registration.add(GradleEnterprisePluginCheckInService.class, DefaultGradleEnterprisePluginCheckInService.class);
        registration.add(DevelocityBuildLifecycleService.class, DefaultDevelocityBuildLifecycleService.class);
        registration.add(GradleEnterprisePluginRequiredServices.class, DefaultGradleEnterprisePluginRequiredServices.class);

        // legacy
        registration.add(BuildScanScopeIds.class, DefaultBuildScanScopeIds.class);
        registration.add(LegacyGradleEnterprisePluginCheckInService.class);
    }

}
