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

import org.gradle.api.artifacts.ResolvedArtifact
import spock.lang.Specification

class ArtifactBackedArtifactSetTest extends Specification {
    def artifact1 = Mock(ResolvedArtifact)
    def artifact2 = Mock(ResolvedArtifact)
    def set = new ArtifactBackedArtifactSet([artifact1, artifact2] as Set)

    def "factory method returns empty set when source set is empty"() {
        expect:
        ArtifactBackedArtifactSet.of([]) == ResolvedArtifactSet.EMPTY
        ArtifactBackedArtifactSet.of([artifact1, artifact2]) instanceof ArtifactBackedArtifactSet
    }

    def "returns artifacts and retains order"() {
        expect:
        set.artifacts as List == [artifact1, artifact2]
    }

    def "visits artifacts"() {
        def visitor = Mock(ArtifactVisitor)

        when:
        set.visit(visitor)

        then:
        1 * visitor.visitArtifact(artifact1)

        then:
        1 * visitor.visitArtifact(artifact2)
        0 * _
    }
}
