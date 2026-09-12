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

package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Deferrable
import org.gradle.internal.Try
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.operations.BuildOperation
import org.gradle.internal.operations.BuildOperationQueue
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class TransformingAsyncArtifactListenerTest extends Specification {
    def transformStep = Mock(TransformStep)
    def targetAttributes = Mock(ImmutableAttributes)
    def result = ImmutableList.<ResolvedArtifactSet.Artifacts>builder()
    def operationQueue = Mock(BuildOperationQueue)
    def listener = new TransformingAsyncArtifactListener([new BoundTransformStep(transformStep, Stub(TransformUpstreamDependencies))], targetAttributes, ImmutableCapabilities.EMPTY, result)
    def file = new File("foo")
    def artifactFile = new File("foo-artifact")
    def artifactId = Stub(ComponentArtifactIdentifier)
    def sourceVariantId = Stub(VariantIdentifier)
    def source = Stub(CalculatedValue) {
        isFinalized() >> true
        getValue() >> Try.successful(file)
    }
    def artifact = Stub(ResolvableArtifact) {
        getId() >> artifactId
        getFileSource() >> source
        getFile() >> artifactFile
    }
    def artifacts = Mock(ResolvedArtifactSet.Artifacts)

    def "adds expensive artifact transformations to the build operation queue"() {
        given:
        def pendingInvocation = Deferrable.deferred { throw new AssertionError("The invocation should be queued, not executed") }

        when:
        listener.visitArtifacts(artifacts)
        def artifacts = result.build()

        then:
        artifacts.size() == 1
        1 * artifacts.visit(_) >> { ArtifactVisitor visitor -> visitor.visitArtifact(null, sourceVariantId, null, ImmutableCapabilities.EMPTY, artifact) }
        0 * _

        when:
        artifacts[0].startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation(_, _, _) >> pendingInvocation
        1 * operationQueue.add(_ as BuildOperation)
    }

    def "runs cheap artifact transformations immediately when not scheduled"() {
        given:
        def completedInvocation = Deferrable.completed(Try.successful(TransformStepSubject.initial(artifact)))

        when:
        listener.visitArtifacts(artifacts)
        def artifacts = result.build()

        then:
        artifacts.size() == 1
        1 * artifacts.visit(_) >> { ArtifactVisitor visitor -> visitor.visitArtifact(null, sourceVariantId, null, ImmutableCapabilities.EMPTY, artifact) }
        0 * _

        when:
        artifacts[0].startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation({ it.files == [this.artifactFile] }, _ as TransformUpstreamDependencies, _) >> completedInvocation
        0 * operationQueue._
        !artifacts[0].hasPendingInvocation()
    }

    def "propagates the input artifact failure without queueing the transformation"() {
        given:
        def failure = new RuntimeException("broken")
        def failedSource = Stub(CalculatedValue) {
            isFinalized() >> true
            getValue() >> Try.failure(failure)
        }
        def failedArtifact = Stub(ResolvableArtifact) {
            getId() >> artifactId
            getFileSource() >> failedSource
        }
        def visitor = Mock(ArtifactVisitor)

        when:
        listener.visitArtifacts(artifacts)
        def transformed = result.build()

        then:
        transformed.size() == 1
        1 * artifacts.visit(_) >> { ArtifactVisitor v -> v.visitArtifact(null, sourceVariantId, null, ImmutableCapabilities.EMPTY, failedArtifact) }
        0 * _

        when:
        transformed[0].startFinalization(operationQueue, true)

        then:
        0 * transformStep._
        0 * operationQueue._

        when:
        transformed[0].visit(visitor)

        then:
        1 * visitor.visitFailure({ it instanceof TransformException && it.cause == failure })
        0 * _
    }

    def "creates the invocation only once under concurrent finalization"() {
        given:
        def outputArtifact = Stub(ResolvableArtifact)
        def artifact = Stub(ResolvableArtifact) {
            getId() >> artifactId
            getFileSource() >> source
            getFile() >> artifactFile
            transformedTo(_) >> outputArtifact
        }
        def invocationCreations = new AtomicInteger()
        def invocationExecutions = new AtomicInteger()
        def visitor = Mock(ArtifactVisitor)
        transformStep.createInvocation(_, _, _) >> {
            invocationCreations.incrementAndGet()
            // Give racing threads a chance to attempt a duplicate creation
            Thread.sleep(10)
            Deferrable.deferred {
                invocationExecutions.incrementAndGet()
                Try.successful(TransformStepSubject.initial(artifact))
            }
        }

        when:
        listener.visitArtifacts(artifacts)
        def transformed = result.build()

        then:
        transformed.size() == 1
        1 * artifacts.visit(_) >> { ArtifactVisitor v -> v.visitArtifact(null, sourceVariantId, null, ImmutableCapabilities.EMPTY, artifact) }
        0 * _

        when:
        def threads = (1..8).collect {
            Thread.start {
                transformed[0].startFinalization(operationQueue, true)
                transformed[0].run(null)
                transformed[0].visit(visitor)
            }
        }
        threads*.join(30_000)

        then:
        threads.every { !it.alive }
        invocationCreations.get() == 1
        invocationExecutions.get() == 1
        8 * visitor.visitArtifact(null, sourceVariantId, targetAttributes, ImmutableCapabilities.EMPTY, outputArtifact)
        0 * visitor.visitFailure(_)
        !transformed[0].hasPendingInvocation()
    }
}
