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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.filestore.ivy.ArtifactIdentifierFileStore;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.PersistentModuleSource;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Set;

/**
 * A codec for {@link MetadataFileSource}. This codec is particular because of the persistent cache
 * which must be relocatable. As a consequence, it would be an error to serialize the file path because
 * it would contain an absolute path to the descriptor file.
 *
 * Therefore, the deserialized metadata file source reconstructs the file path from the component
 * module artifact identifier.
 */
public class DefaultMetadataFileSourceCodec implements PersistentModuleSource.Codec<MetadataFileSource> {
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ArtifactIdentifierFileStore fileStore;

    public DefaultMetadataFileSourceCodec(ImmutableModuleIdentifierFactory moduleIdentifierFactory, ArtifactIdentifierFileStore fileStore) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.fileStore = fileStore;
    }

    @Override
    public void encode(MetadataFileSource moduleSource, Encoder encoder) throws IOException {
        ModuleComponentArtifactIdentifier artifactId = moduleSource.getArtifactId();
        ModuleComponentIdentifier componentIdentifier = artifactId.getComponentIdentifier();
        encoder.writeString(componentIdentifier.getGroup());
        encoder.writeString(componentIdentifier.getModule());
        encoder.writeString(componentIdentifier.getVersion());
        IvyArtifactName name = artifactId.getName();
        encoder.writeString(name.getName());
        encoder.writeString(name.getType());
        encoder.writeNullableString(name.getExtension());
        encoder.writeNullableString(name.getClassifier());
        encoder.writeBinary(moduleSource.getSha1().toByteArray());
    }

    @Override
    public MetadataFileSource decode(Decoder decoder) throws IOException {
        String group = decoder.readString();
        String module = decoder.readString();
        String version = decoder.readString();
        IvyArtifactName name = readArtifact(decoder);
        BigInteger sha1 = new BigInteger(decoder.readBinary());
        DefaultMetadataFileSource source = createSource(sha1, group, module, version, name);
        return source;
    }

    private DefaultMetadataFileSource createSource(BigInteger sha1, String group, String module, String version, IvyArtifactName name) {
        DefaultModuleComponentArtifactIdentifier artifactId = createArtifactId(group, module, version, name);
        Set<? extends LocallyAvailableResource> search = fileStore.search(artifactId);
        File metadataFile = null;
        for (LocallyAvailableResource locallyAvailableResource : search) {
            if (locallyAvailableResource.getSha1().asBigInteger().equals(sha1)) {
                metadataFile = locallyAvailableResource.getFile();
                break;
            }
        }
        return new DefaultMetadataFileSource(
            artifactId,
            metadataFile,
            sha1);
    }

    private DefaultModuleComponentArtifactIdentifier createArtifactId(String group, String module, String version, IvyArtifactName name) {
        return new DefaultModuleComponentArtifactIdentifier(
            DefaultModuleComponentIdentifier.newId(
                moduleIdentifierFactory.module(group, module),
                version
            ),
            name
        );
    }

    private IvyArtifactName readArtifact(Decoder decoder) throws IOException {
        String name = decoder.readString();
        String type = decoder.readString();
        String ext = decoder.readNullableString();
        String classy = decoder.readNullableString();
        return new DefaultIvyArtifactName(name, type, ext, classy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return moduleIdentifierFactory.hashCode();
    }
}
