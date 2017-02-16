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

import org.gradle.api.Transformer
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import spock.lang.Specification

class DefaultVisitedArtifactResultsTest extends Specification {
    def "selection includes selected variant of each node"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1 = Stub(ResolvedVariant)
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def variant2 = Stub(ResolvedVariant)
        def variant3 = Stub(ResolvedVariant)
        def variant3Artifacts = Stub(ResolvedArtifactSet)
        def artifacts1Variants = [variant1, variant2] as Set
        def artifacts2Variants = [variant3] as Set

        def transformer = Stub(Transformer)

        given:
        artifacts1.variants >> artifacts1Variants
        artifacts2.variants >> artifacts2Variants
        transformer.transform(artifacts1Variants) >> variant1Artifacts
        transformer.transform(artifacts2Variants) >> variant3Artifacts

        def results = new DefaultVisitedArtifactResults([1L: artifacts1, 2L: artifacts2], [1L, 2L] as Set)
        def selected = results.select(Specs.satisfyAll(), transformer)

        expect:
        selected.getArtifacts(1) == variant1Artifacts
        selected.getArtifacts(2) == variant3Artifacts
    }

    def "selection includes empty result for each node that is filtered"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1 = Stub(ResolvedVariant)
        def variant2 = Stub(ResolvedVariant)
        def variant2Artifacts = Stub(ResolvedArtifactSet)
        def artifacts2Variants = [variant1, variant2] as Set
        def artifacts1Id = Stub(ComponentIdentifier)
        def artifacts2Id = Stub(ComponentIdentifier)

        def transformer = Stub(Transformer)
        def spec = Stub(Spec)

        given:
        artifacts1.componentIdentifier >> artifacts1Id
        artifacts2.componentIdentifier >> artifacts2Id
        spec.isSatisfiedBy(artifacts1Id) >> false
        spec.isSatisfiedBy(artifacts2Id) >> true
        artifacts2.variants >> artifacts2Variants
        transformer.transform(artifacts2Variants) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults([1L: artifacts1, 2L: artifacts2], [1L, 2L] as Set)
        def selected = results.select(spec, transformer)

        expect:
        selected.getArtifacts(1) == ResolvedArtifactSet.EMPTY
        selected.getArtifacts(2) == variant2Artifacts
    }
}
