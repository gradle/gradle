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
import org.gradle.api.internal.tasks.DefaultSourceSetContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.internal.DefaultJvmPluginServices;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;

public class JvmLanguageServices extends AbstractGradleModuleServices {

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new ComponentRegistrationAction());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectScopeServices());
        registration.add(DefaultJvmPluginServices.class);
    }

    private static class ProjectScopeServices implements ServiceRegistrationProvider {
        @Provides
        SourceSetContainer createSourceSetContainer(ObjectFactory objectFactory) {
            return objectFactory.newInstance(DefaultSourceSetContainer.class);
        }
    }

    private static class ComponentRegistrationAction implements ServiceRegistrationProvider {
        /**
         * @param registration unused parameter required by convention, see {@link org.gradle.internal.service.DefaultServiceRegistry}.
         */
        public void configure(ServiceRegistration registration,
                              ComponentTypeRegistry componentTypeRegistry) {
            componentTypeRegistry
                .maybeRegisterComponentType(JvmLibrary.class)
                .registerArtifactType(SourcesArtifact.class, ArtifactType.SOURCES);
        }
    }
}
