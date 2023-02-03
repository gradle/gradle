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
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.catalog.DependenciesAccessorsWorkspaceProvider;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.snapshot.impl.ValueSnapshotterSerializerRegistry;

public class DependencyManagementBuildSessionScopeServices {

    void configure(ServiceRegistration registration) {
        registration.add(DependenciesAccessorsWorkspaceProvider.class);
    }

    ComponentSelectionDescriptorFactory createComponentSelectionDescriptorFactory() {
        return new CachingComponentSelectionDescriptorFactory();
    }

    ValueSnapshotterSerializerRegistry createDependencyManagementValueSnapshotterSerializerRegistry(
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ImmutableAttributesFactory immutableAttributesFactory,
        NamedObjectInstantiator namedObjectInstantiator,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory
    ) {
        return new DependencyManagementValueSnapshotterSerializerRegistry(
            moduleIdentifierFactory,
            immutableAttributesFactory,
            namedObjectInstantiator,
            componentSelectionDescriptorFactory
        );
    }
}
