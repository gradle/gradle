/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.specs.Spec
import spock.lang.Specification

class NoBuildDependenciesArtifactSetTest extends Specification {
    def "returns original set when it is empty"() {
        expect:
        NoBuildDependenciesArtifactSet.of(ResolvedArtifactSet.EMPTY) == ResolvedArtifactSet.EMPTY
    }

    def "returns original set when it is a wrapper"() {
        def set = Stub(ResolvedArtifactSet)

        expect:
        def wrapper = NoBuildDependenciesArtifactSet.of(set)
        NoBuildDependenciesArtifactSet.of(wrapper) == wrapper
    }

    def "creates wrapper for non-empty set"() {
        def set = Stub(ResolvedArtifactSet)

        expect:
        def wrapper = NoBuildDependenciesArtifactSet.of(set)
        wrapper instanceof NoBuildDependenciesArtifactSet

        def buildDeps = []
        wrapper.collectBuildDependencies(buildDeps)
        buildDeps.empty
    }

    def "selects artifacts"() {
        def spec = Stub(Spec)
        def set1 = Stub(ResolvedArtifactSet)
        def selected1 = Stub(ResolvedArtifactSet)
        def set2 = Stub(ResolvedArtifactSet)
        def set3 = Stub(ResolvedArtifactSet)

        given:
        set1.select(spec) >> selected1
        set2.select(spec) >> set2
        set3.select(spec) >> ResolvedArtifactSet.EMPTY

        expect:
        def wrapper1 = NoBuildDependenciesArtifactSet.of(set1)
        wrapper1.select(spec) instanceof NoBuildDependenciesArtifactSet

        def wrapper2 = NoBuildDependenciesArtifactSet.of(set2)
        wrapper2.select(spec).is(wrapper2)

        def wrapper3 = NoBuildDependenciesArtifactSet.of(set3)
        wrapper3.select(spec) == ResolvedArtifactSet.EMPTY
    }
}
