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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractComponentGraphResolveState<T extends ComponentGraphResolveMetadata, S extends ComponentResolveMetadata> implements ComponentGraphResolveState, ComponentArtifactResolveState {
    private final T graphMetadata;
    private final S artifactMetadata;
    private final AttributeDesugaring attributeDesugaring;
    // The public view for each variant of this component
    private final ConcurrentMap<VariantGraphResolveMetadata, ResolvedVariantResult> publicViews = new ConcurrentHashMap<>();

    public AbstractComponentGraphResolveState(T graphMetadata, S artifactMetadata, AttributeDesugaring attributeDesugaring) {
        this.graphMetadata = graphMetadata;
        this.artifactMetadata = artifactMetadata;
        this.attributeDesugaring = attributeDesugaring;
    }

    @Override
    public ComponentIdentifier getId() {
        return graphMetadata.getId();
    }

    @Override
    public ModuleSources getSources() {
        return artifactMetadata.getSources();
    }

    @Override
    public T getMetadata() {
        return graphMetadata;
    }

    public S getArtifactMetadata() {
        return artifactMetadata;
    }

    @Override
    public GraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        Optional<List<? extends VariantGraphResolveMetadata>> variants = graphMetadata.getVariantsForGraphTraversal();
        return new GraphSelectionCandidates() {
            @Override
            public boolean isUseVariants() {
                return variants.isPresent();
            }

            @Override
            public List<? extends VariantGraphResolveMetadata> getVariants() {
                return variants.get();
            }

            @Nullable
            @Override
            public ConfigurationGraphResolveMetadata getLegacyConfiguration() {
                return graphMetadata.getConfiguration(Dependency.DEFAULT_CONFIGURATION);
            }

            @Override
            public List<? extends ConfigurationGraphResolveMetadata> getCandidateConfigurations() {
                Set<String> configurationNames = graphMetadata.getConfigurationNames();
                ImmutableList.Builder<ConfigurationGraphResolveMetadata> builder = new ImmutableList.Builder<>();
                for (String configurationName : configurationNames) {
                    ConfigurationGraphResolveMetadata configuration = graphMetadata.getConfiguration(configurationName);
                    if (configuration.isCanBeConsumed()) {
                        builder.add(configuration);
                    }
                }
                return builder.build();
            }
        };
    }

    @Override
    public ResolvedVariantResult getVariantResult(VariantGraphResolveMetadata metadata, @Nullable ResolvedVariantResult externalVariant) {
        if (externalVariant != null) {
            // Don't cache the result
            // Note that the external variant is a function of the metadata of the component, so should be constructed by this state object and cached rather than passed in
            return createVariantResult(metadata, externalVariant);
        } else {
            return publicViews.computeIfAbsent(metadata, m -> createVariantResult(metadata, null));
        }
    }

    private DefaultResolvedVariantResult createVariantResult(VariantGraphResolveMetadata metadata, @Nullable ResolvedVariantResult externalVariant) {
        return new DefaultResolvedVariantResult(
            getId(),
            Describables.of(metadata.getName()),
            attributeDesugaring.desugar(metadata.getAttributes()),
            capabilitiesFor(metadata.getCapabilities()),
            externalVariant);
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

    public void resolveArtifactsWithType(ArtifactResolver artifactResolver, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        artifactResolver.resolveArtifactsWithType(getResolveMetadata(), artifactType, result);
    }

    @Override
    public ArtifactSet prepareForArtifactResolution(ArtifactSelector artifactSelector, Collection<? extends ComponentArtifactMetadata> artifacts, ImmutableAttributes overriddenAttributes) {
        return artifactSelector.resolveArtifacts(getResolveMetadata(), artifacts, overriddenAttributes);
    }

    protected List<? extends Capability> capabilitiesFor(CapabilitiesMetadata variantCapabilities) {
        List<? extends Capability> capabilities = variantCapabilities.getCapabilities();
        if (capabilities.isEmpty()) {
            capabilities = ImmutableList.of(DefaultImmutableCapability.defaultCapabilityForComponent(getMetadata().getModuleVersionId()));
        } else {
            capabilities = ImmutableList.copyOf(capabilities);
        }
        return capabilities;
    }
}
