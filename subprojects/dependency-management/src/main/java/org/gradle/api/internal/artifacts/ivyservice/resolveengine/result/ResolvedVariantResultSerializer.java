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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

class ResolvedVariantResultSerializer implements Serializer<ResolvedVariantResult> {
    private final Map<ResolvedVariantResult, Integer> written = Maps.newHashMap();
    private final List<ResolvedVariantResult> read = Lists.newArrayList();

    private final AttributeContainerSerializer attributeContainerSerializer;

    ResolvedVariantResultSerializer(AttributeContainerSerializer attributeContainerSerializer) {
        this.attributeContainerSerializer = attributeContainerSerializer;
    }

    @Override
    public ResolvedVariantResult read(Decoder decoder) throws IOException {
        int index = decoder.readSmallInt();
        if (index == -1) {
            return null;
        }
        if (index == read.size()) {
            String variantName = decoder.readString();
            AttributeContainer attributes = attributeContainerSerializer.read(decoder);
            List<Capability> capabilities = readCapabilities(decoder);
            DefaultResolvedVariantResult result = new DefaultResolvedVariantResult(Describables.of(variantName), attributes, capabilities);
            read.add(result);
            return result;
        }
        return read.get(index);
    }

    private List<Capability> readCapabilities(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<Capability> capabilities = ImmutableList.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            String group = decoder.readString();
            String name = decoder.readString();
            String version = decoder.readNullableString();
            capabilities.add(new ImmutableCapability(group, name, version));
        }
        return capabilities.build();
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
            encoder.writeString(variant.getDisplayName());
            attributeContainerSerializer.write(encoder, variant.getAttributes());
            writeCapabilities(encoder, variant.getCapabilities());
        } else {
            encoder.writeSmallInt(index);
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

    void reset() {
        written.clear();
        read.clear();
    }
}
