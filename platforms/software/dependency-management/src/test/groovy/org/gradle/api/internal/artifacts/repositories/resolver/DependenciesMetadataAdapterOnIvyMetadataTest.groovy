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

package org.gradle.api.internal.artifacts.repositories.resolver

import com.google.common.collect.ArrayListMultimap
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor

class DependenciesMetadataAdapterOnIvyMetadataTest extends DependenciesMetadataAdapterTest {

    @Override
    ModuleDependencyMetadata newDependency(ModuleComponentSelector requested) {
        ExternalDependencyDescriptor dependencyDescriptor = new IvyDependencyDescriptor(requested, ArrayListMultimap.create())
        return new ConfigurationBoundExternalDependencyMetadata(null, DefaultModuleComponentIdentifier.newId(requested.moduleIdentifier, requested.version), dependencyDescriptor)
    }

}
