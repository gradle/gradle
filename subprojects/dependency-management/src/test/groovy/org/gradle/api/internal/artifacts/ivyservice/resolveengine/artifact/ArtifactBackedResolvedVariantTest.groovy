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
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.Describables
import org.gradle.internal.Try
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Specification

class ArtifactBackedResolvedVariantTest extends Specification {
    def attributes = Mock(AttributeContainerInternal)
    def variantDisplayName = Describables.of("<variant>")
    def id = Mock(VariantResolveMetadata.Identifier)
    def queue = new TestBuildOperationExecutor.TestBuildOperationQueue()
    def artifact1 = Mock(TestArtifact)
    def artifact2 = Mock(TestArtifact)

    def "visits empty variant"() {
        def visitor = Mock(ResolvedArtifactSet.Visitor)
        def set1 = of([])

        when:
        set1.artifacts.visit(visitor)

        then:
        0 * _
    }

    def "visits local artifacts of empty variant"() {
        def visitor = Mock(ResolvedArtifactSet.TransformSourceVisitor)
        def set1 = of([])

        when:
        set1.artifacts.visitTransformSources(visitor)

        then:
        0 * _
    }

    def "visits artifacts and retains order when artifact files are not required"() {
        def artifactVisitor = Mock(ArtifactVisitor)
        def visitor = Mock(ResolvedArtifactSet.Visitor)
        def set1 = of([artifact1, artifact2])
        def set2 = of([artifact1])

        when:
        set1.artifacts.visit(visitor)

        then:
        1 * visitor.visitArtifacts(_) >> { ResolvedArtifactSet.Artifacts artifacts ->
            artifacts.startFinalization(queue, false)
            artifacts.visit(artifactVisitor)
        }
        1 * artifactVisitor.requireArtifactFiles() >> false
        1 * artifactVisitor.visitArtifact(variantDisplayName, attributes, [], artifact1)
        1 * artifactVisitor.endVisitCollection(FileCollectionInternal.OTHER) // each artifact is treated as a separate collection, the entire variant could instead be treated as a collection

        then:
        1 * visitor.visitArtifacts(_) >> { ResolvedArtifactSet.Artifacts artifacts ->
            artifacts.startFinalization(queue, false)
            artifacts.visit(artifactVisitor)
        }
        1 * artifactVisitor.requireArtifactFiles() >> false
        1 * artifactVisitor.visitArtifact(variantDisplayName, attributes, [], artifact2)
        1 * artifactVisitor.endVisitCollection(FileCollectionInternal.OTHER)
        0 * _

        when:
        set2.artifacts.visit(visitor)

        then:
        1 * visitor.visitArtifacts(_) >> { ResolvedArtifactSet.Artifacts artifacts ->
            artifacts.startFinalization(queue, false)
            artifacts.visit(artifactVisitor)
        }
        1 * artifactVisitor.requireArtifactFiles() >> false
        1 * artifactVisitor.visitArtifact(variantDisplayName, attributes, [], artifact1)
        1 * artifactVisitor.endVisitCollection(FileCollectionInternal.OTHER)
        0 * _
    }

    def "visits artifacts when artifact files are required"() {
        def artifactVisitor = Mock(ArtifactVisitor)
        def visitor = Mock(ResolvedArtifactSet.Visitor)
        def f1 = new File("f1")
        def f2 = new File("f2")
        def source1 = Mock(CalculatedValue)
        def source2 = Mock(CalculatedValue)
        def set1 = of([artifact1, artifact2])
        def set2 = of([artifact1])

        when:
        set1.artifacts.visit(visitor)

        then:
        _ * artifact1.id >> Stub(ComponentArtifactIdentifier)
        1 * artifact1.resolveSynchronously >> false
        _ * artifact1.fileSource >> source1
        1 * source1.finalizeIfNotAlready()
        _ * source1.value >> Try.successful(f1)
        1 * visitor.visitArtifacts(_) >> { ResolvedArtifactSet.Artifacts artifacts ->
            artifacts.startFinalization(queue, true)
            artifacts.visit(artifactVisitor)
        }
        1 * artifactVisitor.requireArtifactFiles() >> true
        1 * artifactVisitor.visitArtifact(variantDisplayName, attributes, [], artifact1)
        1 * artifactVisitor.endVisitCollection(FileCollectionInternal.OTHER)

        then:
        _ * artifact2.id >> Stub(ComponentArtifactIdentifier)
        1 * artifact2.resolveSynchronously >> false
        _ * artifact2.fileSource >> source2
        1 * source2.finalizeIfNotAlready()
        _ * source2.value >> Try.successful(f2)
        1 * visitor.visitArtifacts(_) >> { ResolvedArtifactSet.Artifacts artifacts ->
            artifacts.startFinalization(queue, true)
            artifacts.visit(artifactVisitor)
        }
        1 * artifactVisitor.requireArtifactFiles() >> true
        1 * artifactVisitor.visitArtifact(variantDisplayName, attributes, [], artifact2)
        1 * artifactVisitor.endVisitCollection(FileCollectionInternal.OTHER)
        0 * _

        when:
        set2.artifacts.visit(visitor)

        then:
        _ * artifact1.id >> Stub(ComponentArtifactIdentifier)
        1 * artifact1.resolveSynchronously >> false
        _ * artifact1.fileSource >> source1
        1 * source1.finalizeIfNotAlready()
        _ * source1.value >> Try.successful(f1)
        1 * visitor.visitArtifacts(_) >> { ResolvedArtifactSet.Artifacts artifacts ->
            artifacts.startFinalization(queue, true)
            artifacts.visit(artifactVisitor)
        }
        1 * artifactVisitor.requireArtifactFiles() >> true
        1 * artifactVisitor.visitArtifact(variantDisplayName, attributes, [], artifact1)
        1 * artifactVisitor.endVisitCollection(FileCollectionInternal.OTHER)
        0 * _
    }

    def "visits local artifacts"() {
        def visitor = Mock(ResolvedArtifactSet.TransformSourceVisitor)
        def set1 = of([artifact1, artifact2])
        def set2 = of([artifact1])

        when:
        set1.artifacts.visitTransformSources(visitor)

        then:
        1 * artifact1.id >> new ComponentFileArtifactIdentifier(Stub(ProjectComponentIdentifier), "some-file")
        1 * artifact2.id >> new ComponentFileArtifactIdentifier(Stub(ModuleComponentIdentifier), "some-file")
        1 * visitor.visitArtifact(artifact1)
        0 * _

        when:
        set2.artifacts.visitTransformSources(visitor)

        then:
        1 * artifact1.id >> new ComponentFileArtifactIdentifier(Stub(ProjectComponentIdentifier), "some-file")
        1 * visitor.visitArtifact(artifact1)
        0 * _
    }

    def "collects build dependencies"() {
        def visitor = Mock(TaskDependencyResolveContext)
        def set1 = of([artifact1, artifact2])
        def set2 = of([artifact1])

        when:
        set1.artifacts.visitDependencies(visitor)

        then:
        1 * visitor.add(artifact1)
        1 * visitor.add(artifact2)
        0 * visitor._

        when:
        set2.artifacts.visitDependencies(visitor)

        then:
        1 * visitor.add(artifact1)
        0 * visitor._
    }

    ResolvedVariant of(List<ResolvableArtifact> artifacts) {
        return new ArtifactBackedResolvedVariant(id, variantDisplayName, attributes, ImmutableCapabilities.EMPTY, [], { artifacts as Set })
    }

    interface TestArtifact extends ResolvableArtifact, Buildable {}

}
