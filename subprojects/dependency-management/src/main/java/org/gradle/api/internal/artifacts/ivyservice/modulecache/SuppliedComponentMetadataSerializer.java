/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.UserProvidedMetadata;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.List;

/**
 * This component metadata serializer is responsible for serializing metadata that comes out
 * of a {@link org.gradle.api.artifacts.ComponentMetadataSupplier component metadata supplier} rule.
 * It does NOT contain full metadata, which can be confusing given the name of the class it's
 * supposed to serialize. This is, therefore, limited to the metadata necessary to perform selection
 * in a dynamic version resolver.
 */
public class SuppliedComponentMetadataSerializer extends AbstractSerializer<ComponentMetadata> {
    private final ModuleVersionIdentifierSerializer moduleVersionIdentifierSerializer;
    private final AttributeContainerSerializer attributeContainerSerializer;

    public SuppliedComponentMetadataSerializer(ModuleVersionIdentifierSerializer moduleVersionIdentifierSerializer, AttributeContainerSerializer attributeContainerSerializer) {
        this.moduleVersionIdentifierSerializer = moduleVersionIdentifierSerializer;
        this.attributeContainerSerializer = attributeContainerSerializer;
    }

    @Override
    public ComponentMetadata read(Decoder decoder) throws Exception {
        ModuleVersionIdentifier id = moduleVersionIdentifierSerializer.read(decoder);
        AttributeContainerInternal attributes = attributeContainerSerializer.read(decoder);
        List<String> statusScheme = readStatusScheme(decoder);
        return new UserProvidedMetadata(id, statusScheme, attributes.asImmutable());
    }

    @Override
    public void write(Encoder encoder, ComponentMetadata md) throws Exception {
        moduleVersionIdentifierSerializer.write(encoder, md.getId());
        attributeContainerSerializer.write(encoder, md.getAttributes());
        checkChangingFlag(md);
        writeStatusScheme(encoder, md);
    }

    private void checkChangingFlag(ComponentMetadata md) {
        boolean changing = md.isChanging();
        if (changing) {
            throw new UnsupportedOperationException("User-supplied metadata shouldn't have changing=true");
        }
    }

    private void writeStatusScheme(Encoder encoder, ComponentMetadata md) throws IOException {
        List<String> statusScheme = md.getStatusScheme();
        encoder.writeSmallInt(statusScheme.size());
        for (String s : statusScheme) {
            encoder.writeString(s);
        }
    }

    private List<String> readStatusScheme(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        ImmutableList.Builder<String> scheme = ImmutableList.builder();
        for (int i=0; i<size; i++) {
            scheme.add(decoder.readString());
        }
        return scheme.build();
    }
}
