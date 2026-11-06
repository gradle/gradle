/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.metadata;

import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifierSerializer;
import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.model.PersistentModuleSource;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.File;
import java.io.IOException;

/**
 * A codec for {@link MetadataFileSource}. This codec is particular because of the persistent cache
 * which must be relocatable. As a consequence, it would be an error to serialize the file path because
 * it would contain an absolute path to the descriptor file.
 *
 * Therefore, the deserialized metadata file source reconstructs the file path from the component
 * module artifact identifier.
 */
public class DefaultMetadataFileSourceCodec implements PersistentModuleSource.Codec<MetadataFileSource> {
    private final ComponentArtifactIdentifierSerializer artifactIdSerializer;
    private final ArtifactIdentifierFileStore fileStore;

    public DefaultMetadataFileSourceCodec(ComponentArtifactIdentifierSerializer artifactIdSerializer, ArtifactIdentifierFileStore fileStore) {
        this.artifactIdSerializer = artifactIdSerializer;
        this.fileStore = fileStore;
    }

    @Override
    public void encode(MetadataFileSource moduleSource, Encoder encoder) throws IOException {
        artifactIdSerializer.write(encoder, moduleSource.getArtifactId());
        encoder.writeBinary(moduleSource.getSha1().toByteArray());
    }

    @Override
    public MetadataFileSource decode(Decoder decoder) throws IOException {
        ModuleComponentArtifactIdentifier artifactId = (ModuleComponentArtifactIdentifier) artifactIdSerializer.read(decoder);
        HashCode sha1 = HashCode.fromBytes(decoder.readBinary());
        File metadataFile = fileStore.whereIs(artifactId, sha1.toString());
        return new DefaultMetadataFileSource(
            artifactId,
            metadataFile,
            sha1
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
