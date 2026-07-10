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
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.publish.ImmutablePublishArtifact
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
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
        metadata.getDisplayName() == "name-1234.jar (foo:foo:foo)"
    }

    def "equals and hash code differentiate between same and different instances"() {
        when:
        def metadata = new PublishArtifactLocalArtifactMetadata(newComponentId("foo"), newPublishArtifact("name", "type", "ext", "classifier", "name-1234.jar"))
        def same = new PublishArtifactLocalArtifactMetadata(newComponentId("foo"), newPublishArtifact("name", "type", "ext", "classifier", "name-1234.jar"))

        then:
        metadata == same

        when:
        def different1 = new PublishArtifactLocalArtifactMetadata(newComponentId("foo"), newPublishArtifact("name", "type", "ext", "classifier", "different"))
        def different2 = new PublishArtifactLocalArtifactMetadata(newComponentId("bar"), newPublishArtifact("name", "type", "ext", "classifier", "name-1234.jar"))

        then:
        metadata != different1
        metadata.hashCode() != different1.hashCode()
        metadata != different2
        metadata.hashCode() != different2.hashCode()
    }

    ComponentIdentifier newComponentId(String id) {
        DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(id, id), id);
    }

    PublishArtifact newPublishArtifact(String name, String type, String extension, String classifier, String fileName) {
        new ImmutablePublishArtifact(name, extension, type, classifier, new File(fileName))
    }
}
