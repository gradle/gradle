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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactIdentifier;
import org.gradle.api.internal.artifacts.metadata.IvyArtifactName;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactIdentifier;
import org.gradle.messaging.serialize.Decoder;
import org.gradle.messaging.serialize.Encoder;
import org.gradle.messaging.serialize.Serializer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ModuleVersionArtifactIdentifierSerializer implements Serializer<ModuleVersionArtifactIdentifier> {
    private final ComponentIdentifierSerializer componentIdentifierSerializer = new ComponentIdentifierSerializer();
    private final ModuleVersionIdentifierSerializer modIdSerializer = new ModuleVersionIdentifierSerializer();

    public void write(Encoder encoder, ModuleVersionArtifactIdentifier value) throws Exception {
        DefaultModuleVersionArtifactIdentifier artifact = (DefaultModuleVersionArtifactIdentifier) value;
        componentIdentifierSerializer.write(encoder, artifact.getComponentIdentifier());
        modIdSerializer.write(encoder, artifact.getModuleVersionIdentifier());
        IvyArtifactName ivyArtifactName = artifact.getName();
        encoder.writeString(ivyArtifactName.getName());
        encoder.writeString(ivyArtifactName.getType());
        encoder.writeNullableString(ivyArtifactName.getExtension());
        Map<String, String> attributes = ivyArtifactName.getAttributes();
        encoder.writeSmallInt(attributes.size());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            encoder.writeString(entry.getKey());
            encoder.writeString(entry.getValue());
        }
    }

    public ModuleVersionArtifactIdentifier read(Decoder decoder) throws Exception {
        ComponentIdentifier componentIdentifier = componentIdentifierSerializer.read(decoder);
        ModuleVersionIdentifier moduleVersionIdentifier = modIdSerializer.read(decoder);
        String artifactName = decoder.readString();
        String type = decoder.readString();
        String extension = decoder.readNullableString();
        int attrCount = decoder.readSmallInt();
        Map<String, String> attributes;
        if (attrCount == 0) {
            attributes = Collections.emptyMap();
        } else {
            attributes = new HashMap<String, String>(attrCount);
            for (int i = 0; i < attrCount; i++) {
                String key = decoder.readString();
                String value = decoder.readString();
                attributes.put(key, value);
            }
        }
        return new DefaultModuleVersionArtifactIdentifier((ModuleComponentIdentifier) componentIdentifier, moduleVersionIdentifier, artifactName, type, extension, attributes);
    }
}
