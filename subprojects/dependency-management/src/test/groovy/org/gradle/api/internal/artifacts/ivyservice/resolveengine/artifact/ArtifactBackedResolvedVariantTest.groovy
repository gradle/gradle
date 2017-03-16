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
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.tasks.TaskDependency
import spock.lang.Specification

class ArtifactBackedResolvedVariantTest extends Specification {
    def variant = Mock(AttributeContainerInternal)
    def artifact1 = Mock(TestArtifact)
    def artifact2 = Mock(TestArtifact)

    def "factory method returns specialized sets for zero and one elements"() {
        expect:
        of([]) instanceof EmptyResolvedVariant
        of([artifact1]) instanceof ArtifactBackedResolvedVariant.SingleArtifactResolvedVariant
        of([artifact1, artifact2]) instanceof ArtifactBackedResolvedVariant
    }

    def "visits artifacts and retains order"() {
        def visitor = Mock(ArtifactVisitor)
        def set1 = of([artifact1, artifact2])
        def set2 = of([artifact1])

        when:
        set1.visit(visitor)

        then:
        1 * visitor.visitArtifact(variant, artifact1)

        then:
        1 * visitor.visitArtifact(variant, artifact2)
        0 * _

        when:
        set2.visit(visitor)

        then:
        1 * visitor.visitArtifact(variant, artifact1)
        0 * _
    }

    def "collects build dependencies"() {
        def deps1 = Stub(TaskDependency)
        def deps2 = Stub(TaskDependency)
        def set1 = of([artifact1, artifact2])
        def set2 = of([artifact1])

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

    ResolvedVariant of(artifacts) {
        return ArtifactBackedResolvedVariant.create(variant, artifacts)
    }

    interface TestArtifact extends ResolvedArtifact, Buildable { }

}
