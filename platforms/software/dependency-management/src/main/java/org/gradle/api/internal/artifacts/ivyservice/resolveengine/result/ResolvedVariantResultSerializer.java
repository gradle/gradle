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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A serializer for {@link ResolvedVariantResult} that is not thread safe and not reusable.
 */
@NotThreadSafe
public class ResolvedVariantResultSerializer implements Serializer<ResolvedVariantResult> {
    private final Map<ResolvedVariantResult, Integer> written = new HashMap<>();
    private final List<ResolvedVariantResult> read = new ArrayList<>();

    private final ComponentIdentifierSerializer componentIdentifierSerializer;
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final CapabilitySerializer capabilitySerializer;

    public ResolvedVariantResultSerializer(ComponentIdentifierSerializer componentIdentifierSerializer, AttributeContainerSerializer attributeContainerSerializer) {
        this.componentIdentifierSerializer = componentIdentifierSerializer;
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.capabilitySerializer = new CapabilitySerializer();
    }

    @Override
    public ResolvedVariantResult read(Decoder decoder) throws IOException {
        int index = decoder.readSmallInt();
        if (index == -1) {
            return null;
        }
        if (index == read.size()) {
            ComponentIdentifier owner = componentIdentifierSerializer.read(decoder);
            String variantName = decoder.readString();
            AttributeContainer attributes = attributeContainerSerializer.read(decoder);
            ImmutableCapabilities capabilities = readCapabilities(decoder);
            read.add(null);
            ResolvedVariantResult externalVariant = read(decoder);
            DefaultResolvedVariantResult result = new DefaultResolvedVariantResult(owner, Describables.of(variantName), attributes, capabilities, externalVariant);
            this.read.set(index, result);
            return result;
        }
        return read.get(index);
    }

    private ImmutableCapabilities readCapabilities(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return ImmutableCapabilities.EMPTY;
        }
        ImmutableSet.Builder<ImmutableCapability> capabilities = ImmutableSet.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            capabilities.add(capabilitySerializer.read(decoder));
        }
        return new ImmutableCapabilities(capabilities.build());
    }

    @Override
    public void write(Encoder encoder, ResolvedVariantResult variant) throws IOException {
        if (variant == null) {
            encoder.writeSmallInt(-1);
            return;
        }
        Integer index = written.get(variant);
        if (index == null) {
            index = written.size();
            written.put(variant, index);
            encoder.writeSmallInt(index);
            componentIdentifierSerializer.write(encoder, variant.getOwner());
            encoder.writeString(variant.getDisplayName());
            attributeContainerSerializer.write(encoder, variant.getAttributes());
            writeCapabilities(encoder, variant.getCapabilities());
            write(encoder, variant.getExternalVariant().orElse(null));
        } else {
            encoder.writeSmallInt(index);
        }
    }

    private void writeCapabilities(Encoder encoder, List<Capability> capabilities) throws IOException {
        encoder.writeSmallInt(capabilities.size());
        for (Capability capability : capabilities) {
            capabilitySerializer.write(encoder, capability);
        }
    }

    void reset() {
        written.clear();
        read.clear();
    }
}
