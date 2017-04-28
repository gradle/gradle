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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry
import org.gradle.composite.internal.CompositeBuildIdeProjectResolver
import org.gradle.initialization.DefaultBuildIdentity
import org.gradle.initialization.IncludedBuildExecuter
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class ProjectDependencyBuilderTest extends AbstractProjectBuilderSpec {
    def ProjectComponentIdentifier projectId = newProjectId(":nested:project-name")
    def localComponentRegistry = Mock(LocalComponentRegistry)
    def ideProjectResolver = new CompositeBuildIdeProjectResolver(localComponentRegistry, Stub(IncludedBuildExecuter), new DefaultBuildIdentity(projectId.build))
    def ProjectDependencyBuilder builder = new ProjectDependencyBuilder(ideProjectResolver)
    def IdeProjectDependency ideProjectDependency = new IdeProjectDependency(projectId)

    def "should create dependency using project name for project without eclipse plugin applied"() {
        when:
        def dependency = builder.build(ideProjectDependency)

        then:
        dependency.path == "/project-name"

        and:
        localComponentRegistry.getAdditionalArtifacts(_) >> []
    }

    def "should create dependency using eclipse projectName"() {
        given:
        def projectArtifact = Stub(LocalComponentArtifactMetadata) {
            getName() >> new DefaultIvyArtifactName("foo", "eclipse.project", "project", null)
        }
        localComponentRegistry.getAdditionalArtifacts(_) >> [projectArtifact]

        when:
        def dependency = builder.build(ideProjectDependency)

        then:
        dependency.path == '/foo'
    }
}
