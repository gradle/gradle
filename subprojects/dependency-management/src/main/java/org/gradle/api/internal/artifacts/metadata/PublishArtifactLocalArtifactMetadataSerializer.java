/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts.metadata;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.publish.ImmutablePublishArtifact;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;

public class PublishArtifactLocalArtifactMetadataSerializer implements Serializer<PublishArtifactLocalArtifactMetadata> {

    private final ComponentIdentifierSerializer componentIdentifierSerializer;

    public PublishArtifactLocalArtifactMetadataSerializer(ComponentIdentifierSerializer componentIdentifierSerializer) {
        this.componentIdentifierSerializer = componentIdentifierSerializer;
    }

    @Override
    public PublishArtifactLocalArtifactMetadata read(Decoder decoder) throws Exception {
        ComponentIdentifier identifier = componentIdentifierSerializer.read(decoder);
        String artifactName = decoder.readString();
        String artifactExtension = decoder.readString();
        String artifactType = decoder.readString();
        String artifactClassifier = decoder.readNullableString();
        File artifactFile = new File(decoder.readString());
        return new PublishArtifactLocalArtifactMetadata(
            identifier,
            new ImmutablePublishArtifact(artifactName, artifactExtension, artifactType, artifactClassifier, artifactFile)
        );
    }

    @Override
    public void write(Encoder encoder, PublishArtifactLocalArtifactMetadata value) throws Exception {
        componentIdentifierSerializer.write(encoder, value.getComponentIdentifier());
        PublishArtifact publishArtifact = value.getPublishArtifact();
        encoder.writeString(publishArtifact.getName());
        encoder.writeString(publishArtifact.getType());
        encoder.writeString(publishArtifact.getExtension());
        encoder.writeNullableString(publishArtifact.getClassifier());
        encoder.writeString(publishArtifact.getFile().getCanonicalPath());
    }
}
