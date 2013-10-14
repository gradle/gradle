/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;

/**
 * Provides the various build initialization services.
 */
public class BuildInitServices implements PluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
    }

    public void registerBuildServices(ServiceRegistration registration) {
    }

    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeBuildInitServices());
    }

    private static class ProjectScopeBuildInitServices {
        ProjectLayoutSetupRegistry createProjectLayoutSetupRegistry(MavenSettingsProvider mavenSettingsProvider, DocumentationRegistry documentationRegistry, GradleInternal gradle) {
            return new ProjectLayoutSetupRegistryFactory(mavenSettingsProvider, documentationRegistry, gradle.getRootProject().getFileResolver()).createProjectLayoutSetupRegistry();
        }
    }
}
