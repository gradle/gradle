/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.metadata

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.publish.ImmutablePublishArtifact
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.serialize.SerializerSpec

class PublishArtifactLocalArtifactMetadataSerializerTest extends SerializerSpec {
    PublishArtifactLocalArtifactMetadataSerializer serializer = new PublishArtifactLocalArtifactMetadataSerializer(new ComponentIdentifierSerializer())

    def "round trips artifact metadata with different extension and type"() {
        given:
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def artifact = new ImmutablePublishArtifact("name", "ext", "type", "classifier", new File("some-file.ext").absoluteFile)
        def metadata = new PublishArtifactLocalArtifactMetadata(componentId, artifact)

        when:
        PublishArtifactLocalArtifactMetadata result = serialize(metadata, serializer)

        then:
        result.componentIdentifier == componentId
        result.publishArtifact.name == "name"
        result.publishArtifact.extension == "ext"
        result.publishArtifact.type == "type"
        result.publishArtifact.classifier == "classifier"
        result.publishArtifact.file == artifact.file
    }
}
