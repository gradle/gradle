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

import org.gradle.plugins.ide.eclipse.internal.EclipseProjectMetadata
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class ProjectDependencyBuilderTest extends AbstractProjectBuilderSpec {
    def projectId = newProjectId(":nested:project-name")
    def artifactRegistry = Mock(IdeArtifactRegistry)
    def builder = new ProjectDependencyBuilder(artifactRegistry)

    def "should create dependency using project name for project without eclipse plugin applied"() {
        when:
        def dependency = builder.build(projectId, null, null, false, false)

        then:
        dependency.path == "/project-name"

        and:
        artifactRegistry.getIdeProject(_, _) >> null
    }

    def "should create dependency using eclipse projectName"() {
        given:
        def projectMetadata = Stub(EclipseProjectMetadata) {
            getName() >> "foo"
        }
        artifactRegistry.getIdeProject(EclipseProjectMetadata, projectId) >> projectMetadata

        when:
        def dependency = builder.build(projectId, null, null, false, false)

        then:
        dependency.path == '/foo'
    }
}
