/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.component.ComponentIdentifier
import spock.lang.Specification

/**
 * Tests {@link PublishArtifactLocalArtifactMetadata}
 */
class PublishArtifactLocalArtifactMetadataTest extends Specification {
    def "has useful display name"() {
        def publishArtifact = newPublishArtifact("name", "type", "ext", "classifier", "name-1234.jar")
        def componentId = newComponentId("foo")
        def metadata = new PublishArtifactLocalArtifactMetadata(componentId, publishArtifact)

        expect:
        metadata.getDisplayName() == "name-1234.jar (foo)"
    }

    def "are equal when publish artifact and componentId are equal"() {
        def publishArtifact = newPublishArtifact("name", "type", "ext", "classifier", "name-1234.jar")
        def componentId = newComponentId("foo")

        when:
        def metadata = new PublishArtifactLocalArtifactMetadata(componentId, publishArtifact)
        def same = new PublishArtifactLocalArtifactMetadata(componentId, publishArtifact)

        then:
        metadata == same

        when:
        def differentPublishArtifact = newPublishArtifact("name", "type", "ext", "classifier", "name-1234.jar")
        def differentComponentId = newComponentId("bar")

        def different1 = new PublishArtifactLocalArtifactMetadata(componentId, differentPublishArtifact)
        def different2 = new PublishArtifactLocalArtifactMetadata(differentComponentId, publishArtifact)

        then:
        metadata != different1
        metadata.hashCode() != different1.hashCode()
        metadata != different2
        metadata.hashCode() != different2.hashCode()
    }

    ComponentIdentifier newComponentId(String id) {
        Mock(ComponentIdentifier) {
            getDisplayName() >> id
        }
    }

    PublishArtifact newPublishArtifact(String name, String type, String extension, String classifier, String fileName) {
        Mock(PublishArtifact) {
            getName() >> name
            getExtension() >> extension
            getType() >> type
            getClassifier() >> classifier
            getFile() >> new File(fileName)
        }
    }
}
