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
package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.CachingComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DesugaredAttributeContainerSerializer;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.DefaultAttributesFactory;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistryFactory;
import org.gradle.api.internal.catalog.DependenciesAccessorsWorkspaceProvider;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.snapshot.impl.ValueSnapshotterSerializerRegistry;

public class DependencyManagementBuildSessionScopeServices implements ServiceRegistrationProvider {

    void configure(ServiceRegistration registration) {
        registration.add(DependenciesAccessorsWorkspaceProvider.class);
        registration.add(DefaultAttributesFactory.class);
        registration.add(DesugaredAttributeContainerSerializer.class);
        registration.add(MavenMutableModuleMetadataFactory.class);
        registration.add(IvyMutableModuleMetadataFactory.class);
        registration.add(PreferJavaRuntimeVariant.class);
        registration.add(ImmutableAttributesSchemaFactory.class);
        registration.add(ImmutableArtifactTypeRegistryFactory.class);
        registration.add(AttributeSchemaServices.class);
    }

    @Provides
    ComponentSelectionDescriptorFactory createComponentSelectionDescriptorFactory() {
        return new CachingComponentSelectionDescriptorFactory();
    }

    @Provides
    ValueSnapshotterSerializerRegistry createDependencyManagementValueSnapshotterSerializerRegistry(
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        AttributesFactory attributesFactory,
        NamedObjectInstantiator namedObjectInstantiator,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory
    ) {
        return new DependencyManagementValueSnapshotterSerializerRegistry(
            moduleIdentifierFactory,
            attributesFactory,
            namedObjectInstantiator,
            componentSelectionDescriptorFactory
        );
    }
}
