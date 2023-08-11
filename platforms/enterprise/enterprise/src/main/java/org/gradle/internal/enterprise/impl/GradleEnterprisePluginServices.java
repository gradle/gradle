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

import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanBuildStartedTime;
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanClock;
import org.gradle.internal.enterprise.impl.legacy.DefaultBuildScanScopeIds;
import org.gradle.internal.enterprise.impl.legacy.LegacyGradleEnterprisePluginCheckInService;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;

public class GradleEnterprisePluginServices extends AbstractPluginServiceRegistry {

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.add(GradleEnterpriseAutoAppliedPluginRegistry.class);
        registration.add(GradleEnterprisePluginAutoAppliedStatus.class);
        registration.add(DefaultGradleEnterprisePluginServiceRef.class);
        registration.add(DefaultGradleEnterprisePluginConfig.class);
        registration.add(DefaultGradleEnterprisePluginBuildState.class);

        // legacy
        registration.add(DefaultBuildScanClock.class);
        registration.add(DefaultBuildScanBuildStartedTime.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(GradleEnterprisePluginAutoApplicationListener.class);
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
        registration.add(DefaultGradleEnterprisePluginAdapter.class);
        registration.add(DefaultGradleEnterprisePluginBackgroundJobExecutors.class);
        registration.add(DefaultGradleEnterprisePluginCheckInService.class);
        registration.add(DefaultGradleEnterprisePluginRequiredServices.class);

        // legacy
        registration.add(DefaultBuildScanScopeIds.class);
        registration.add(LegacyGradleEnterprisePluginCheckInService.class);
    }

}
