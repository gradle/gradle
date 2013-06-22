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
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ModuleVersionPublishMetaData
import org.gradle.api.internal.artifacts.ivyservice.BuildableModuleVersionResolveResult
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyMetaData
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptor
import org.gradle.api.internal.project.ProjectInternal
import spock.lang.Specification

class ProjectDependencyResolverTest extends Specification {
    final ProjectModuleRegistry registry = Mock()
    final DependencyToModuleVersionResolver target = Mock()
    final ModuleDescriptorConverter converter = Mock()
    final ProjectDependencyResolver resolver = new ProjectDependencyResolver(registry, target, converter)

    def "resolves project dependency"() {
        setup:
        def ModuleVersionIdentifier moduleId = Stub(ModuleVersionIdentifier)
        def moduleDescriptor = Stub(ModuleDescriptor)
        def publishMetaData = Stub(ModuleVersionPublishMetaData) {
            getId() >> moduleId
            getModuleDescriptor() >> moduleDescriptor
        }
        def result = Mock(BuildableModuleVersionResolveResult)
        def dependencyProject = Stub(ProjectInternal)
        def dependencyDescriptor = Stub(ProjectDependencyDescriptor) {
            getTargetProject() >> dependencyProject
        }
        def dependencyMetaData = Stub(DependencyMetaData) {
            getDescriptor() >> dependencyDescriptor
        }

        when:
        resolver.resolve(dependencyMetaData, result)

        then:
        1 * registry.findProject(dependencyDescriptor) >> publishMetaData
        1 * result.resolved(moduleId, moduleDescriptor, _)
        0 * result._
    }

    def "delegates to backing resolver for non-project dependency"() {
        def result = Mock(BuildableModuleVersionResolveResult)
        def dependencyDescriptor = Stub(DependencyDescriptor)
        def dependencyMetaData = Stub(DependencyMetaData) {
            getDescriptor() >> dependencyDescriptor
        }

        when:
        resolver.resolve(dependencyMetaData, result)

        then:
        1 * target.resolve(dependencyMetaData, result)
        0 * _
    }
}
