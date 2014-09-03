/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.jvm.internal;

import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

public class JvmPluginServiceRegistry implements PluginServiceRegistry {
    public void registerGlobalServices(ServiceRegistration registration) {
    }

    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new ComponentRegistrationAction());
    }

    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class ComponentRegistrationAction {
        public void configure(ServiceRegistration registration, ComponentTypeRegistry componentTypeRegistry) {
            // TODO There should be a more explicit way to execute an action against existing services
            // TODO:DAZ Dependency Management should be able to extract this from the plugin, without explicit registration
            componentTypeRegistry.maybeRegisterComponentType(JvmLibrary.class)
                    .registerArtifactType(SourcesArtifact.class, ArtifactType.SOURCES);
        }
    }
}
