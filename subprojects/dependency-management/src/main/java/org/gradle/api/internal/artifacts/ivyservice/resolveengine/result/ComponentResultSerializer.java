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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedVariantDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DefaultVariantDetails;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.List;

public class ComponentResultSerializer implements Serializer<ResolvedGraphComponent> {

    private final ModuleVersionIdentifierSerializer idSerializer;
    private final ComponentSelectionReasonSerializer reasonSerializer;
    private final ComponentIdentifierSerializer componentIdSerializer;
    private final AttributeContainerSerializer attributeContainerSerializer;

    public ComponentResultSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory, AttributeContainerSerializer attributeContainerSerializer) {
        idSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);
        this.attributeContainerSerializer = attributeContainerSerializer;
        reasonSerializer = new ComponentSelectionReasonSerializer();
        componentIdSerializer = new ComponentIdentifierSerializer();
    }

    @Override
    public ResolvedGraphComponent read(Decoder decoder) throws IOException {
        long resultId = decoder.readSmallLong();
        ModuleVersionIdentifier id = idSerializer.read(decoder);
        ComponentSelectionReason reason = reasonSerializer.read(decoder);
        ComponentIdentifier componentId = componentIdSerializer.read(decoder);
        List<ResolvedVariantDetails> resolvedVariants = readResolvedVariants(decoder);
        String repositoryName = decoder.readNullableString();
        return new DetachedComponentResult(resultId, id, reason, componentId, resolvedVariants, repositoryName);
    }

    private List<ResolvedVariantDetails> readResolvedVariants(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        ImmutableList.Builder<ResolvedVariantDetails> builder = ImmutableList.builderWithExpectedSize(size);
        for (int i=0; i<size; i++) {
            String variantName = decoder.readString();
            AttributeContainer attributes = attributeContainerSerializer.read(decoder);
            List<Capability> capabilities = readCapabilities(decoder);
            builder.add(new DefaultVariantDetails(Describables.of(variantName), attributes, capabilities));
        }
        return builder.build();
    }

    private List<Capability> readCapabilities(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<Capability> capabilities = ImmutableList.builderWithExpectedSize(size);
        for (int i=0; i<size; i++) {
            String group = decoder.readString();
            String name = decoder.readString();
            String version = decoder.readNullableString();
            capabilities.add(new ImmutableCapability(group, name, version));
        }
        return capabilities.build();
    }

    @Override
    public void write(Encoder encoder, ResolvedGraphComponent value) throws IOException {
        encoder.writeSmallLong(value.getResultId());
        idSerializer.write(encoder, value.getModuleVersion());
        reasonSerializer.write(encoder, value.getSelectionReason());
        componentIdSerializer.write(encoder, value.getComponentId());
        writeSelectedVariantDetails(encoder, value.getResolvedVariants());
        encoder.writeNullableString(value.getRepositoryName());
    }

    private void writeSelectedVariantDetails(Encoder encoder, List<ResolvedVariantDetails> variants) throws IOException {
        encoder.writeSmallInt(variants.size());
        for (ResolvedVariantDetails variant : variants) {
            encoder.writeString(variant.getVariantName().getDisplayName());
            attributeContainerSerializer.write(encoder, variant.getVariantAttributes());
            writeCapabilities(encoder, variant.getCapabilities());
        }
    }

    private void writeCapabilities(Encoder encoder, List<Capability> capabilities) throws IOException {
        encoder.writeSmallInt(capabilities.size());
        for (Capability capability : capabilities) {
            encoder.writeString(capability.getGroup());
            encoder.writeString(capability.getName());
            encoder.writeNullableString(capability.getVersion());
        }
    }


}
