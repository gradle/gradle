/*
 * Copyright 2016 the original author or authors.
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

/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.descriptor.MavenScope
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.component.external.model.maven.MavenDependencyType
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ExcludeMetadata

class MavenDependencyDescriptorTest extends ExternalDependencyDescriptorTest {
    final ModuleExclusions moduleExclusions = new ModuleExclusions()
    final ExcludeSpec nothing = moduleExclusions.nothing()

    @Override
    MavenDependencyDescriptor create(ModuleComponentSelector selector) {
        return mavenDependencyMetadata(MavenScope.Compile, selector, [])
    }

    MavenDependencyDescriptor createWithExcludes(ModuleComponentSelector selector, List<Exclude> excludes) {
        return mavenDependencyMetadata(MavenScope.Compile, selector, excludes)
    }

    def "excludes nothing when no exclude rules provided"() {
        def dep = createWithExcludes(requested, [])

        expect:
        def exclusions = moduleExclusions.excludeAny(dep.allExcludes)
        exclusions == nothing
        exclusions.is(moduleExclusions.excludeAny(dep.allExcludes))
    }

    def "applies exclude rules when traversing a configuration"() {
        def exclude1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def exclude2 = new DefaultExclude(DefaultModuleIdentifier.newId("group2", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude1, exclude2])

        expect:
        def exclusions = moduleExclusions.excludeAny(dep.allExcludes)
        exclusions == moduleExclusions.excludeAny(ImmutableList.of(exclude1, exclude2))
        exclusions.is(moduleExclusions.excludeAny(dep.allExcludes))
    }

    private static MavenDependencyDescriptor mavenDependencyMetadata(MavenScope scope, ModuleComponentSelector selector, List<ExcludeMetadata> excludes) {
        return new MavenDependencyDescriptor(scope, MavenDependencyType.DEPENDENCY, selector, null, excludes)
    }
}
