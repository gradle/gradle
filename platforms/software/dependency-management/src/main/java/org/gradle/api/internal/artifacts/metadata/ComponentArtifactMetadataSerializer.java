/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Objects;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

public class ComponentArtifactMetadataSerializer extends AbstractSerializer<ComponentArtifactMetadata> {
    private final ComponentIdentifierSerializer componentIdentifierSerializer = new ComponentIdentifierSerializer();

    @Override
    public void write(Encoder encoder, ComponentArtifactMetadata value) throws Exception {
        if (value instanceof ModuleComponentArtifactMetadata) {
            ModuleComponentArtifactMetadata moduleComponentArtifactMetadata = (ModuleComponentArtifactMetadata) value;
            componentIdentifierSerializer.write(encoder, moduleComponentArtifactMetadata.getComponentId());
            IvyArtifactNameSerializer.INSTANCE.write(encoder,  moduleComponentArtifactMetadata.getName());
        } else {
            throw new IllegalArgumentException("Unknown artifact metadata type.");
        }
    }

    @Override
    public ComponentArtifactMetadata read(Decoder decoder) throws Exception {
        ModuleComponentIdentifier componentIdentifier = (ModuleComponentIdentifier) componentIdentifierSerializer.read(decoder);
        IvyArtifactName name = IvyArtifactNameSerializer.INSTANCE.read(decoder);
        return new DefaultModuleComponentArtifactMetadata(componentIdentifier, name);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        ComponentArtifactMetadataSerializer rhs = (ComponentArtifactMetadataSerializer) obj;
        return Objects.equal(componentIdentifierSerializer, rhs.componentIdentifierSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), componentIdentifierSerializer);
    }
}
