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
import org.gradle.api.internal.attributes.ImmutableAttributes
import spock.lang.Specification

class CompositeArtifactSetTest extends Specification {
    def "factory method returns empty when no non-empty sets provided"() {
        expect:
        CompositeArtifactSet.of([]) == ResolvedArtifactSet.EMPTY
        CompositeArtifactSet.of([ResolvedArtifactSet.EMPTY, ResolvedArtifactSet.EMPTY]) == ResolvedArtifactSet.EMPTY
    }

    def "factory method returns single set when single non-empty sets provided"() {
        def set = Mock(ResolvedArtifactSet)

        expect:
        CompositeArtifactSet.of([set]) == set
        CompositeArtifactSet.of([ResolvedArtifactSet.EMPTY, set, ResolvedArtifactSet.EMPTY]) == set
    }

    def "provides union of resolved artifacts with order retained"() {
        def a1 = Mock(ResolvedArtifact)
        def a2 = Mock(ResolvedArtifact)
        def a3 = Mock(ResolvedArtifact)
        def set1 = ArtifactBackedArtifactSet.forVariant(ImmutableAttributes.EMPTY, [a1, a2])
        def set2 = ArtifactBackedArtifactSet.forVariant(ImmutableAttributes.EMPTY, [a2, a3])

        expect:
        CompositeArtifactSet.of([set1, set2]).artifacts as List == [a1, a2, a3]
    }

    def "visits each set in turn"() {
        def set1 = Mock(ResolvedArtifactSet)
        def set2 = Mock(ResolvedArtifactSet)
        def visitor = Mock(ArtifactVisitor)

        when:
        CompositeArtifactSet.of([set1, set2]).visit(visitor)

        then:
        1 * set1.visit(visitor)

        then:
        1 * set2.visit(visitor)
        0 * _
    }
}
