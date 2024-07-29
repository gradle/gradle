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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

import javax.annotation.Nullable;

public abstract class AbstractComponentGraphResolveState<T extends ComponentGraphResolveMetadata> implements ComponentGraphResolveState, ComponentArtifactResolveState {
    private final long instanceId;
    private final T graphMetadata;
    private final AttributeDesugaring attributeDesugaring;

    public AbstractComponentGraphResolveState(long instanceId, T graphMetadata, AttributeDesugaring attributeDesugaring) {
        this.instanceId = instanceId;
        this.graphMetadata = graphMetadata;
        this.attributeDesugaring = attributeDesugaring;
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

    @Nullable
    @Override
    public ComponentGraphResolveState maybeAsLenientPlatform(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier) {
        return null;
    }

    @Override
    public ComponentArtifactResolveState prepareForArtifactResolution() {
        return this;
    }

    @Override
    public void resolveArtifactsWithType(ArtifactResolver artifactResolver, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        artifactResolver.resolveArtifactsWithType(getArtifactMetadata(), artifactType, result);
    }

    protected ImmutableCapabilities capabilitiesFor(ImmutableCapabilities capabilities) {
        if (capabilities.asSet().isEmpty()) {
            return ImmutableCapabilities.of(DefaultImmutableCapability.defaultCapabilityForComponent(getMetadata().getModuleVersionId()));
        } else {
            return capabilities;
        }
    }

    protected abstract static class AbstractVariantGraphResolveState implements VariantGraphResolveState {
        private final Lazy<ResolvedVariantResult> publicView;
        private final AbstractComponentGraphResolveState<?> component;

        public AbstractVariantGraphResolveState(AbstractComponentGraphResolveState<?> component) {
            this.publicView = Lazy.locking().of(() -> createVariantResult(null));
            this.component = component;
        }

        @Override
        public boolean isAdHoc() {
            return component.isAdHoc();
        }

        @Override
        public ResolvedVariantResult getVariantResult(@Nullable ResolvedVariantResult externalVariant) {
            if (externalVariant != null) {
                // Don't cache the result
                // Note that the external variant is a function of the metadata of the component, so should be constructed by this state object and cached rather than passed in
                return createVariantResult(externalVariant);
            } else {
                return publicView.get();
            }
        }

        private DefaultResolvedVariantResult createVariantResult(@Nullable ResolvedVariantResult externalVariant) {
            VariantGraphResolveMetadata metadata = getMetadata();
            return new DefaultResolvedVariantResult(
                component.getId(),
                Describables.of(metadata.getName()),
                component.attributeDesugaring.desugar(metadata.getAttributes()),
                component.capabilitiesFor(metadata.getCapabilities()),
                externalVariant);
        }
    }
}
