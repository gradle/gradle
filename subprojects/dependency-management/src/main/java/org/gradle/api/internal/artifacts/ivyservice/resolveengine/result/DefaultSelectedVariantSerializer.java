/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;

/**
 * Default implementation of {@link SelectedVariantSerializer}.
 */
@NotThreadSafe
public class DefaultSelectedVariantSerializer implements SelectedVariantSerializer {

    private final ResolvedVariantResultSerializer resolvedVariantResultSerializer;

    public DefaultSelectedVariantSerializer(ResolvedVariantResultSerializer resolvedVariantResultSerializer) {
        this.resolvedVariantResultSerializer = resolvedVariantResultSerializer;
    }

    @Override
    public void writeVariantResult(ResolvedGraphVariant variant, Encoder encoder) throws IOException {

        VariantGraphResolveState variantState = variant.getResolveState();
        ResolvedGraphVariant externalVariant = variant.getExternalVariant();

        ResolvedVariantResult resolvedVariantResult;
        if (externalVariant == null) {
            resolvedVariantResult = variantState.getVariantResult(null);
        } else {
            resolvedVariantResult = variantState.getVariantResult(externalVariant.getResolveState().getVariantResult(null));
        }

        encoder.writeSmallLong(variant.getNodeId());
        resolvedVariantResultSerializer.write(encoder, resolvedVariantResult);
    }

    @Override
    public void readSelectedVariant(Decoder decoder, ResolvedComponentVisitor visitor) throws IOException {
        long nodeId = decoder.readSmallLong();
        ResolvedVariantResult variant = resolvedVariantResultSerializer.read(decoder);
        visitor.visitSelectedVariant(nodeId, variant);
    }
}
