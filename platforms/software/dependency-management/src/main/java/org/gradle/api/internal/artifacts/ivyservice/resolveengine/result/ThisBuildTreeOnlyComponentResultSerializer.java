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
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.List;

/**
 * A serializer used for resolution results that will be consumed from the same Gradle invocation that produces them.
 * <p>
 * Unlike {@link CompleteComponentResultSerializer}, this serializer writes references to {@link ComponentGraphResolveState}
 * and {@link VariantGraphResolveState} instances to build the result from, rather than persisting the associated data.
 * <p>
 * This serializer is intended for {@link ComponentGraphResolveState#isAdHoc() non-adhoc} components, as holding references
 * to adhoc components would prevent them from being garbage collected.
 */
public class ThisBuildTreeOnlyComponentResultSerializer implements ComponentResultSerializer {

    private final Long2ObjectMap<ComponentGraphResolveState> components = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
    private final Long2ObjectMap<VariantGraphResolveState> variants = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    private final ComponentSelectionReasonSerializer reasonSerializer;

    public ThisBuildTreeOnlyComponentResultSerializer(
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory
    ) {
        this.reasonSerializer = new ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory);
    }

    @Override
    public void writeComponentResult(Encoder encoder, ResolvedGraphComponent value, boolean includeAllSelectableVariantResults) throws Exception {
        encoder.writeSmallLong(value.getResultId());
        reasonSerializer.write(encoder, value.getSelectionReason());
        encoder.writeNullableString(value.getRepositoryName());

        ComponentGraphResolveState componentState = value.getResolveState();
        writeComponentReference(encoder, componentState);

        encoder.writeBoolean(includeAllSelectableVariantResults);

        List<ResolvedGraphVariant> selectedVariants = value.getSelectedVariants();
        encoder.writeSmallInt(selectedVariants.size());
        for (ResolvedGraphVariant variant : selectedVariants) {
            writeVariantResult(variant, encoder);
        }
    }

    private void writeComponentReference(Encoder encoder, ComponentGraphResolveState componentState) throws IOException {
        long instanceId = componentState.getInstanceId();
        components.putIfAbsent(instanceId, componentState);
        encoder.writeSmallLong(instanceId);
    }

    private void writeVariantResult(ResolvedGraphVariant variant, Encoder encoder) throws Exception {
        encoder.writeSmallLong(variant.getNodeId());

        writeVariantReference(encoder, variant.getResolveState());
        ResolvedGraphVariant externalVariant = variant.getExternalVariant();
        if (externalVariant != null) {
            encoder.writeBoolean(true);
            writeComponentReference(encoder, externalVariant.getComponentResolveState());
            writeVariantReference(encoder, externalVariant.getResolveState());
        } else {
            encoder.writeBoolean(false);
        }
    }

    private void writeVariantReference(Encoder encoder, VariantGraphResolveState variant) throws IOException {
        long instanceId = variant.getInstanceId();
        variants.putIfAbsent(instanceId, variant);
        encoder.writeSmallLong(instanceId);
    }

    @Override
    public void readComponentResult(Decoder decoder, ResolvedComponentVisitor visitor) throws Exception {
        long resultId = decoder.readSmallLong();
        ComponentSelectionReason reason = reasonSerializer.read(decoder);
        String repo = decoder.readNullableString();
        visitor.startVisitComponent(resultId, reason, repo);

        ComponentGraphResolveState component = readComponentReference(decoder);
        visitor.visitComponentDetails(component.getId(), component.getMetadata().getModuleVersionId());

        boolean includeAllSelectableVariantResults = decoder.readBoolean();
        if (includeAllSelectableVariantResults) {
            visitor.visitComponentVariants(component.getAllSelectableVariantResults());
        } else {
            visitor.visitComponentVariants(ImmutableList.of());
        }

        int variantCount = decoder.readSmallInt();
        for (int i = 0; i < variantCount; i++) {
            readVariantResult(decoder, component, visitor);
        }

        visitor.endVisitComponent();
    }

    private ComponentGraphResolveState readComponentReference(Decoder decoder) throws IOException {
        long instanceId = decoder.readSmallLong();
        ComponentGraphResolveState component = components.get(instanceId);
        if (component == null) {
            throw new IllegalStateException("No component with id " + instanceId + " found.");
        }
        return component;
    }

    private void readVariantResult(Decoder decoder, ComponentGraphResolveState component, ResolvedComponentVisitor visitor) throws Exception {
        long nodeId = decoder.readSmallLong();

        VariantGraphResolveState variant = readVariantReference(decoder);
        ResolvedVariantResult externalVariant;
        if (decoder.readBoolean()) {
            ComponentGraphResolveState externalVariantComponent = readComponentReference(decoder);
            VariantGraphResolveState externalVariantReference = readVariantReference(decoder);
            externalVariant = externalVariantComponent.getPublicViewFor(externalVariantReference, null);
        } else {
            externalVariant = null;
        }
        ResolvedVariantResult variantResult = component.getPublicViewFor(variant, externalVariant);
        visitor.visitSelectedVariant(nodeId, variantResult);
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
