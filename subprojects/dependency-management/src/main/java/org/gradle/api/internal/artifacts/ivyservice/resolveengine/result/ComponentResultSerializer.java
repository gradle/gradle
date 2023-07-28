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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.List;

public class ComponentResultSerializer {
    private final ComponentSelectionReasonSerializer reasonSerializer;
    private final ComponentDetailsSerializer componentDetailsSerializer;
    private final SelectedVariantSerializer selectedVariantSerializer;
    private final ResolvedVariantResultSerializer resolvedVariantResultSerializer;
    private final boolean returnAllVariants;

    public ComponentResultSerializer(
        ComponentDetailsSerializer componentDetailsSerializer,
        SelectedVariantSerializer selectedVariantSerializer,
        ResolvedVariantResultSerializer resolvedVariantResultSerializer,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        boolean returnAllVariants
    ) {
        this.componentDetailsSerializer = componentDetailsSerializer;
        this.selectedVariantSerializer = selectedVariantSerializer;
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
        String repo = decoder.readNullableString();
        builder.startVisitComponent(resultId, reason, repo);
        componentDetailsSerializer.readComponentDetails(decoder, builder);
        int variantCount = decoder.readSmallInt();
        for (int i = 0; i < variantCount; i++) {
            selectedVariantSerializer.readSelectedVariant(decoder, builder);
        }

        List<ResolvedVariantResult> availableVariants;
        if (decoder.readBoolean()) {
            // use all available variants
            availableVariants = readVariantList(decoder);
        } else {
            availableVariants = ImmutableList.of();
        }
        builder.visitComponentVariants(availableVariants);

        builder.endVisitComponent();
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
        encoder.writeNullableString(value.getRepositoryName());

        ComponentGraphResolveState component = value.getResolveState();
        componentDetailsSerializer.writeComponentDetails(component, encoder);
        List<ResolvedGraphVariant> selectedVariants = value.getSelectedVariants();
        encoder.writeSmallInt(selectedVariants.size());
        for (ResolvedGraphVariant variant : selectedVariants) {
            selectedVariantSerializer.writeVariantResult(variant, encoder);
        }

        if (returnAllVariants) {
            encoder.writeBoolean(true);
            writeVariantList(encoder, component.getAllSelectableVariantResults());
        } else {
            encoder.writeBoolean(false);
        }
    }

    private void writeVariantList(Encoder encoder, List<ResolvedVariantResult> variants) throws IOException {
        encoder.writeSmallInt(variants.size());
        for (ResolvedVariantResult variant : variants) {
            resolvedVariantResultSerializer.write(encoder, variant);
        }
    }
}
