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
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.operations.BuildOperation
import org.gradle.internal.operations.BuildOperationQueue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

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
        1 * transformStep.createInvocation(_, _, _) >> TransformInvocation.deferred({ throw new AssertionError("must not execute while preparing") } as Supplier)
        1 * operationQueue.add(_ as BuildOperation)
    }

    def "runs cheap artifact transformations immediately when not scheduled"() {
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
        1 * transformStep.createInvocation({ it.files == [this.artifactFile] }, _ as TransformUpstreamDependencies, _) >> TransformInvocation.known(Try.successful(TransformStepSubject.initial(artifact)))
        0 * operationQueue._
    }

    def "takes the result of a chain immediately when every step is known"() {
        def secondStep = Mock(TransformStep)
        def transformedArtifact = transformedArtifactOfChain(secondStep)
        def intermediate = subjectWithFile("intermediate")
        def visitor = Mock(ArtifactVisitor)

        when:
        transformedArtifact.startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation(_, _, _) >> TransformInvocation.known(Try.successful(intermediate))
        1 * secondStep.createInvocation(intermediate, _, _) >> TransformInvocation.known(Try.successful(subjectWithFile("output")))
        0 * operationQueue._

        when:
        transformedArtifact.visit(visitor)

        then:
        1 * visitor.visitArtifact(_, _, _, _, _)
        0 * transformStep.createInvocation(_, _, _)
        0 * secondStep.createInvocation(_, _, _)
    }

    def "queues a chain at its first step and creates later steps only when it runs"() {
        def secondStep = Mock(TransformStep)
        def transformedArtifact = transformedArtifactOfChain(secondStep)
        def intermediate = subjectWithFile("intermediate")
        def visitor = Mock(ArtifactVisitor)
        def queued = null

        when:
        transformedArtifact.startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation(_, _, _) >> TransformInvocation.deferred({ Try.successful(intermediate) } as Supplier)
        0 * secondStep.createInvocation(_, _, _)
        1 * operationQueue.add(_) >> { BuildOperation operation -> queued = operation }
        transformedArtifact.@invocation != null

        when:
        queued.run(null)

        then:
        1 * secondStep.createInvocation(intermediate, _, _) >> TransformInvocation.known(Try.successful(subjectWithFile("output")))
        transformedArtifact.@invocation == null

        when:
        transformedArtifact.visit(visitor)

        then:
        1 * visitor.visitArtifact(_, _, _, _, _)
        0 * transformStep.createInvocation(_, _, _)
        0 * secondStep.createInvocation(_, _, _)
    }

    def "queues a chain at a later step whose result is not known"() {
        def secondStep = Mock(TransformStep)
        def transformedArtifact = transformedArtifactOfChain(secondStep)
        def intermediate = subjectWithFile("intermediate")
        def secondStepWork = Mock(Supplier)
        def queued = null

        when:
        transformedArtifact.startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation(_, _, _) >> TransformInvocation.known(Try.successful(intermediate))
        1 * secondStep.createInvocation(intermediate, _, _) >> TransformInvocation.deferred(secondStepWork)
        0 * secondStepWork._
        1 * operationQueue.add(_) >> { BuildOperation operation -> queued = operation }

        when:
        queued.run(null)

        then:
        1 * secondStepWork.get() >> Try.successful(subjectWithFile("output"))
        0 * transformStep.createInvocation(_, _, _)
        0 * secondStep.createInvocation(_, _, _)
    }

    def "stops a chain at a step that is known to fail"() {
        def secondStep = Mock(TransformStep)
        def transformedArtifact = transformedArtifactOfChain(secondStep)
        def visitor = Mock(ArtifactVisitor)

        when:
        transformedArtifact.startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation(_, _, _) >> TransformInvocation.known(Try.failure(new RuntimeException("broken")))
        0 * secondStep.createInvocation(_, _, _)
        0 * operationQueue._

        when:
        transformedArtifact.visit(visitor)

        then:
        1 * visitor.visitFailure(_)
    }

    def "stops a chain at a step that fails when it runs"() {
        def secondStep = Mock(TransformStep)
        def transformedArtifact = transformedArtifactOfChain(secondStep)
        def visitor = Mock(ArtifactVisitor)
        def queued = null

        when:
        transformedArtifact.startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation(_, _, _) >> TransformInvocation.deferred({ Try.failure(new RuntimeException("broken")) } as Supplier)
        1 * operationQueue.add(_) >> { BuildOperation operation -> queued = operation }

        when:
        queued.run(null)
        transformedArtifact.visit(visitor)

        then:
        0 * secondStep.createInvocation(_, _, _)
        1 * visitor.visitFailure(_)
    }

    @Timeout(30)
    def "runs the rest of a chain once when the stored invocation is finalized concurrently"() {
        def secondStep = Mock(TransformStep)
        def transformedArtifact = transformedArtifactOfChain(secondStep)
        def intermediate = subjectWithFile("intermediate")
        def visitor = Stub(ArtifactVisitor)
        def firstStepStarted = new CountDownLatch(1)
        def releaseFirstStep = new CountDownLatch(1)
        def firstStepRuns = new AtomicInteger()
        def queued = null
        transformStep.createInvocation(_, _, _) >> TransformInvocation.deferred({
            firstStepRuns.incrementAndGet()
            firstStepStarted.countDown()
            releaseFirstStep.await()
            Try.successful(intermediate)
        } as Supplier)
        operationQueue.add(_) >> { BuildOperation operation -> queued = operation }
        transformedArtifact.startFinalization(operationQueue, true)

        when:
        def running = Thread.start { queued.run(null) }
        firstStepStarted.await()
        def visiting = Thread.start { transformedArtifact.visit(visitor) }
        waitUntilWaiting(visiting)
        releaseFirstStep.countDown()
        running.join()
        visiting.join()

        then:
        1 * secondStep.createInvocation(intermediate, _, _) >> TransformInvocation.known(Try.successful(subjectWithFile("output")))
        firstStepRuns.get() == 1
    }

    def "does not queue an artifact that was finalized while its chain was being created"() {
        def secondStep = Mock(TransformStep)
        def transformedArtifact = transformedArtifactOfChain(secondStep)
        def visitor = Mock(ArtifactVisitor)
        def calls = 0

        when:
        transformedArtifact.startFinalization(operationQueue, true)

        then:
        2 * transformStep.createInvocation(_, _, _) >> {
            if (++calls == 1) {
                transformedArtifact.visit(visitor)
                return TransformInvocation.deferred({ throw new AssertionError("must not run") } as Supplier)
            }
            return TransformInvocation.known(Try.successful(subjectWithFile("intermediate")))
        }
        1 * secondStep.createInvocation(_, _, _) >> TransformInvocation.known(Try.successful(subjectWithFile("output")))
        1 * visitor.visitArtifact(_, _, _, _, _)
        0 * operationQueue._
        transformedArtifact.@invocation == null
    }

    private ResolvedArtifactSet.Artifacts transformedArtifactOfChain(TransformStep secondStep) {
        def steps = [new BoundTransformStep(transformStep, Stub(TransformUpstreamDependencies)), new BoundTransformStep(secondStep, Stub(TransformUpstreamDependencies))]
        def chainResult = ImmutableList.<ResolvedArtifactSet.Artifacts>builder()
        def artifactSet = Stub(ResolvedArtifactSet.Artifacts) {
            visit(_) >> { ArtifactVisitor visitor -> visitor.visitArtifact(null, sourceVariantId, null, ImmutableCapabilities.EMPTY, artifact) }
        }
        new TransformingAsyncArtifactListener(steps, targetAttributes, ImmutableCapabilities.EMPTY, chainResult).visitArtifacts(artifactSet)
        return chainResult.build()[0]
    }

    private static void waitUntilWaiting(Thread thread) {
        while (thread.state != Thread.State.BLOCKED && thread.state != Thread.State.WAITING) {
            Thread.sleep(10)
        }
    }

    private TransformStepSubject subjectWithFile(String name) {
        return TransformStepSubject.initial(artifact).createSubjectFromResult(ImmutableList.of(new File(name)))
    }
}
