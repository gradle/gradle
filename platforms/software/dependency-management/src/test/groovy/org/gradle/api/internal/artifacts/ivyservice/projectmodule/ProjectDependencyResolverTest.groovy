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

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.TestComponentIdentifiers
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import spock.lang.Specification

import java.util.function.Consumer

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class ProjectDependencyResolverTest extends Specification {
    final LocalComponentRegistry registry = Mock()
    final ProjectStateRegistry projectRegistry = Stub()
    final ProjectArtifactResolver projectArtifactResolver = Stub()
    final ProjectDependencyResolver resolver = new ProjectDependencyResolver(registry, projectArtifactResolver)

    def setup() {
        def projectState = Stub(ProjectState)
        _ * projectRegistry.stateFor(_) >> projectState
        _ * projectState.applyToMutableState(_) >> { Consumer consumer -> consumer.accept(Stub(ProjectInternal)) }
    }

    def "resolves project dependency"() {
        setup:
        def selector = TestComponentIdentifiers.newSelector(":project")
        def componentState = Mock(LocalComponentGraphResolveState)
        def result = Mock(BuildableComponentIdResolveResult)
        def id = newProjectId(":project")

        when:
        resolver.resolve(selector, DefaultComponentOverrideMetadata.EMPTY, null, null, result)

        then:
        1 * registry.getComponent(id) >> componentState
        1 * result.resolved(componentState, _)
        0 * result._
    }

    def "resolves project component"() {
        setup:
        def componentState = Mock(LocalComponentGraphResolveState)
        def result = Mock(BuildableComponentResolveResult)
        def projectComponentId = newProjectId(":projectPath")

        when:
        resolver.resolve(projectComponentId, DefaultComponentOverrideMetadata.EMPTY, result)

        then:
        1 * registry.getComponent(projectComponentId) >> componentState
        1 * result.resolved(componentState, _)
        0 * result._
    }

    def "doesn't try to resolve non-project dependency"() {
        def result = Mock(BuildableComponentIdResolveResult)
        def selector = Stub(ComponentSelector)

        when:
        resolver.resolve(selector, DefaultComponentOverrideMetadata.EMPTY, null, null, result)

        then:
        0 * registry.getComponent(_)
        0 * _
    }

    def "doesn't try to resolve non-project identifier"() {
        def result = Mock(BuildableComponentResolveResult)
        def componentIdentifier = Mock(ComponentIdentifier)
        def overrideMetaData = Mock(ComponentOverrideMetadata)

        when:
        resolver.resolve(componentIdentifier, overrideMetaData, result)

        then:
        0 * registry.getComponent(_)
        0 * _
    }
}
