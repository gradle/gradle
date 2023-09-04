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

import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.internal.artifacts.transform.VariantSelector
import org.gradle.api.specs.Spec
import spock.lang.Specification

class DefaultVisitedArtifactResultsTest extends Specification {
    def "strict selection includes selected variant of each node"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def variant2Artifacts = Stub(ResolvedArtifactSet)

        def selector = Stub(VariantSelector)
        def spec = Stub(Spec)

        given:
        artifacts1.select(spec, selector, selectFromAllVariants) >> variant1Artifacts
        artifacts2.select(spec, selector, selectFromAllVariants) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ResolutionStrategy.SortOrder.CONSUMER_FIRST, [artifacts1, artifacts2])
        def selected = results.select(spec, selector, selectFromAllVariants)

        expect:
        selected.getArtifacts() instanceof CompositeResolvedArtifactSet
        selected.getArtifacts().sets == [variant1Artifacts, variant2Artifacts]

        selected.getArtifactsWithId(0) == variant1Artifacts
        selected.getArtifactsWithId(1) == variant2Artifacts

        where:
        selectFromAllVariants << [false, true]
    }

    def "strict selection includes all failed artifacts"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = new BrokenResolvedArtifactSet(new Exception())
        def variant2Artifacts = new UnavailableResolvedArtifactSet(new Exception())

        def selector = Stub(VariantSelector)
        def spec = Stub(Spec)

        given:
        artifacts1.select(spec, selector, selectFromAllVariants) >> variant1Artifacts
        artifacts2.select(spec, selector, selectFromAllVariants) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ResolutionStrategy.SortOrder.CONSUMER_FIRST, [artifacts1, artifacts2])
        def selected = results.select(spec, selector, selectFromAllVariants)

        expect:
        selected.getArtifacts() instanceof CompositeResolvedArtifactSet
        selected.getArtifacts().sets == [variant1Artifacts, variant2Artifacts]

        selected.getArtifactsWithId(0) == variant1Artifacts
        selected.getArtifactsWithId(1) == variant2Artifacts

        where:
        selectFromAllVariants << [false, true]
    }

    def "lenient selection includes selected variant of each node"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def variant2Artifacts = Stub(ResolvedArtifactSet)

        def selector = Stub(VariantSelector)
        def spec = Stub(Spec)

        given:
        artifacts1.select(spec, selector, selectFromAllVariants) >> variant1Artifacts
        artifacts2.select(spec, selector, selectFromAllVariants) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ResolutionStrategy.SortOrder.CONSUMER_FIRST, [artifacts1, artifacts2])
        def selected = results.selectLenient(spec, selector, selectFromAllVariants)

        expect:
        selected.getArtifacts() instanceof CompositeResolvedArtifactSet
        selected.getArtifacts().sets == [variant1Artifacts, variant2Artifacts]

        selected.getArtifactsWithId(0) == variant1Artifacts
        selected.getArtifactsWithId(1) == variant2Artifacts

        where:
        selectFromAllVariants << [false, true]
    }

    def "lenient selection does not include unavailable selected variant"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = new UnavailableResolvedArtifactSet(new Exception())
        def variant2Artifacts = Stub(ResolvedArtifactSet)

        def selector = Stub(VariantSelector)
        def spec = Stub(Spec)

        given:
        artifacts1.select(spec, selector, selectFromAllVariants) >> variant1Artifacts
        artifacts2.select(spec, selector, selectFromAllVariants) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ResolutionStrategy.SortOrder.CONSUMER_FIRST, [artifacts1, artifacts2])
        def selected = results.selectLenient(spec, selector, selectFromAllVariants)

        expect:
        selected.getArtifacts() == variant2Artifacts

        where:
        selectFromAllVariants << [false, true]
    }

    def "lenient selection includes broken artifacts"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = new BrokenResolvedArtifactSet(new Exception())
        def variant2Artifacts = Stub(ResolvedArtifactSet)

        def selector = Stub(VariantSelector)
        def spec = Stub(Spec)

        given:
        artifacts1.select(spec, selector, selectFromAllVariants) >> variant1Artifacts
        artifacts2.select(spec, selector, selectFromAllVariants) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ResolutionStrategy.SortOrder.CONSUMER_FIRST, [artifacts1, artifacts2])
        def selected = results.selectLenient(spec, selector, selectFromAllVariants)

        expect:
        selected.getArtifacts() instanceof CompositeResolvedArtifactSet
        selected.getArtifacts().sets == [variant1Artifacts, variant2Artifacts]

        selected.getArtifactsWithId(0) == variant1Artifacts
        selected.getArtifactsWithId(1) == variant2Artifacts

        where:
        selectFromAllVariants << [false, true]
    }
}
