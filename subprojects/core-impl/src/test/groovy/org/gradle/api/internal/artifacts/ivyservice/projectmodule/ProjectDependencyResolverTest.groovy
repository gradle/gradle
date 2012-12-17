/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.BuildableModuleVersionResolveResult
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver
import spock.lang.Specification

class ProjectDependencyResolverTest extends Specification {
    final ProjectModuleRegistry registry = Mock()
    final DependencyDescriptor dependencyDescriptor = Mock()
    final ModuleRevisionId moduleRevisionId = Mock()
    final DependencyToModuleResolver target = Mock()
    final ProjectDependencyResolver resolver = new ProjectDependencyResolver(registry, target)

    def "resolves project dependency"() {
        setup:
        1 * moduleRevisionId.organisation >> "group"
        1 * moduleRevisionId.name >> "project"
        1 * moduleRevisionId.revision >> "1.0"

        ModuleDescriptor moduleDescriptor = Mock()
        BuildableModuleVersionResolveResult result = Mock()

        when:
        resolver.resolve(dependencyDescriptor, result)

        then:
        1 * registry.findProject(dependencyDescriptor) >> moduleDescriptor
        _ * moduleDescriptor.moduleRevisionId >> moduleRevisionId
        1 * result.resolved(_, moduleDescriptor, _) >> { args ->
            ModuleVersionIdentifier moduleVersionIdentifier = args[0]
            moduleVersionIdentifier.group == "group"
            moduleVersionIdentifier.name == "project"
            moduleVersionIdentifier.version == "1.0"
        }
        0 * result._
    }

    def "delegates to backing resolver for non-project dependency"() {
        BuildableModuleVersionResolveResult result = Mock()

        when:
        resolver.resolve(dependencyDescriptor, result)

        then:
        1 * registry.findProject(dependencyDescriptor) >> null
        1 * target.resolve(dependencyDescriptor, result)
        0 * result._
    }
}
