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

import com.google.common.collect.ImmutableList
import spock.lang.Specification

class DefaultVisitedArtifactResultsTest extends Specification {
    def "strict selection includes selected variant of each node"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def variant2Artifacts = Stub(ResolvedArtifactSet)

        def services = Stub(ArtifactSelectionServices)

        given:
        artifacts1.select(services, _) >> variant1Artifacts
        artifacts2.select(services, _) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ImmutableList.of(artifacts1, artifacts2))
        def selected = results.select(services, Mock(ArtifactSelectionSpec), false)

        expect:
        selected.getArtifacts() instanceof CompositeResolvedArtifactSet
        selected.getArtifacts().sets == [variant1Artifacts, variant2Artifacts]

        selected.getArtifactsWithId(0) == variant1Artifacts
        selected.getArtifactsWithId(1) == variant2Artifacts
    }

    def "strict selection includes all failed artifacts"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = new BrokenResolvedArtifactSet(new Exception())
        def variant2Artifacts = new UnavailableResolvedArtifactSet(new Exception())

        def services = Stub(ArtifactSelectionServices)

        given:
        artifacts1.select(services, _) >> variant1Artifacts
        artifacts2.select(services, _) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ImmutableList.of(artifacts1, artifacts2))
        def selected = results.select(services, Mock(ArtifactSelectionSpec), false)

        expect:
        selected.getArtifacts() instanceof CompositeResolvedArtifactSet
        selected.getArtifacts().sets == [variant1Artifacts, variant2Artifacts]

        selected.getArtifactsWithId(0) == variant1Artifacts
        selected.getArtifactsWithId(1) == variant2Artifacts
    }

    def "lenient selection includes selected variant of each node"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def variant2Artifacts = Stub(ResolvedArtifactSet)

        def services = Stub(ArtifactSelectionServices)

        given:
        artifacts1.select(services, _) >> variant1Artifacts
        artifacts2.select(services, _) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ImmutableList.of(artifacts1, artifacts2))
        def selected = results.select(services, Mock(ArtifactSelectionSpec), true)

        expect:
        selected.getArtifacts() instanceof CompositeResolvedArtifactSet
        selected.getArtifacts().sets == [variant1Artifacts, variant2Artifacts]

        selected.getArtifactsWithId(0) == variant1Artifacts
        selected.getArtifactsWithId(1) == variant2Artifacts
    }

    def "lenient selection does not include unavailable selected variant"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = new UnavailableResolvedArtifactSet(new Exception())
        def variant2Artifacts = Stub(ResolvedArtifactSet)

        def services = Stub(ArtifactSelectionServices)

        given:
        artifacts1.select(services, _) >> variant1Artifacts
        artifacts2.select(services, _) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ImmutableList.of(artifacts1, artifacts2))
        def selected = results.select(services, Mock(ArtifactSelectionSpec), true)

        expect:
        selected.getArtifacts() == variant2Artifacts
    }

    def "lenient selection includes broken artifacts"() {
        def artifacts1 = Stub(ArtifactSet)
        def artifacts2 = Stub(ArtifactSet)
        def variant1Artifacts = new BrokenResolvedArtifactSet(new Exception())
        def variant2Artifacts = Stub(ResolvedArtifactSet)

        def services = Stub(ArtifactSelectionServices)

        given:
        artifacts1.select(services, _) >> variant1Artifacts
        artifacts2.select(services, _) >> variant2Artifacts

        def results = new DefaultVisitedArtifactResults(ImmutableList.of(artifacts1, artifacts2))
        def selected = results.select(services, Mock(ArtifactSelectionSpec), true)

        expect:
        selected.getArtifacts() instanceof CompositeResolvedArtifactSet
        selected.getArtifacts().sets == [variant1Artifacts, variant2Artifacts]

        selected.getArtifactsWithId(0) == variant1Artifacts
        selected.getArtifactsWithId(1) == variant2Artifacts
    }
}
