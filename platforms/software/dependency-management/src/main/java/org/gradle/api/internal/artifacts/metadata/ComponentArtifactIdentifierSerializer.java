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

package org.gradle.api.internal.artifacts.metadata;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.local.model.MissingLocalArtifactMetadata;
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.component.local.model.TransformedArtifactIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.File;

/**
 * A thread-safe and reusable serializer for all {@link ComponentArtifactIdentifier} implementations.
 */
public class ComponentArtifactIdentifierSerializer extends AbstractSerializer<ComponentArtifactIdentifier> {
    private static final byte MODULE = 1;
    private static final byte MODULE_FILE = 2;
    private static final byte COMPONENT_FILE = 3;
    private static final byte OPAQUE = 4;
    private static final byte PUBLISH_ARTIFACT_LOCAL = 5;
    private static final byte TRANSFORMED = 6;
    private static final byte MISSING_LOCAL = 7;

    private final DefaultModuleComponentArtifactIdentifierSerializer moduleSerializer = new DefaultModuleComponentArtifactIdentifierSerializer();
    private final ModuleComponentFileArtifactIdentifierSerializer moduleFileSerializer = new ModuleComponentFileArtifactIdentifierSerializer();
    private final ComponentFileArtifactIdentifierSerializer componentFileSerializer = new ComponentFileArtifactIdentifierSerializer();
    private final ComponentIdentifierSerializer componentIdentifierSerializer;
    private final PublishArtifactLocalArtifactMetadataSerializer publishArtifactSerializer;

    public ComponentArtifactIdentifierSerializer(ComponentIdentifierSerializer componentIdentifierSerializer) {
        this.componentIdentifierSerializer = componentIdentifierSerializer;
        this.publishArtifactSerializer = new PublishArtifactLocalArtifactMetadataSerializer(componentIdentifierSerializer);
    }

    @Override
    public ComponentArtifactIdentifier read(Decoder decoder) throws Exception {
        byte tag = decoder.readByte();
        return switch (tag) {
            case MODULE -> moduleSerializer.read(decoder);
            case MODULE_FILE -> moduleFileSerializer.read(decoder);
            case COMPONENT_FILE -> componentFileSerializer.read(decoder);
            case OPAQUE -> new OpaqueComponentArtifactIdentifier(new File(decoder.readString()));
            case PUBLISH_ARTIFACT_LOCAL -> publishArtifactSerializer.read(decoder);
            case TRANSFORMED -> {
                ComponentArtifactIdentifier inputArtifactId = read(decoder);
                String fileName = decoder.readString();
                yield new TransformedArtifactIdentifier(inputArtifactId, fileName);
            }
            case MISSING_LOCAL -> {
                ComponentIdentifier componentIdentifier = componentIdentifierSerializer.read(decoder);
                IvyArtifactName name = IvyArtifactNameSerializer.INSTANCE.read(decoder);
                yield new MissingLocalArtifactMetadata(componentIdentifier, name);
            }
            default -> throw new IllegalArgumentException("Unable to find component artifact identifier type with id: " + tag);
        };
    }

    @Override
    public void write(Encoder encoder, ComponentArtifactIdentifier value) throws Exception {
        if (value instanceof DefaultModuleComponentArtifactIdentifier module) {
            encoder.writeByte(MODULE);
            moduleSerializer.write(encoder, module);
        } else if (value instanceof ModuleComponentFileArtifactIdentifier moduleFile) {
            encoder.writeByte(MODULE_FILE);
            moduleFileSerializer.write(encoder, moduleFile);
        } else if (value instanceof ComponentFileArtifactIdentifier componentFile) {
            encoder.writeByte(COMPONENT_FILE);
            componentFileSerializer.write(encoder, componentFile);
        } else if (value instanceof OpaqueComponentArtifactIdentifier opaque) {
            encoder.writeByte(OPAQUE);
            encoder.writeString(opaque.getFile().getCanonicalPath());
        } else if (value instanceof PublishArtifactLocalArtifactMetadata publishArtifact) {
            encoder.writeByte(PUBLISH_ARTIFACT_LOCAL);
            publishArtifactSerializer.write(encoder, publishArtifact);
        } else if (value instanceof TransformedArtifactIdentifier transformed) {
            encoder.writeByte(TRANSFORMED);
            write(encoder, transformed.getInputArtifactId());
            encoder.writeString(transformed.getFileName());
        } else if (value instanceof MissingLocalArtifactMetadata missing) {
            encoder.writeByte(MISSING_LOCAL);
            componentIdentifierSerializer.write(encoder, missing.getComponentIdentifier());
            IvyArtifactNameSerializer.INSTANCE.write(encoder, missing.getName());
        } else {
            throw new IllegalArgumentException("Unsupported component artifact identifier class: " + value.getClass().getName());
        }
    }
}
