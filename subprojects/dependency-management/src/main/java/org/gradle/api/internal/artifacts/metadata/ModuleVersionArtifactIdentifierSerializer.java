/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.MapSerializer;
import org.gradle.messaging.serialize.Serializer;

import java.util.Map;

import static org.gradle.messaging.serialize.BaseSerializerFactory.STRING_SERIALIZER;

public class ModuleVersionArtifactIdentifierSerializer implements Serializer<ModuleComponentArtifactIdentifier> {
    private final ComponentIdentifierSerializer componentIdentifierSerializer = new ComponentIdentifierSerializer();
    private final MapSerializer<String, String> attributesSerializer = new MapSerializer<String, String>(STRING_SERIALIZER, STRING_SERIALIZER);

    public void write(Encoder encoder, ModuleComponentArtifactIdentifier value) throws Exception {
        DefaultModuleComponentArtifactIdentifier artifact = (DefaultModuleComponentArtifactIdentifier) value;
        componentIdentifierSerializer.write(encoder, artifact.getComponentIdentifier());
        IvyArtifactName ivyArtifactName = artifact.getName();
        encoder.writeString(ivyArtifactName.getName());
        encoder.writeString(ivyArtifactName.getType());
        encoder.writeNullableString(ivyArtifactName.getExtension());
        attributesSerializer.write(encoder, ivyArtifactName.getAttributes());
    }

    public ModuleComponentArtifactIdentifier read(Decoder decoder) throws Exception {
        ModuleComponentIdentifier componentIdentifier = (ModuleComponentIdentifier) componentIdentifierSerializer.read(decoder);
        String artifactName = decoder.readString();
        String type = decoder.readString();
        String extension = decoder.readNullableString();
        Map<String, String> attributes = attributesSerializer.read(decoder);
        return new DefaultModuleComponentArtifactIdentifier(componentIdentifier, artifactName, type, extension, attributes);
    }
}
