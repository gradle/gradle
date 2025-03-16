/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.buildinit.plugins.internal.services;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry;
import org.gradle.buildinit.plugins.internal.action.InitBuiltInCommand;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.workers.WorkerExecutor;

/**
 * Provides the various build initialization services.
 */
public class BuildInitServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(InitBuiltInCommand.class);
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ServiceRegistrationProvider() {
            @Provides
            ProjectLayoutSetupRegistry createProjectLayoutSetupRegistry(MavenSettingsProvider mavenSettingsProvider, DocumentationRegistry documentationRegistry, WorkerExecutor workerExecutor) {
                return new ProjectLayoutSetupRegistryFactory(mavenSettingsProvider, documentationRegistry, workerExecutor).createProjectLayoutSetupRegistry();
            }
        });
    }
}
