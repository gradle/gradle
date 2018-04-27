/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.component

import org.gradle.api.Project
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DefaultModule
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.Module
import org.gradle.api.internal.artifacts.ProjectBackedModule
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.BuildIdentity
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import spock.lang.Specification

class DefaultComponentIdentifierFactoryTest extends Specification {
    BuildIdentity buildIdentity = Mock(BuildIdentity)
    ComponentIdentifierFactory componentIdentifierFactory = new DefaultComponentIdentifierFactory(buildIdentity)

    def "can create project component identifier"() {
        given:
        BuildIdentifier buildId = new DefaultBuildIdentifier("build")
        Project project = Mock(ProjectInternal)
        Module module = new ProjectBackedModule(project)

        when:
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module)

        then:
        project.path >> ':a'
        buildIdentity.getCurrentBuild() >> buildId

        and:
        componentIdentifier == new DefaultProjectComponentIdentifier(buildId, ':a')
    }

    def "can create module component identifier"() {
        given:
        Module module = new DefaultModule('some-group', 'some-name', '1.0')

        when:
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module)

        then:
        componentIdentifier == new DefaultModuleComponentIdentifier('some-group', 'some-name', '1.0')
    }

    def "can create component identifier for project dependency in same build"() {
        given:
        BuildIdentifier buildId = new DefaultBuildIdentifier("build")
        ProjectComponentSelector selector = new DefaultProjectComponentSelector(buildId, ":a")

        when:
        ComponentIdentifier componentIdentifier = componentIdentifierFactory.createProjectComponentIdentifier(selector)

        then:
        buildIdentity.getCurrentBuild() >> buildId

        and:
        componentIdentifier == new DefaultProjectComponentIdentifier(buildId, ':a')
    }
}
