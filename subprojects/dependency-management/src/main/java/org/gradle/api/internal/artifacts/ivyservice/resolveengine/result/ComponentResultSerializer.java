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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class ComponentResultSerializer implements Serializer<ResolvedGraphComponent> {

    private final ModuleVersionIdentifierSerializer idSerializer;
    private final ComponentSelectionReasonSerializer reasonSerializer;
    private final ComponentIdentifierSerializer componentIdSerializer;
    private final ResolvedVariantResultSerializer resolvedVariantResultSerializer;
    private final boolean returnAllVariants;

    public ComponentResultSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                     ResolvedVariantResultSerializer resolvedVariantResultSerializer,
                                     ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
                                     ComponentIdentifierSerializer componentIdentifierSerializer,
                                     boolean returnAllVariants) {
        this.idSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);
        this.resolvedVariantResultSerializer = resolvedVariantResultSerializer;
        this.reasonSerializer = new ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory);
        this.componentIdSerializer = componentIdentifierSerializer;
        this.returnAllVariants = returnAllVariants;
    }

    void reset() {
        resolvedVariantResultSerializer.reset();
    }

    @Override
    public ResolvedGraphComponent read(Decoder decoder) throws IOException {
        long resultId = decoder.readSmallLong();
        ModuleVersionIdentifier id = idSerializer.read(decoder);
        ComponentSelectionReason reason = reasonSerializer.read(decoder);
        ComponentIdentifier componentId = componentIdSerializer.read(decoder);
        int allVariantsSize = decoder.readSmallInt();
        ImmutableList.Builder<ResolvedVariantResult> allVariants = ImmutableList.builderWithExpectedSize(allVariantsSize);
        int resolvedVariantsSize = decoder.readSmallInt();
        ImmutableList.Builder<ResolvedVariantResult> resolvedVariants = ImmutableList.builderWithExpectedSize(resolvedVariantsSize);
        readVariants(decoder, allVariantsSize, resolvedVariantsSize, allVariants, resolvedVariants);
        String repositoryName = decoder.readNullableString();
        return new DetachedComponentResult(resultId, id, reason, componentId, resolvedVariants.build(), allVariants.build(), repositoryName);
    }

    private void readVariants(
        Decoder decoder,
        int allVariantsSize,
        int resolvedVariantsSize,
        ImmutableList.Builder<ResolvedVariantResult> allVariants,
        ImmutableList.Builder<ResolvedVariantResult> resolvedVariants
    ) throws IOException {
        boolean returnAllVariants = allVariantsSize != resolvedVariantsSize;
        for (int i = 0; i < allVariantsSize; i++) {
            ResolvedVariantResult variant = resolvedVariantResultSerializer.read(decoder);
            boolean isResolved;
            if (returnAllVariants) {
                isResolved = decoder.readBoolean();
            } else {
                isResolved = true;
            }
            if (isResolved) {
                resolvedVariants.add(variant);
            }
            allVariants.add(variant);
        }
    }

    @Override
    public void write(Encoder encoder, ResolvedGraphComponent value) throws IOException {
        encoder.writeSmallLong(value.getResultId());
        idSerializer.write(encoder, value.getModuleVersion());
        reasonSerializer.write(encoder, value.getSelectionReason());
        componentIdSerializer.write(encoder, value.getComponentId());
        List<ResolvedVariantResult> allVariants;
        Set<ResolvedVariantResult> resolvedVariants;
        if (returnAllVariants) {
            allVariants = value.getAllVariants();
            resolvedVariants = ImmutableSet.copyOf(value.getResolvedVariants());
        } else {
            allVariants = value.getResolvedVariants();
            resolvedVariants = ImmutableSet.copyOf(allVariants);
        }
        writeSelectedVariantDetails(encoder, resolvedVariants, allVariants);
        encoder.writeNullableString(value.getRepositoryName());
    }

    private void writeSelectedVariantDetails(
        Encoder encoder, Set<ResolvedVariantResult> resolvedVariants, List<ResolvedVariantResult> variants
    ) throws IOException {
        encoder.writeSmallInt(variants.size());
        encoder.writeSmallInt(resolvedVariants.size());
        boolean returnAllVariants = variants.size() != resolvedVariants.size();
        for (ResolvedVariantResult variant : variants) {
            resolvedVariantResultSerializer.write(encoder, variant);
            // Optimization for when not writing all variants -- we don't need to mark which ones are resolved
            if (returnAllVariants) {
                encoder.writeBoolean(resolvedVariants.contains(variant));
            }
        }
    }

}
