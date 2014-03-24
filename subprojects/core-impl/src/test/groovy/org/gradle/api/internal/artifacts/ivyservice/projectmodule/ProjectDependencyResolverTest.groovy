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
import org.gradle.api.internal.artifacts.ivyservice.BuildableComponentResolveResult
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptor
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData
import org.gradle.api.internal.artifacts.metadata.MutableLocalComponentMetaData
import org.gradle.api.internal.project.ProjectInternal
import spock.lang.Specification

class ProjectDependencyResolverTest extends Specification {
    final ProjectComponentRegistry registry = Mock()
    final DependencyToModuleVersionResolver target = Mock()
    final LocalComponentFactory converter = Mock()
    final ProjectDependencyResolver resolver = new ProjectDependencyResolver(registry, converter, target)

    def "resolves project dependency"() {
        setup:
        def resolveMetaData = Stub(ModuleVersionMetaData)
        def componentMetaData = Stub(MutableLocalComponentMetaData) {
            toResolveMetaData() >> resolveMetaData
        }
        def result = Mock(BuildableComponentResolveResult)
        def dependencyProject = Stub(ProjectInternal) {
            getPath() >> ":project"
        }
        def dependencyDescriptor = Stub(ProjectDependencyDescriptor) {
            getTargetProject() >> dependencyProject
        }
        def dependencyMetaData = Stub(DependencyMetaData) {
            getDescriptor() >> dependencyDescriptor
        }

        when:
        resolver.resolve(dependencyMetaData, result)

        then:
        1 * registry.getProject(":project") >> componentMetaData
        1 * result.resolved(resolveMetaData)
        0 * result._
    }

    def "delegates to backing resolver for non-project dependency"() {
        def result = Mock(BuildableComponentResolveResult)
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
