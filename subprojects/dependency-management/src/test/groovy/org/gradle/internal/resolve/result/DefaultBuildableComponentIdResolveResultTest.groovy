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

package org.gradle.internal.resolve.result

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

class DefaultBuildableComponentIdResolveResultTest extends Specification {
    def result = new DefaultBuildableComponentIdResolveResult()

    def "can resolve using id"() {
        def id = Stub(ComponentIdentifier)
        def mvId = Stub(ModuleVersionIdentifier)

        when:
        result.resolved(id, mvId)

        then:
        result.hasResult()
        result.id == id
        result.moduleVersionId == mvId
        result.state == null
        result.failure == null
    }

    def "can resolve using state"() {
        def id = Stub(ComponentIdentifier)
        def mvId = Stub(ModuleVersionIdentifier)
        def metadata = Stub(ComponentGraphResolveMetadata) {
            getModuleVersionId() >> mvId
        }
        def state = Stub(ComponentGraphResolveState) {
            getId() >> id
            getMetadata() >> metadata
        }

        when:
        result.resolved(state)

        then:
        result.hasResult()
        result.id == id
        result.moduleVersionId == mvId
        result.state == state
        result.failure == null
    }

    def "can mark as failed"() {
        org.gradle.internal.Factory<String> broken = { "too bad" }
        def failure = new ModuleVersionResolveException(Stub(ModuleVersionSelector), broken)

        when:
        result.failed(failure)

        then:
        result.hasResult()
        result.failure == failure

        when:
        result.id

        then:
        ModuleVersionResolveException e = thrown()
        e == failure
    }
}
