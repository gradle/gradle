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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons
import org.gradle.internal.component.model.ComponentResolveMetadata
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
        result.selectionDescription == VersionSelectionReasons.REQUESTED
        result.metaData == null
        result.failure == null
    }

    def "can override selection reason"() {
        def id = Stub(ComponentIdentifier)
        def mvId = Stub(ModuleVersionIdentifier)

        when:
        result.resolved(id, mvId)
        result.selectionDescription = VersionSelectionReasons.CONFLICT_RESOLUTION

        then:
        result.selectionDescription == VersionSelectionReasons.CONFLICT_RESOLUTION
    }

    def "can resolve using meta-data"() {
        def id = Stub(ComponentIdentifier)
        def mvId = Stub(ModuleVersionIdentifier)
        def metaData = Stub(ComponentResolveMetadata) {
            getId() >> mvId
            getComponentId() >> id
        }

        when:
        result.resolved(metaData)

        then:
        result.hasResult()
        result.id == id
        result.moduleVersionId == mvId
        result.metaData == metaData
        result.selectionDescription == VersionSelectionReasons.REQUESTED
        result.failure == null
    }

    def "can mark as failed"() {
        def failure = new ModuleVersionResolveException(Stub(ModuleVersionSelector), "broken")

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
        result.selectionDescription == VersionSelectionReasons.REQUESTED
    }
}
