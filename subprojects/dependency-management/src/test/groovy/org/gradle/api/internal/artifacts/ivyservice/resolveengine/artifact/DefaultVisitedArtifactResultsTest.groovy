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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.internal.artifacts.transform.VariantSelector
import org.gradle.api.specs.Spec
import org.gradle.internal.operations.BuildOperationProcessor
import spock.lang.Specification

class DefaultVisitedArtifactResultsTest extends Specification {
    def "selection includes selected variant of each node"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def variant2Artifacts = Stub(ResolvedArtifactSet)

        def selector = Stub(VariantSelector)
        def spec = Stub(Spec)

        given:
        artifacts1.select(spec, selector) >> variant1Artifacts
        artifacts2.select(spec, selector) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults([1L: artifacts1, 2L: artifacts2], [1L, 2L] as Set, Stub(BuildOperationProcessor))
        def selected = results.select(spec, selector)

        expect:
        selected.getArtifacts(1) == variant1Artifacts
        selected.getArtifacts(2) == variant2Artifacts
    }

}
