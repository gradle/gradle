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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;

/**
 * A serializer used for resolution results that will be consumed from the same Gradle invocation that produces them.
 *
 * <p>Writes a reference to the {@link VariantGraphResolveState} instance to build the result from, rather than persisting the associated data.</p>
 */
@ThreadSafe
public class ThisBuildOnlySelectedVariantSerializer implements SelectedVariantSerializer {
    private final Long2ObjectMap<VariantGraphResolveState> variants = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    @Override
    public void writeVariantResult(ResolvedGraphVariant variant, Encoder encoder) throws IOException {
        encoder.writeSmallLong(variant.getNodeId());
        VariantGraphResolveState state = variant.getResolveState();
        writeVariantReference(encoder, state);
        ResolvedGraphVariant externalVariant = variant.getExternalVariant();
        if (externalVariant == null) {
            encoder.writeBoolean(false);
        } else {
            encoder.writeBoolean(true);
            writeVariantReference(encoder, externalVariant.getResolveState());
        }
    }

    private void writeVariantReference(Encoder encoder, VariantGraphResolveState state) throws IOException {
        long instanceId = state.getInstanceId();
        variants.putIfAbsent(instanceId, state);
        encoder.writeSmallLong(instanceId);
    }

    @Override
    public void readSelectedVariant(Decoder decoder, ResolvedComponentVisitor visitor) throws IOException {
        long nodeId = decoder.readSmallLong();
        VariantGraphResolveState variant = readVariantReference(decoder);
        ResolvedVariantResult externalVariant;
        if (decoder.readBoolean()) {
            externalVariant = readVariantReference(decoder).getVariantResult(null);
        } else {
            externalVariant = null;
        }
        visitor.visitSelectedVariant(nodeId, variant.getVariantResult(externalVariant));
    }

    private VariantGraphResolveState readVariantReference(Decoder decoder) throws IOException {
        long instanceId = decoder.readSmallLong();
        VariantGraphResolveState variant = variants.get(instanceId);
        if (variant == null) {
            throw new IllegalStateException("No variant with id " + instanceId + " found.");
        }
        return variant;
    }
}
