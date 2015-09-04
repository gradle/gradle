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
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.component.local.model.LocalComponentMetaData
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import spock.lang.Specification

class ProjectDependencyResolverTest extends Specification {
    final ProjectComponentRegistry registry = Mock()
    final ProjectDependencyResolver resolver = new ProjectDependencyResolver(registry)

    def "resolves project dependency"() {
        setup:
        def componentMetaData = Mock(LocalComponentMetaData)
        def result = Mock(BuildableComponentIdResolveResult)
        def dependencyMetaData = Stub(DependencyMetaData) {
            getSelector() >> DefaultProjectComponentSelector.newSelector(":project")
        }

        when:
        resolver.resolve(dependencyMetaData, result)

        then:
        1 * registry.getProject(":project") >> componentMetaData
        1 * result.resolved(componentMetaData)
        0 * result._
    }

    def "resolves project component"() {
        setup:
        def componentMetaData = Mock(LocalComponentMetaData)
        def result = Mock(BuildableComponentResolveResult)
        def projectComponentId = new DefaultProjectComponentIdentifier(":projectPath")

        when:
        resolver.resolve(projectComponentId, new DefaultComponentOverrideMetadata(), result)

        then:
        1 * registry.getProject(":projectPath") >> componentMetaData
        1 * result.resolved(componentMetaData)
        0 * result._
    }

    def "doesn't try to resolve non-project dependency"() {
        def result = Mock(BuildableComponentIdResolveResult)
        def dependencyDescriptor = Stub(DependencyDescriptor)
        def dependencyMetaData = Stub(DependencyMetaData) {
            getDescriptor() >> dependencyDescriptor
        }

        when:
        resolver.resolve(dependencyMetaData, result)

        then:
        0 * registry.getProject(_)
        0 * _
    }

    def "doesn't try to resolve non-project identifier"() {
        def result = Mock(BuildableComponentResolveResult)
        def componentIdentifier = Mock(ComponentIdentifier)
        def overrideMetaData = Mock(ComponentOverrideMetadata)

        when:
        resolver.resolve(componentIdentifier, overrideMetaData, result)

        then:
        0 * registry.getProject(_)
        0 * _
    }

    def "adds failure to resolution result if project does not exist"() {
        def result = Mock(BuildableComponentResolveResult)
        def componentIdentifier = new DefaultProjectComponentIdentifier(":doesnotexist")
        def overrideMetaData = Mock(ComponentOverrideMetadata)

        when:
        registry.getProject(_) >> null
        and:
        resolver.resolve(componentIdentifier, overrideMetaData, result)

        then:
        1 * result.failed(_) >> { ModuleVersionResolveException failure ->
            assert failure.message == "project ':doesnotexist' not found."
        }
        0 * _
    }
}
