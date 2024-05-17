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

import org.gradle.plugins.ide.idea.internal.IdeaModuleMetadata
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import spock.lang.Specification

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class ModuleDependencyBuilderTest extends Specification {

    def projectId = newProjectId(":nested:project-name")
    def artifactRegistry = Mock(IdeArtifactRegistry)
    def builder = new ModuleDependencyBuilder(artifactRegistry)

    def "builds dependency for nonIdea project"() {
        when:
        def dependency = builder.create(projectId, 'compile')

        then:
        dependency.scope == 'compile'
        dependency.name == "project-name"

        and:
        artifactRegistry.getIdeProject(_, _) >> null
    }

    def "builds dependency for nonIdea root project"() {
        when:
        def dependency = builder.create(newProjectId(":build-1",":a"), 'compile')

        then:
        dependency.scope == 'compile'
        dependency.name == "a"

        and:
        artifactRegistry.getIdeProject(_, _) >> null
    }

    def "builds dependency for project"() {
        given:
        def moduleMetadata = Stub(IdeaModuleMetadata) {
            getName() >> "foo"
        }

        when:
        def dependency = builder.create(projectId, 'compile')

        then:
        dependency.scope == 'compile'
        dependency.name == 'foo'

        and:
        artifactRegistry.getIdeProject(IdeaModuleMetadata, projectId) >> moduleMetadata
    }
}
