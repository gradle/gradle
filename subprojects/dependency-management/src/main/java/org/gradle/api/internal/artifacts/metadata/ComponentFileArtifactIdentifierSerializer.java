/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

public class ComponentFileArtifactIdentifierSerializer implements Serializer<ComponentFileArtifactIdentifier> {
    private final ComponentIdentifierSerializer componentIdentifierSerializer = new ComponentIdentifierSerializer();

    @Override
    public void write(Encoder encoder, ComponentFileArtifactIdentifier value) throws Exception {
        componentIdentifierSerializer.write(encoder, value.getComponentIdentifier());
        Object rawFileName = value.getRawFileName();
        if (rawFileName instanceof IvyArtifactName) {
            encoder.writeBoolean(true);
            IvyArtifactNameSerializer.INSTANCE.write(encoder, (IvyArtifactName) rawFileName);
        } else {
            encoder.writeBoolean(false);
            encoder.writeString((String) rawFileName);
        }
    }

    @Override
    public ComponentFileArtifactIdentifier read(Decoder decoder) throws Exception {
        ModuleComponentIdentifier componentIdentifier = (ModuleComponentIdentifier) componentIdentifierSerializer.read(decoder);
        if (decoder.readBoolean()) {
            IvyArtifactName fileName = IvyArtifactNameSerializer.INSTANCE.read(decoder);
            return new ComponentFileArtifactIdentifier(componentIdentifier, fileName);
        } else {
            String fileName = decoder.readString();
            return new ComponentFileArtifactIdentifier(componentIdentifier, fileName);
        }
    }
}
