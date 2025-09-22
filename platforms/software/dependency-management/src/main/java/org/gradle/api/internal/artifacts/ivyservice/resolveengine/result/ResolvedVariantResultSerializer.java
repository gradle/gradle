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
import org.gradle.api.internal.artifacts.result.ResolvedVariantResultInternal;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * A serializer for {@link ResolvedVariantResult} that is not thread safe and not reusable.
 */
@NotThreadSafe
public class ResolvedVariantResultSerializer implements Serializer<ResolvedVariantResult> {

    private final Set<ResolvedVariantResult> seen = new HashSet<>();

    private final VariantIdentifierSerializer variantIdentifierSerializer;
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final CapabilitySerializer capabilitySerializer;

    public ResolvedVariantResultSerializer(VariantIdentifierSerializer variantIdentifierSerializer, AttributeContainerSerializer attributeContainerSerializer) {
        this.variantIdentifierSerializer = variantIdentifierSerializer;
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.capabilitySerializer = new CapabilitySerializer();
    }

    @Override
    public ResolvedVariantResult read(Decoder decoder) throws IOException {
        // This serializer is used only for input snapshotting. No need to implement reading.
        throw new UnsupportedOperationException();
    }

    @Override
    public void write(Encoder encoder, ResolvedVariantResult variant) throws Exception {
        if (seen.add(variant)) {
            ResolvedVariantResultInternal internalVariant = (ResolvedVariantResultInternal) variant;
            variantIdentifierSerializer.write(encoder, internalVariant.getId());
            encoder.writeString(variant.getDisplayName());
            attributeContainerSerializer.write(encoder, variant.getAttributes());
            for (Capability capability : variant.getCapabilities()) {
                capabilitySerializer.write(encoder, capability);
            }
            if (variant.getExternalVariant().isPresent()) {
                write(encoder, variant.getExternalVariant().get());
            }
        }
    }

}
