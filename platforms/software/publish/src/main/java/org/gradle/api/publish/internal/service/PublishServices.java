/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.internal.service;

import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectDependencyPublicationResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.internal.component.DefaultSoftwareComponentFactory;
import org.gradle.api.publish.internal.mapping.DefaultDependencyCoordinateResolverFactory;
import org.gradle.api.publish.internal.validation.DuplicatePublicationTracker;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;

public class PublishServices extends AbstractGradleModuleServices {
    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(DefaultProjectDependencyPublicationResolver.class);
        registration.add(DuplicatePublicationTracker.class);
        registration.add(DefaultDependencyCoordinateResolverFactory.class);
    }

    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalScopeServices());
    }

    private static class GlobalScopeServices implements ServiceRegistrationProvider {
        @Provides
        SoftwareComponentFactory createSoftwareComponentFactory(ObjectFactory objectFactory) {
            return new DefaultSoftwareComponentFactory(objectFactory);
        }
    }
}
