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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModule
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ProjectBackedModule
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.build.BuildState
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

class DefaultComponentIdentifierFactoryTest extends Specification {
    def buildIdentity = Mock(BuildState)
    def componentIdentifierFactory = new DefaultComponentIdentifierFactory(buildIdentity)

    def "can create project component identifier"() {
        given:
        def project = Mock(ProjectInternal)
        def expectedId = Stub(ProjectComponentIdentifier)
        def module = new ProjectBackedModule(project)

        when:
        def componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module)

        then:
        project.owner >> Stub(ProjectState) {
            getComponentIdentifier() >> expectedId
        }

        and:
        componentIdentifier == expectedId
    }

    def "can create module component identifier"() {
        given:
        def module = new DefaultModule('some-group', 'some-name', '1.0')

        when:
        def componentIdentifier = componentIdentifierFactory.createComponentIdentifier(module)

        then:
        componentIdentifier == new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('some-group', 'some-name'), '1.0')
    }
}
