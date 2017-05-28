/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.plugins.ide.idea.model.internal

import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry
import org.gradle.composite.internal.CompositeBuildIdeProjectResolver
import org.gradle.composite.internal.IncludedBuildControllers
import org.gradle.initialization.DefaultBuildIdentity
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency
import spock.lang.Specification

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class ModuleDependencyBuilderTest extends Specification {

    def projectId = newProjectId(":nested:project-name")
    def ideDependency = new IdeProjectDependency(projectId)
    def localComponentRegistry = Mock(LocalComponentRegistry)
    def ideProjectResolver = new CompositeBuildIdeProjectResolver(localComponentRegistry, Stub(IncludedBuildControllers), new DefaultBuildIdentity(projectId.build))
    def builder = new ModuleDependencyBuilder(ideProjectResolver)

    def "builds dependency for nonIdea project"() {
        when:
        def dependency = builder.create(ideDependency, 'compile')

        then:
        dependency.scope == 'compile'
        dependency.name == "project-name"

        and:
        localComponentRegistry.getAdditionalArtifacts(_) >> []
    }

    def "builds dependency for nonIdea root project"() {
        when:
        def dependency = builder.create(new IdeProjectDependency(newProjectId("build-1",":")), 'compile')

        then:
        dependency.scope == 'compile'
        dependency.name == "build-1"

        and:
        localComponentRegistry.getAdditionalArtifacts(_) >> []
    }

    def "builds dependency for project"() {
        given:
        def imlArtifact = Stub(LocalComponentArtifactMetadata) {
            getName() >> new DefaultIvyArtifactName("foo", "iml", "iml", null)
        }

        when:
        def dependency = builder.create(ideDependency, 'compile')

        then:
        dependency.scope == 'compile'
        dependency.name == 'foo'

        and:
        localComponentRegistry.getAdditionalArtifacts(_) >> [imlArtifact]
    }
}
