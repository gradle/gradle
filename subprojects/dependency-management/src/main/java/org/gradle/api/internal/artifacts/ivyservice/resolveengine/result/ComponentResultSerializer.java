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
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.List;

public class ComponentResultSerializer {
    private final ComponentSelectionReasonSerializer reasonSerializer;
    private final ComponentDetailsSerializer componentDetailsSerializer;
    private final ResolvedVariantResultSerializer resolvedVariantResultSerializer;
    private final boolean returnAllVariants;

    public ComponentResultSerializer(
        ComponentDetailsSerializer componentDetailsSerializer,
        ResolvedVariantResultSerializer resolvedVariantResultSerializer,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        boolean returnAllVariants
    ) {
        this.componentDetailsSerializer = componentDetailsSerializer;
        this.resolvedVariantResultSerializer = resolvedVariantResultSerializer;
        this.reasonSerializer = new ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory);
        this.returnAllVariants = returnAllVariants;
    }

    void reset() {
        resolvedVariantResultSerializer.reset();
    }

    public void readInto(Decoder decoder, ResolvedComponentVisitor builder) throws Exception {
        long resultId = decoder.readSmallLong();
        ComponentSelectionReason reason = reasonSerializer.read(decoder);
        builder.startVisitComponent(resultId, reason);
        componentDetailsSerializer.readComponentDetails(decoder, builder);
        List<ResolvedVariantResult> availableVariants;
        List<ResolvedVariantResult> resolvedVariants;
        if (decoder.readBoolean()) {
            // read selected + available variants
            resolvedVariants = readVariantList(decoder);
            availableVariants = readAvailableVariants(decoder, resolvedVariants);
        } else {
            // read selected variants only
            resolvedVariants = readVariantList(decoder);
            availableVariants = resolvedVariants;
        }
        builder.visitComponentVariants(resolvedVariants, availableVariants);
    }

    private List<ResolvedVariantResult> readAvailableVariants(Decoder decoder, List<ResolvedVariantResult> selectedVariants) throws IOException {
        List<ResolvedVariantResult> availableVariants;
        int availableVariantsSize = decoder.readSmallInt();
        ImmutableList.Builder<ResolvedVariantResult> availableVariantsBuilder = ImmutableList.builderWithExpectedSize(availableVariantsSize);
        for (int i = 0; i < availableVariantsSize; i++) {
            int index = decoder.readInt();
            if (index >= 0) {
                availableVariantsBuilder.add(selectedVariants.get(index));
            } else {
                availableVariantsBuilder.add(resolvedVariantResultSerializer.read(decoder));
            }
        }
        availableVariants = availableVariantsBuilder.build();
        return availableVariants;
    }

    private List<ResolvedVariantResult> readVariantList(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        ImmutableList.Builder<ResolvedVariantResult> builder = ImmutableList.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            ResolvedVariantResult variant = resolvedVariantResultSerializer.read(decoder);
            builder.add(variant);
        }
        return builder.build();
    }

    public void write(Encoder encoder, ResolvedGraphComponent value) throws IOException {
        encoder.writeSmallLong(value.getResultId());
        reasonSerializer.write(encoder, value.getSelectionReason());
        componentDetailsSerializer.writeComponentDetails(value.getResolveState(), encoder);
        if (returnAllVariants) {
            encoder.writeBoolean(true);
            List<ResolvedVariantResult> selectedVariants = value.getSelectedVariants();
            writeVariantList(encoder, selectedVariants);
            writeSelectedAndAvailableVariants(encoder, selectedVariants, value.getAvailableVariants());
        } else {
            encoder.writeBoolean(false);
            writeVariantList(encoder, value.getSelectedVariants());
        }
    }

    private void writeSelectedAndAvailableVariants(Encoder encoder, List<ResolvedVariantResult> selectedVariants, List<ResolvedVariantResult> variants) throws IOException {
        // The resolved variants collection is not necessarily a subset of the variants collection
        encoder.writeSmallInt(variants.size());
        for (ResolvedVariantResult variant : variants) {
            int index = selectedVariants.indexOf(variant);
            encoder.writeInt(index);
            if (index < 0) {
                resolvedVariantResultSerializer.write(encoder, variant);
            }
        }
    }

    private void writeVariantList(Encoder encoder, List<ResolvedVariantResult> variants) throws IOException {
        encoder.writeSmallInt(variants.size());
        for (ResolvedVariantResult variant : variants) {
            resolvedVariantResultSerializer.write(encoder, variant);
        }
    }
}
