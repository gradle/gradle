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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Deferrable
import org.gradle.internal.Try
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.operations.BuildOperation
import org.gradle.internal.operations.BuildOperationQueue
import spock.lang.Specification

class TransformingAsyncArtifactListenerTest extends Specification {
    def transformStep = Mock(TransformStep)
    def targetAttributes = Mock(ImmutableAttributes)
    def result = ImmutableList.<ResolvedArtifactSet.Artifacts>builder()
    def invocation = Mock(Deferrable<TransformStepSubject>)
    def operationQueue = Mock(BuildOperationQueue)
    def listener = new TransformingAsyncArtifactListener([new BoundTransformStep(transformStep, Stub(TransformUpstreamDependencies))], targetAttributes, [], result)
    def file = new File("foo")
    def artifactFile = new File("foo-artifact")
    def artifactId = Stub(ComponentArtifactIdentifier)
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
        1 * artifacts.visit(_) >> { ArtifactVisitor visitor -> visitor.visitArtifact(null, null, [], artifact) }
        0 * _

        when:
        artifacts[0].startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation(_, _, _) >> invocation
        1 * invocation.getCompleted() >> Optional.empty()
        1 * operationQueue.add(_ as BuildOperation)
    }

    def "runs cheap artifact transformations immediately when not scheduled"() {
        when:
        listener.visitArtifacts(artifacts)
        def artifacts = result.build()

        then:
        artifacts.size() == 1
        1 * artifacts.visit(_) >> { ArtifactVisitor visitor -> visitor.visitArtifact(null, null, [], artifact) }
        0 * _

        when:
        artifacts[0].startFinalization(operationQueue, true)

        then:
        1 * transformStep.createInvocation({ it.files == [this.artifactFile] }, _ as TransformUpstreamDependencies, _) >> invocation
        2 * invocation.getCompleted() >> Optional.of(Try.successful(TransformStepSubject.initial(artifact)))
        0 * operationQueue._
    }
}
