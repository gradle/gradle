/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractComponentGraphResolveState<T extends ComponentGraphResolveMetadata> implements ComponentGraphResolveState, ComponentArtifactResolveState {
    private final long instanceId;
    private final T graphMetadata;
    private final AttributeDesugaring attributeDesugaring;
    private final ImmutableCapability implicitCapability;

    // The public view of all graph variants of this component, mapped by their instance ID.
    private final ConcurrentHashMap<Long, ResolvedVariantResult> publicVariants = new ConcurrentHashMap<>();

    public AbstractComponentGraphResolveState(long instanceId, T graphMetadata, AttributeDesugaring attributeDesugaring) {
        this.instanceId = instanceId;
        this.graphMetadata = graphMetadata;
        this.attributeDesugaring = attributeDesugaring;
        this.implicitCapability =  DefaultImmutableCapability.defaultCapabilityForComponent(graphMetadata.getModuleVersionId());
    }

    @Override
    public String toString() {
        return getId().toString();
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    @Override
    public ComponentIdentifier getId() {
        return graphMetadata.getId();
    }

    @Override
    public T getMetadata() {
        return graphMetadata;
    }

    @Override
    public boolean isAdHoc() {
        return false;
    }

    protected AttributeDesugaring getAttributeDesugaring() {
        return attributeDesugaring;
    }

    @Override
    public ComponentArtifactResolveState prepareForArtifactResolution() {
        return this;
    }

    @Override
    public void resolveArtifactsWithType(ArtifactResolver artifactResolver, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        artifactResolver.resolveArtifactsWithType(getArtifactMetadata(), artifactType, result);
    }

    @Override
    public ImmutableCapability getDefaultCapability() {
        return implicitCapability;
    }

    protected ImmutableCapabilities capabilitiesFor(ImmutableCapabilities capabilities) {
        if (capabilities.asSet().isEmpty()) {
            return ImmutableCapabilities.of(DefaultImmutableCapability.defaultCapabilityForComponent(getMetadata().getModuleVersionId()));
        } else {
            return capabilities;
        }
    }

    @Override
    public ResolvedVariantResult getPublicViewFor(VariantGraphResolveState variant, @Nullable ResolvedVariantResult externalVariant) {
        if (externalVariant != null) {
            // Don't cache the result
            // Note that the external variant is a function of the metadata of the component, so should be constructed by this state object and cached rather than passed in
            return createVariantResult(variant, externalVariant);
        }

        return publicVariants.computeIfAbsent(variant.getInstanceId(), k -> createVariantResult(variant, null));
    }

    private DefaultResolvedVariantResult createVariantResult(VariantGraphResolveState variant, @Nullable ResolvedVariantResult externalVariant) {
        VariantGraphResolveMetadata metadata = variant.getMetadata();
        return new DefaultResolvedVariantResult(
            getId(),
            Describables.of(metadata.getName()),
            attributeDesugaring.desugar(metadata.getAttributes()),
            capabilitiesFor(metadata.getCapabilities()),
            externalVariant
        );
    }
}
