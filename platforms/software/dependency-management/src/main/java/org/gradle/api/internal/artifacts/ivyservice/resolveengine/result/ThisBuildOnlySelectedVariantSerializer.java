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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.util.List;

/**
 * A serializer used for resolution results that will be consumed from the same Gradle invocation that produces them.
 *
 * <p>Writes a reference to the {@link VariantGraphResolveState} instance to build the result from, rather than persisting the associated data.</p>
 */
@ThreadSafe
public class ThisBuildOnlySelectedVariantSerializer implements SelectedVariantSerializer {
    private final Long2ObjectMap<VariantGraphResolveState> variants = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    private final Serializer<ComponentIdentifier> componentIdSerializer = new ComponentIdentifierSerializer();
    private final Serializer<AttributeContainer> attributeContainerSerializer;
    private final Serializer<List<Capability>> capabilitySerializer = new ListSerializer<>(new CapabilitySerializer());

    public ThisBuildOnlySelectedVariantSerializer(ImmutableAttributesFactory immutableAttributesFactory, NamedObjectInstantiator namedObjectInstantiator) {
        attributeContainerSerializer = new DesugaringAttributeContainerSerializer(immutableAttributesFactory, namedObjectInstantiator);
    }

    @Override
    public void writeVariantResult(ResolvedGraphVariant variant, Encoder encoder) throws Exception {
        encoder.writeSmallLong(variant.getNodeId());
        VariantGraphResolveState state = variant.getResolveState();
        if (state.isAdHoc()) {
            writeVariantData(encoder, state);
        } else {
            writeVariantReference(encoder, variant, state);
        }
    }

    private void writeVariantReference(Encoder encoder, ResolvedGraphVariant variant, VariantGraphResolveState state) throws IOException {
        encoder.writeBoolean(false);
        writeVariantReference(encoder, state);
        ResolvedGraphVariant externalVariant = variant.getExternalVariant();
        if (externalVariant == null) {
            encoder.writeBoolean(false);
        } else {
            encoder.writeBoolean(true);
            writeVariantReference(encoder, externalVariant.getResolveState());
        }
    }

    private void writeVariantData(Encoder encoder, VariantGraphResolveState state) throws Exception {
        encoder.writeBoolean(true);
        ResolvedVariantResult variantResult = state.getVariantResult(null);
        componentIdSerializer.write(encoder, variantResult.getOwner());
        encoder.writeString(variantResult.getDisplayName());
        attributeContainerSerializer.write(encoder, variantResult.getAttributes());
        capabilitySerializer.write(encoder, variantResult.getCapabilities());
    }

    private void writeVariantReference(Encoder encoder, VariantGraphResolveState variant) throws IOException {
        long instanceId = variant.getInstanceId();
        variants.putIfAbsent(instanceId, variant);
        encoder.writeSmallLong(instanceId);
    }

    @Override
    public void readSelectedVariant(Decoder decoder, ResolvedComponentVisitor visitor) throws Exception {
        long nodeId = decoder.readSmallLong();
        if (decoder.readBoolean()) {
            readVariantData(decoder, visitor, nodeId);
        } else {
            readVariantReference(decoder, visitor, nodeId);
        }
    }

    private void readVariantReference(Decoder decoder, ResolvedComponentVisitor visitor, long nodeId) throws IOException {
        VariantGraphResolveState variant = readVariantReference(decoder);
        ResolvedVariantResult externalVariant;
        if (decoder.readBoolean()) {
            externalVariant = readVariantReference(decoder).getVariantResult(null);
        } else {
            externalVariant = null;
        }
        visitor.visitSelectedVariant(nodeId, variant.getVariantResult(externalVariant));
    }

    private void readVariantData(Decoder decoder, ResolvedComponentVisitor visitor, long nodeId) throws Exception {
        ComponentIdentifier ownerId = componentIdSerializer.read(decoder);
        String displayName = decoder.readString();
        AttributeContainer attributes = attributeContainerSerializer.read(decoder);
        List<Capability> capabilities = capabilitySerializer.read(decoder);
        visitor.visitSelectedVariant(nodeId, new DefaultResolvedVariantResult(ownerId, Describables.of(displayName), attributes, capabilities, null));
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
