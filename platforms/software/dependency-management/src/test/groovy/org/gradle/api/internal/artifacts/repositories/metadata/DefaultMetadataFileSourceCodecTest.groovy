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

package org.gradle.api.internal.artifacts.repositories.metadata

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifierSerializer
import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerSpec

class DefaultMetadataFileSourceCodecTest extends SerializerSpec {
    static final def COMPONENT_ID = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.0")

    def metadataFile = new File("metadata")
    def fileStore = Stub(ArtifactIdentifierFileStore) {
        whereIs(_, _) >> metadataFile
    }
    def codec = new DefaultMetadataFileSourceCodec(new ComponentArtifactIdentifierSerializer(new ComponentIdentifierSerializer()), fileStore)
    def serializer = new Serializer<MetadataFileSource>() {
        @Override
        MetadataFileSource read(Decoder decoder) {
            codec.decode(decoder)
        }

        @Override
        void write(Encoder encoder, MetadataFileSource value) {
            codec.encode(value, encoder)
        }
    }

    def "round trips #description"() {
        given:
        def sha1 = TestHashCodes.hashCodeFrom(1234)
        def source = new DefaultMetadataFileSource(artifactId, new File("original"), sha1)

        when:
        def result = serialize(source, serializer)

        then:
        result.artifactId == artifactId
        result.artifactId.class == artifactId.class
        result.artifactId.hashCode() == artifactId.hashCode()
        result.artifactId.filePath == artifactId.filePath
        result.artifactId.destinationFileName == artifactId.destinationFileName
        result.sha1 == sha1
        result.artifactFile == metadataFile

        where:
        description                               | artifactId
        "ivy descriptor"                          | new DefaultModuleComponentArtifactIdentifier(COMPONENT_ID, "ivy", "ivy", "xml", null)
        "pom"                                     | new DefaultModuleComponentArtifactIdentifier(COMPONENT_ID, "module", "pom", "pom", null)
        "file with path matching file name"       | new ModuleComponentFileArtifactIdentifier(COMPONENT_ID, "module-1.0.module")
        "file with path different from file name" | new ModuleComponentFileArtifactIdentifier(COMPONENT_ID, "module-1.0.module", "dir/module.module")
    }
}
