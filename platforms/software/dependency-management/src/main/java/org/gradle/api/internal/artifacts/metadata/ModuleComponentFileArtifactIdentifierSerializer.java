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

package org.gradle.api.internal.artifacts.metadata;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;

/**
 * A thread-safe and reusable serializer for {@link ModuleComponentFileArtifactIdentifier}.
 */
public class ModuleComponentFileArtifactIdentifierSerializer implements Serializer<ModuleComponentFileArtifactIdentifier> {
    private final ComponentIdentifierSerializer componentIdentifierSerializer = new ComponentIdentifierSerializer();

    @Override
    public ModuleComponentFileArtifactIdentifier read(Decoder decoder) throws IOException {
        ModuleComponentIdentifier componentIdentifier = (ModuleComponentIdentifier) componentIdentifierSerializer.read(decoder);
        String destinationFileName = decoder.readString();
        boolean pathIsDestinationFileName = decoder.readBoolean();
        String filePath = pathIsDestinationFileName ? destinationFileName : decoder.readString();
        return new ModuleComponentFileArtifactIdentifier(componentIdentifier, destinationFileName, filePath);
    }

    @Override
    public void write(Encoder encoder, ModuleComponentFileArtifactIdentifier value) throws IOException {
        componentIdentifierSerializer.write(encoder, value.getComponentIdentifier());
        encoder.writeString(value.getDestinationFileName());
        boolean pathIsDestinationFileName = value.getFilePath().equals(value.getDestinationFileName());
        encoder.writeBoolean(pathIsDestinationFileName);
        if (!pathIsDestinationFileName) {
            encoder.writeString(value.getFilePath());
        }
    }
}
