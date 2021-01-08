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

public class ComponentResultSerializer implements Serializer<ResolvedGraphComponent> {

    private final ModuleVersionIdentifierSerializer idSerializer;
    private final ComponentSelectionReasonSerializer reasonSerializer;
    private final ComponentIdentifierSerializer componentIdSerializer;
    private final ResolvedVariantResultSerializer resolvedVariantResultSerializer;

    public ComponentResultSerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                     ResolvedVariantResultSerializer resolvedVariantResultSerializer,
                                     ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
                                     ComponentIdentifierSerializer componentIdentifierSerializer) {
        this.idSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);
        this.resolvedVariantResultSerializer = resolvedVariantResultSerializer;
        this.reasonSerializer = new ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory);
        this.componentIdSerializer = componentIdentifierSerializer;
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
        List<ResolvedVariantResult> resolvedVariants = readResolvedVariants(decoder);
        String repositoryName = decoder.readNullableString();
        return new DetachedComponentResult(resultId, id, reason, componentId, resolvedVariants, repositoryName);
    }

    private List<ResolvedVariantResult> readResolvedVariants(Decoder decoder) throws IOException {
        int size = decoder.readSmallInt();
        ImmutableList.Builder<ResolvedVariantResult> builder = ImmutableList.builderWithExpectedSize(size);
        for (int i=0; i<size; i++) {
            builder.add(resolvedVariantResultSerializer.read(decoder));
        }
        return builder.build();
    }

    @Override
    public void write(Encoder encoder, ResolvedGraphComponent value) throws IOException {
        encoder.writeSmallLong(value.getResultId());
        idSerializer.write(encoder, value.getModuleVersion());
        reasonSerializer.write(encoder, value.getSelectionReason());
        componentIdSerializer.write(encoder, value.getComponentId());
        writeSelectedVariantDetails(encoder, value.getResolvedVariants());
        encoder.writeNullableString(value.getRepositoryName());
    }

    private void writeSelectedVariantDetails(Encoder encoder, List<ResolvedVariantResult> variants) throws IOException {
        encoder.writeSmallInt(variants.size());
        for (ResolvedVariantResult variant : variants) {
            resolvedVariantResultSerializer.write(encoder, variant);
        }
    }


}
