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

import com.google.common.collect.Maps
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.internal.operations.BuildOperation
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.testing.internal.util.Specification

class TransformingAsyncArtifactListenerTest extends Specification {
    def transformer = Mock(ArtifactTransformation)
    def operationQueue = Mock(BuildOperationQueue)
    def transformListener = Mock(ArtifactTransformListener)
    def listener  = new TransformingAsyncArtifactListener(transformer, null, operationQueue, Maps.newHashMap(), Maps.newHashMap(), transformListener)
    def file = new File("foo")
    def artifactFile = new File("foo-artifact")
    def artifactId = Stub(ComponentArtifactIdentifier)
    def artifact = Stub(ResolvableArtifact) {
        getId() >> artifactId
        getArtifactFile() >> artifactFile
    }

    def "runs transforms in parallel if no cached result is available"() {
        given:
        transformer.hasCachedResult(_ as File) >> false

        when:
        listener.artifactAvailable(artifact)

        then:
        1 * operationQueue.add(_ as BuildOperation)

        when:
        listener.fileAvailable(file)

        then:
        1 * operationQueue.add(_ as BuildOperation)
    }

    def "runs transforms immediately if the result is already cached"() {
        given:
        transformer.hasCachedResult(_ as File) >> true

        when:
        listener.artifactAvailable(artifact)

        then:
        0 * transformListener.beforeTransform(transformer, artifactId, artifactFile)
        1 * transformer.transform(artifactFile)
        0 * transformListener.afterTransform(transformer, artifactId, artifactFile, null)

        when:
        listener.fileAvailable(file)

        then:
        0 * transformListener.beforeTransform(transformer, null, file)
        1 * transformer.transform(file)
        0 * transformListener.afterTransform(transformer, null, file, null)
    }
}
