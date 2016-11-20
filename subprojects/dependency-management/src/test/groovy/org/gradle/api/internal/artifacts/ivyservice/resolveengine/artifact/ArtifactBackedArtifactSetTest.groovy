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

import org.gradle.api.Buildable
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import spock.lang.Specification

class ArtifactBackedArtifactSetTest extends Specification {
    def artifact1 = Mock(TestArtifact)
    def artifact2 = Mock(TestArtifact)
    def artifact3 = Mock(TestArtifact)

    def "factory method returns specialized sets for zero and one elements"() {
        expect:
        ArtifactBackedArtifactSet.of([]) == ResolvedArtifactSet.EMPTY
        ArtifactBackedArtifactSet.of([artifact1]) instanceof ArtifactBackedArtifactSet.SingletonSet
        ArtifactBackedArtifactSet.of([artifact1, artifact2]) instanceof ArtifactBackedArtifactSet
    }

    def "returns artifacts and retains order"() {
        def set1 = ArtifactBackedArtifactSet.of([artifact1, artifact2, artifact3])
        def set2 = ArtifactBackedArtifactSet.of([artifact1, artifact2, artifact1, artifact2])
        def set3 = ArtifactBackedArtifactSet.of([artifact1])

        expect:
        set1.artifacts as List == [artifact1, artifact2, artifact3]
        set2.artifacts as List == [artifact1, artifact2]
        set3.artifacts as List == [artifact1]
    }

    def "visits artifacts and retains order"() {
        def visitor = Mock(ArtifactVisitor)
        def set1 = ArtifactBackedArtifactSet.of([artifact1, artifact2])
        def set2 = ArtifactBackedArtifactSet.of([artifact1])

        when:
        set1.visit(visitor)

        then:
        1 * visitor.visitArtifact(artifact1)

        then:
        1 * visitor.visitArtifact(artifact2)
        0 * _

        when:
        set2.visit(visitor)

        then:
        1 * visitor.visitArtifact(artifact1)
        0 * _
    }

    def "selects matching artifacts"() {
        def spec = Stub(Spec)
        def artifact4 = Mock(TestArtifact)

        given:
        spec.isSatisfiedBy(artifact1) >> true
        spec.isSatisfiedBy(artifact2) >> false
        spec.isSatisfiedBy(artifact3) >> false
        spec.isSatisfiedBy(artifact4) >> true

        expect:

        // Singletons
        def set1 = ArtifactBackedArtifactSet.of([artifact1])
        set1.select(spec).is(set1)

        ArtifactBackedArtifactSet.of([artifact2]).select(spec) == ResolvedArtifactSet.EMPTY

        // Composites
        def set2 = ArtifactBackedArtifactSet.of([artifact1, artifact2, artifact3]).select(spec)
        set2 instanceof ArtifactBackedArtifactSet.SingletonSet
        set2.artifacts == [artifact1] as Set

        ArtifactBackedArtifactSet.of([artifact2, artifact3]).select(spec) == ResolvedArtifactSet.EMPTY

        def set3 = ArtifactBackedArtifactSet.of([artifact1, artifact2, artifact4]).select(spec)
        set3 instanceof ArtifactBackedArtifactSet
        set3.artifacts == [artifact1, artifact4] as Set
    }

    def "collects build dependencies"() {
        def deps1 = Stub(TaskDependency)
        def deps2 = Stub(TaskDependency)
        def set1 = ArtifactBackedArtifactSet.of([artifact1, artifact2])
        def set2 = ArtifactBackedArtifactSet.of([artifact1])

        given:
        artifact1.buildDependencies >> deps1
        artifact2.buildDependencies >> deps2

        when:
        def buildDeps = []
        set1.collectBuildDependencies(buildDeps)

        then:
        buildDeps == [deps1, deps2]

        when:
        buildDeps = []
        set2.collectBuildDependencies(buildDeps)

        then:
        buildDeps == [deps1]
    }

    interface TestArtifact extends ResolvedArtifact, Buildable { }

}
