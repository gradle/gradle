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
import org.gradle.internal.Try
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.operations.BuildOperation
import org.gradle.internal.operations.BuildOperationQueue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TransformingAsyncArtifactListenerTest extends Specification {
    def transformStep = Mock(TransformStep)
    def targetAttributes = Mock(ImmutableAttributes)
    def result = ImmutableList.<ResolvedArtifactSet.Artifacts>builder()
    def invocation = Mock(Lazy<Try<TransformStepSubject>>)
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

    def "adds expensive artifact transformations to the build operation queue"() {
        given:
        def transformedArtifact = transformedArtifactFor(artifact)

        when:
        transformedArtifact.startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation(_, _, _) >> invocation
        1 * invocation.isInitialized() >> false
        1 * operationQueue.add(_ as BuildOperation)
    }

    def "runs cheap artifact transformations immediately when not scheduled"() {
        given:
        def transformedArtifact = transformedArtifactFor(artifact)

        when:
        transformedArtifact.startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation({ it.files == [this.artifactFile] }, _ as TransformUpstreamDependencies, _) >> invocation
        1 * invocation.isInitialized() >> true
        1 * invocation.get() >> Try.successful(TransformStepSubject.initial(artifact))
        0 * operationQueue._

        and: "the invocation is released once the result is stored"
        !transformedArtifact.hasPendingInvocation()
    }

    def "propagates the input artifact failure without creating an invocation"() {
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
        def transformedArtifact = transformedArtifactFor(failedArtifact)

        when: "the artifact is finalized"
        transformedArtifact.startFinalization(operationQueue, true)

        then: "no invocation is created or queued"
        0 * transformStep._
        0 * operationQueue._
        !transformedArtifact.hasPendingInvocation()

        when: "the artifact is visited"
        transformedArtifact.visit(visitor)

        then: "the input failure is reported as the cause of the transform failure"
        1 * visitor.visitFailure({ it instanceof TransformException && it.cause == failure })
        0 * _
    }

    @Timeout(60)
    def "drops an invocation created while another thread stores the result"() {
        def creations = new AtomicInteger()
        def creationStarted = new CountDownLatch(1)
        def resultStored = new CountDownLatch(1)
        transformStep.createInvocation(_, _, _) >> {
            if (creations.incrementAndGet() == 1) {
                creationStarted.countDown()
                resultStored.await()
            }
            Lazy.locking().of { Try.successful(TransformStepSubject.initial(artifact)) }
        }
        def executor = Executors.newSingleThreadExecutor()
        def transformedArtifact = transformedArtifactFor(artifact)

        when: "a result is stored while another thread is still creating its invocation"
        def finalization = executor.submit({ transformedArtifact.startFinalization(operationQueue, true) } as Runnable)
        creationStarted.await()
        transformedArtifact.run(null)
        resultStored.countDown()
        finalization.get(1, TimeUnit.MINUTES)

        then: "the late invocation is neither kept nor queued"
        creations.get() == 2
        0 * operationQueue._
        !transformedArtifact.hasPendingInvocation()

        cleanup:
        executor.shutdownNow()
    }

    @Timeout(60)
    def "uses a result stored by another thread instead of running the transform again"() {
        def finalizations = new AtomicInteger()
        def finalizationStarted = new CountDownLatch(1)
        def resultStored = new CountDownLatch(1)
        def blockingSource = Stub(CalculatedValue) {
            getValue() >> Try.successful(file)
            finalizeIfNotAlready() >> {
                if (finalizations.incrementAndGet() == 1) {
                    finalizationStarted.countDown()
                    resultStored.await()
                }
            }
        }
        def blockingArtifact = Stub(ResolvableArtifact) {
            getId() >> artifactId
            getFileSource() >> blockingSource
            getFile() >> artifactFile
        }
        def creations = new AtomicInteger()
        transformStep.createInvocation(_, _, _) >> {
            creations.incrementAndGet()
            Lazy.locking().of { Try.successful(TransformStepSubject.initial(blockingArtifact)) }
        }
        def executor = Executors.newSingleThreadExecutor()
        def transformedArtifact = transformedArtifactFor(blockingArtifact)

        when: "a result is stored while another thread is between its checks for a result"
        def finalization = executor.submit({ transformedArtifact.run(null) } as Runnable)
        finalizationStarted.await()
        transformedArtifact.run(null)
        resultStored.countDown()
        finalization.get(1, TimeUnit.MINUTES)

        then: "the other thread uses the stored result instead of creating another invocation"
        creations.get() == 1
        !transformedArtifact.hasPendingInvocation()

        cleanup:
        executor.shutdownNow()
    }

    private TransformingAsyncArtifactListener.TransformedArtifact transformedArtifactFor(ResolvableArtifact artifactToTransform) {
        def artifacts = Stub(ResolvedArtifactSet.Artifacts) {
            visit(_) >> { ArtifactVisitor visitor -> visitor.visitArtifact(null, sourceVariantId, null, ImmutableCapabilities.EMPTY, artifactToTransform) }
        }
        listener.visitArtifacts(artifacts)
        def transformed = result.build()
        assert transformed.size() == 1
        return transformed[0] as TransformingAsyncArtifactListener.TransformedArtifact
    }
}
