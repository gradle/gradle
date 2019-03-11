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
import com.google.common.collect.Maps
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.internal.Try
import org.gradle.internal.operations.BuildOperation
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.testing.internal.util.Specification

class TransformingAsyncArtifactListenerTest extends Specification {
    def transformation = Mock(Transformation)
    def operationQueue = Mock(BuildOperationQueue)
    def transformationNodeRegistry = Mock(TransformationNodeRegistry)
    def listener  = new TransformingAsyncArtifactListener(transformation, null, operationQueue, Maps.newHashMap(), Maps.newHashMap(), Mock(ExecutionGraphDependenciesResolver), transformationNodeRegistry)
    def file = new File("foo")
    def artifactFile = new File("foo-artifact")
    def artifactId = Stub(ComponentArtifactIdentifier)
    def artifact = Stub(ResolvableArtifact) {
        getId() >> artifactId
        getFile() >> artifactFile
    }
    def node = Mock(TransformationNode)

    def "adds file transformations to the build operation queue"() {
        when:
        listener.fileAvailable(file)

        then:
        1 * operationQueue.add(_ as BuildOperation)
    }

    def "runs artifact transformations immediately when not scheduled"() {
        when:
        listener.artifactAvailable(artifact)

        then:
        1 * transformationNodeRegistry.getCompleted(artifactId, transformation) >> Optional.empty()
        1 * transformation.transform({ it.files == [artifactFile] }, _ as ExecutionGraphDependenciesResolver, _)
    }

    def "re-uses scheduled artifact transformation result"() {
        when:
        listener.artifactAvailable(artifact)

        then:
        1 * transformationNodeRegistry.getCompleted(artifactId, transformation) >> Optional.of(node)
        1 * node.getTransformedSubject() >> Try.successful(TransformationSubject.initial(artifact.id, artifact.file).createSubjectFromResult(ImmutableList.of()))
        0 * transformation.transform(_, _ as ExecutionGraphDependenciesResolver, _)
    }
}
