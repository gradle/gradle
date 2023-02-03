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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.metadata.PublishArtifactLocalArtifactMetadataSerializer;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.File;

public class CompositeProjectComponentArtifactMetadataSerializer implements Serializer<CompositeProjectComponentArtifactMetadata> {

    private final ComponentIdentifierSerializer componentIdentifierSerializer = new ComponentIdentifierSerializer();
    private final PublishArtifactLocalArtifactMetadataSerializer publishArtifactLocalArtifactMetadataSerializer = new PublishArtifactLocalArtifactMetadataSerializer(componentIdentifierSerializer);

    @Override
    public CompositeProjectComponentArtifactMetadata read(Decoder decoder) throws Exception {
        ProjectComponentIdentifier componentIdentifier = (ProjectComponentIdentifier) componentIdentifierSerializer.read(decoder);
        PublishArtifactLocalArtifactMetadata delegate = publishArtifactLocalArtifactMetadataSerializer.read(decoder);
        File file = new File(decoder.readString());
        return new CompositeProjectComponentArtifactMetadata(componentIdentifier, delegate, file);
    }

    @Override
    public void write(Encoder encoder, CompositeProjectComponentArtifactMetadata value) throws Exception {
        componentIdentifierSerializer.write(encoder, value.getComponentIdentifier());
        publishArtifactLocalArtifactMetadataSerializer.write(encoder, (PublishArtifactLocalArtifactMetadata) value.getDelegate());
        encoder.writeString(value.getFile().getCanonicalPath());
    }
}
