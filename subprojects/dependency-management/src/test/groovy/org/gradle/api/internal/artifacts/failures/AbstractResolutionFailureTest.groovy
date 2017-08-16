/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.failures

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import spock.lang.Specification

class AbstractResolutionFailureTest extends Specification {

    def "can extract attempted locations"() {
        given:
        def identifier = moduleComponentId()
        def e = new ModuleVersionNotFoundException(Mock(ModuleVersionIdentifier) {
            getGroup() >> identifier.group
            getName() >> identifier.module
            getVersion() >> identifier.version
        }, ['a', 'b'])

        when:
        def failure = AbstractResolutionFailure.of(identifier, e)

        then:
        failure.componentId == identifier
        failure.problem.is(e)
        failure.attemptedLocations == ['a', 'b']

    }

    def "can create a failure for a ModuleComponentIdentifier"() {
        given:
        def id = moduleComponentId()
        def t = Mock(Throwable)

        when:
        def failure = AbstractResolutionFailure.of(id, t)

        then:
        failure.componentId.is(id)
        failure.problem.is(t)
        failure.attemptedLocations == null
    }

    def "can create a failure for a ProjectComponentIdentifier"() {
        given:
        def id = projectComponentId()
        def t = Mock(Throwable)

        when:
        def failure = AbstractResolutionFailure.of(id, t)

        then:
        failure.componentId.is(id)
        failure.problem.is(t)
        failure.attemptedLocations == null
    }

    def "cannot create a failure from an arbitrary component id"() {
        when:
        AbstractResolutionFailure.of(Mock(ComponentIdentifier), Mock(Throwable))

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.startsWith 'Unsupported identifier type:'
    }

    def "can create a failure for an artifact id when it implements component id"() {
        given:
        def cid = moduleComponentId()
        def aid = Mock(TestArtifactId) {
            getComponentIdentifier() >> cid
        }
        def t = Mock(Throwable)

        when:
        def failure = AbstractResolutionFailure.of(aid, t)

        then:
        failure.componentId.is(cid)
        failure.problem.is(t)
        failure.attemptedLocations == null
    }

    private ModuleComponentIdentifier moduleComponentId() {
        Mock(ModuleComponentIdentifier) {
            getGroup() >> 'foo'
            getModule() >> 'bar'
            getVersion() >> 'baz'
        }
    }

    private ProjectComponentIdentifier projectComponentId() {
        Mock(ProjectComponentIdentifier) {
            getGroup() >> 'foo'
            getModule() >> 'bar'
            getVersion() >> 'baz'
            getProjectPath() >> ':path'
        }
    }

    interface TestArtifactId extends ComponentIdentifier, ComponentArtifactIdentifier {}
}
