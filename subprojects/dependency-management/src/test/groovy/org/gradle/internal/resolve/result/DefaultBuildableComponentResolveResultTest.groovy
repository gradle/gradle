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
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.resolve.ModuleVersionNotFoundException
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DefaultBuildableComponentResolveResultTest extends Specification {
    def result = new DefaultBuildableComponentResolveResult()

    def "can query id and meta-data when resolved"() {
        ModuleVersionIdentifier id = Stub()
        ModuleComponentResolveMetadata metaData = Stub() {
            getModuleVersionId() >> id
        }

        when:
        result.resolved(metaData)

        then:
        result.moduleVersionId == id
        result.metadata == metaData
    }

    def "cannot get id when no result has been specified"() {
        when:
        result.moduleVersionId

        then:
        def e = thrown IllegalStateException
        e.message == 'No result has been specified.'
    }

    def "cannot get meta-data when no result has been specified"() {
        when:
        result.metadata

        then:
        def e = thrown IllegalStateException
        e.message == 'No result has been specified.'
    }

    def "cannot get failure when no result has been specified"() {
        when:
        result.failure

        then:
        def e = thrown IllegalStateException
        e.message == 'No result has been specified.'
    }

    def "cannot get id when resolve failed"() {
        def failure = new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), "c"), "broken")

        when:
        result.failed(failure)
        result.moduleVersionId

        then:
        def e = thrown ModuleVersionResolveException
        e == failure
    }

    def "cannot get meta-data when resolve failed"() {
        def failure = new ModuleVersionResolveException(newSelector(DefaultModuleIdentifier.newId("a", "b"), "c"), "broken")

        when:
        result.failed(failure)
        result.metadata

        then:
        def e = thrown ModuleVersionResolveException
        e == failure
    }

    def "failure is null when successfully resolved"() {
        when:
        result.resolved(Mock(ModuleComponentResolveMetadata))

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
        def metaData = Stub(ComponentResolveMetadata)

        given:
        result.attempted("a")
        result.attempted("b")
        result.resolved(metaData)

        when:
        result.applyTo(idResult)

        then:
        1 * idResult.attempted("a")
        1 * idResult.attempted("b")
        1 * idResult.resolved(metaData)
    }

    def "copies failure result to an id resolve result"() {
        def idResult = Mock(BuildableComponentIdResolveResult)
        def failure = new ModuleVersionResolveException(Stub(ModuleVersionSelector), "broken")

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
