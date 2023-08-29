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

import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.List;

public class ComponentResultSerializer {
    private final ComponentSelectionReasonSerializer reasonSerializer;
    private final ComponentDetailsSerializer componentDetailsSerializer;
    private final SelectedVariantSerializer selectedVariantSerializer;
    private final boolean returnAllVariants;

    public ComponentResultSerializer(
        ComponentDetailsSerializer componentDetailsSerializer,
        SelectedVariantSerializer selectedVariantSerializer,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        boolean returnAllVariants
    ) {
        this.componentDetailsSerializer = componentDetailsSerializer;
        this.selectedVariantSerializer = selectedVariantSerializer;
        this.reasonSerializer = new ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory);
        this.returnAllVariants = returnAllVariants;
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
        builder.endVisitComponent();
    }

    public void write(Encoder encoder, ResolvedGraphComponent value) throws IOException {
        try {
            encoder.writeSmallLong(value.getResultId());
            reasonSerializer.write(encoder, value.getSelectionReason());
            encoder.writeNullableString(value.getRepositoryName());
            componentDetailsSerializer.writeComponentDetails(value.getResolveState(), returnAllVariants, encoder);
            List<ResolvedGraphVariant> selectedVariants = value.getSelectedVariants();
            encoder.writeSmallInt(selectedVariants.size());
            for (ResolvedGraphVariant variant : selectedVariants) {
                selectedVariantSerializer.writeVariantResult(variant, encoder);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}
