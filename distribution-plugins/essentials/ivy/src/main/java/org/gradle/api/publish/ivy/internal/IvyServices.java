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

package org.gradle.api.publish.ivy.internal;

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.publish.ivy.internal.publisher.ContextualizingIvyPublisher;
import org.gradle.api.publish.ivy.internal.publisher.DependencyResolverIvyPublisher;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublisher;
import org.gradle.api.publish.ivy.internal.publisher.ValidatingIvyPublisher;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.ivy.IvyDescriptorArtifact;
import org.gradle.ivy.IvyModule;

public class IvyServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildServices());
    }

    private static class BuildServices {

        IvyPublisher createIvyPublisher(IvyContextManager ivyContextManager, ImmutableModuleIdentifierFactory moduleIdentifierFactory, FileResourceRepository fileResourceRepository, IvyMutableModuleMetadataFactory metadataFactory) {
            IvyPublisher publisher = new DependencyResolverIvyPublisher();
            publisher = new ValidatingIvyPublisher(publisher, moduleIdentifierFactory, fileResourceRepository, metadataFactory);
            return new ContextualizingIvyPublisher(publisher, ivyContextManager);
        }

        public void configure(ServiceRegistration registration, ComponentTypeRegistry componentTypeRegistry) {
            // TODO There should be a more explicit way to execute an action against existing services
            componentTypeRegistry.maybeRegisterComponentType(IvyModule.class)
                    .registerArtifactType(IvyDescriptorArtifact.class, ArtifactType.IVY_DESCRIPTOR);
        }
    }
}
