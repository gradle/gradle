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

package org.gradle.internal.resolve.result

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DefaultBuildableComponentResolveResultTest extends Specification {
    def result = new DefaultBuildableComponentResolveResult()

    def "can query id and metadata when resolved"() {
        ModuleVersionIdentifier id = Stub()
        def metadata = Stub(ModuleComponentResolveMetadata) {
            getModuleVersionId() >> id
        }
        def state = Stub(ComponentGraphResolveState) {
            getMetadata() >> metadata
        }

        when:
        result.resolved(state)

        then:
        result.moduleVersionId == id
        result.state == state
    }

    def "cannot get id when no result has been specified"() {
        when:
        result.moduleVersionId

        then:
        IllegalStateException e = thrown()
        e.message == 'No result has been specified.'
    }

    def "cannot get meta-data when no result has been specified"() {
        when:
        result.state

        then:
        IllegalStateException e = thrown()
        e.message == 'No result has been specified.'
    }

    def "cannot get failure when no result has been specified"() {
        when:
        result.failure

        then:
        IllegalStateException e = thrown()
        e.message == 'No result has been specified.'
    }

    def "cannot get id when resolve failed"() {
        org.gradle.internal.Factory<String> broken = { "too bad" }
        def failure = new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), "c"), broken)

        when:
        result.failed(failure)
        result.moduleVersionId

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "cannot get meta-data when resolve failed"() {
        org.gradle.internal.Factory<String> broken = { "too bad" }
        def failure = new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), "c"), broken)

        when:
        result.failed(failure)
        result.state

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }

    def "failure is null when successfully resolved"() {
        when:
        result.resolved(Mock(ComponentGraphResolveState))

        then:
        result.failure == null
    }

    def "fails with not found exception when not found using module component id"() {
        def id = Mock(ModuleComponentIdentifier) {
            it.group >> "org.gradle"
            it.module >> "core"
            it.version >> "2.3"
        }

        when:
        result.notFound(id)

        then:
        result.failure instanceof ModuleVersionNotFoundException
    }

    def "copies results to an id resolve result"() {
        def idResult = Mock(BuildableComponentIdResolveResult)
        def state = Stub(ComponentGraphResolveState)

        given:
        result.attempted("a")
        result.attempted("b")
        result.resolved(state)

        when:
        result.applyTo(idResult)

        then:
        1 * idResult.attempted("a")
        1 * idResult.attempted("b")
        1 * idResult.resolved(state)
    }

    def "copies failure result to an id resolve result"() {
        org.gradle.internal.Factory<String> broken = { "too bad" }
        def idResult = Mock(BuildableComponentIdResolveResult)
        def failure = new ModuleVersionResolveException(Stub(ModuleVersionSelector), broken)

        given:
        result.attempted("a")
        result.attempted("b")
        result.failed(failure)

        when:
        result.applyTo(idResult)

        then:
        1 * idResult.attempted("a")
        1 * idResult.attempted("b")
        1 * idResult.failed(failure)
    }
}
