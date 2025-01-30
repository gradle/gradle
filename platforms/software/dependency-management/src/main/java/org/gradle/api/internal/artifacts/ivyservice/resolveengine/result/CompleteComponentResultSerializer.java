/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.ListSerializer;
import org.gradle.internal.serialize.Serializer;

import javax.inject.Inject;
import java.util.List;

/**
 * A {@link ComponentResultSerializer} that serializes the complete component result
 * without relying on any external state to be held between serialization and deserialization.
 */
public class CompleteComponentResultSerializer implements ComponentResultSerializer {

    private final ComponentSelectionReasonSerializer reasonSerializer;
    private final Serializer<ModuleVersionIdentifier> moduleVersionIdSerializer;
    private final Serializer<AttributeContainer> attributeContainerSerializer;
    private final Serializer<ComponentIdentifier> componentIdSerializer;
    private final Serializer<List<Capability>> capabilitySerializer;

    @Inject
    public CompleteComponentResultSerializer(
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        AttributesFactory attributesFactory,
        NamedObjectInstantiator namedObjectInstantiator
    ) {
        this.reasonSerializer = new ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory);
        this.moduleVersionIdSerializer = new ModuleVersionIdentifierSerializer(moduleIdentifierFactory);
        this.attributeContainerSerializer = new DesugaringAttributeContainerSerializer(attributesFactory, namedObjectInstantiator);
        this.componentIdSerializer = new ComponentIdentifierSerializer();
        this.capabilitySerializer = new ListSerializer<>(new CapabilitySerializer());
    }

    @Override
    public void writeComponentResult(Encoder encoder, ResolvedGraphComponent component, boolean includeAllSelectableVariantResults) throws Exception {
        encoder.writeSmallLong(component.getResultId());
        reasonSerializer.write(encoder, component.getSelectionReason());
        encoder.writeNullableString(component.getRepositoryName());

        ComponentGraphResolveState componentState = component.getResolveState();
        componentIdSerializer.write(encoder, componentState.getId());
        moduleVersionIdSerializer.write(encoder, componentState.getMetadata().getModuleVersionId());

        // TODO: Do not ignore includeAllSelectableVariantResults.

        List<ResolvedGraphVariant> selectedVariants = component.getSelectedVariants();
        encoder.writeSmallInt(selectedVariants.size());
        for (ResolvedGraphVariant variant : selectedVariants) {
            writeVariantResult(variant, componentState, encoder);
        }
    }

    private void writeVariantResult(ResolvedGraphVariant variant, ComponentGraphResolveState component, Encoder encoder) throws Exception {
        encoder.writeSmallLong(variant.getNodeId());

        ResolvedVariantResult variantResult = component.getPublicViewFor(variant.getResolveState(), null);

        componentIdSerializer.write(encoder, variantResult.getOwner());
        encoder.writeString(variantResult.getDisplayName());
        attributeContainerSerializer.write(encoder, variantResult.getAttributes());
        capabilitySerializer.write(encoder, variantResult.getCapabilities());

        // TODO: Write the external variant, like we do with the variant reference.
    }

    @Override
    public void readComponentResult(Decoder decoder, ResolvedComponentVisitor visitor) throws Exception {
        long resultId = decoder.readSmallLong();
        ComponentSelectionReason reason = reasonSerializer.read(decoder);
        String repo = decoder.readNullableString();
        visitor.startVisitComponent(resultId, reason, repo);

        ComponentIdentifier componentIdentifier = componentIdSerializer.read(decoder);
        ModuleVersionIdentifier moduleVersionIdentifier = moduleVersionIdSerializer.read(decoder);
        visitor.visitComponentDetails(componentIdentifier, moduleVersionIdentifier);

        // TODO: Deserialize all selectable variant results if present.
        visitor.visitComponentVariants(ImmutableList.of());

        int variantCount = decoder.readSmallInt();
        for (int i = 0; i < variantCount; i++) {
            readVariantResult(decoder, visitor);
        }

        visitor.endVisitComponent();
    }

    private void readVariantResult(Decoder decoder, ResolvedComponentVisitor visitor) throws Exception {
        long nodeId = decoder.readSmallLong();

        ComponentIdentifier ownerId = componentIdSerializer.read(decoder);
        String displayName = decoder.readString();
        AttributeContainer attributes = attributeContainerSerializer.read(decoder);
        List<Capability> capabilities = capabilitySerializer.read(decoder);

        // TODO: Read the external variant, like we do with the variant reference.

        visitor.visitSelectedVariant(nodeId, new DefaultResolvedVariantResult(ownerId, Describables.of(displayName), attributes, ImmutableCapabilities.of(capabilities), null));
    }

}
