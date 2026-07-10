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

import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.HashMap;
import java.util.Map;

/**
 * A serializer for {@link ResolvedVariantResult} that is not thread safe and not reusable.
 */
@NotThreadSafe
public class ResolvedVariantResultSerializer implements Serializer<ResolvedVariantResult> {

    private final Map<ResolvedVariantResult, Integer> written = new HashMap<>();

    private final ComponentIdentifierSerializer componentIdentifierSerializer;
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final ListSerializer<Capability> capabilitySerializer;

    public ResolvedVariantResultSerializer(ComponentIdentifierSerializer componentIdentifierSerializer, AttributeContainerSerializer attributeContainerSerializer) {
        this.componentIdentifierSerializer = componentIdentifierSerializer;
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.capabilitySerializer = new ListSerializer<>(new CapabilitySerializer());
    }

    @Override
    public ResolvedVariantResult read(Decoder decoder) throws Exception {
        throw new UnsupportedOperationException("This serializer is only intended for input snapshotting.");
    }

    @Override
    public void write(Encoder encoder, ResolvedVariantResult variant) throws Exception {
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
            capabilitySerializer.write(encoder, variant.getCapabilities());
            write(encoder, variant.getExternalVariant().orElse(null));
        } else {
            encoder.writeSmallInt(index);
        }
    }

}
