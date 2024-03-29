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

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyMetadata
import org.gradle.internal.component.external.model.maven.MavenDependencyType

class DependenciesMetadataAdapterOnPomMetadataTest extends DependenciesMetadataAdapterTest {

    @Override
    ModuleDependencyMetadata newDependency(ModuleComponentSelector requested) {
        MavenDependencyDescriptor dependencyDescriptor = new MavenDependencyDescriptor(MavenScope.Compile, MavenDependencyType.DEPENDENCY, requested, null, [])
        return new MavenDependencyMetadata(dependencyDescriptor)
    }
}
