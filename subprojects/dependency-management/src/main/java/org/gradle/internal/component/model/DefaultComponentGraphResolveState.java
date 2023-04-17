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

import com.google.common.base.Optional;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.resolve.resolver.ArtifactSelector;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Holds the resolution state for an external component.
 */
public class DefaultComponentGraphResolveState<T extends ComponentGraphResolveMetadata, S extends ComponentResolveMetadata> extends AbstractComponentGraphResolveState<T, S> {
    private final ConcurrentMap<ConfigurationMetadata, DefaultVariantArtifactResolveState> variants = new ConcurrentHashMap<>();

    public DefaultComponentGraphResolveState(T graphMetadata, S artifactMetadata) {
        super(graphMetadata, artifactMetadata);
    }

    public static ComponentGraphResolveState of(ModuleComponentResolveMetadata metadata) {
        return new DefaultComponentGraphResolveState<>(metadata, metadata);
    }

    @Override
    public ComponentArtifactResolveMetadata getResolveMetadata() {
        return new ExternalArtifactResolveMetadata(getArtifactMetadata());
    }

    @Override
    public VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant) {
        return (VariantArtifactGraphResolveMetadata) variant;
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution(VariantGraphResolveMetadata variant) {
        ConfigurationMetadata configurationMetadata = (ConfigurationMetadata) variant;
        return variants.computeIfAbsent(configurationMetadata, c -> new DefaultVariantArtifactResolveState(getMetadata(), getArtifactMetadata(), configurationMetadata));
    }

    private static class DefaultVariantArtifactResolveState implements VariantArtifactResolveState {
        private final ComponentResolveMetadata artifactMetadata;
        private final ConfigurationMetadata graphSelectedVariant;
        private final Set<? extends VariantResolveMetadata> fallbackVariants;
        private final Set<? extends VariantResolveMetadata> allVariants;

        public DefaultVariantArtifactResolveState(ComponentGraphResolveMetadata graphMetadata, ComponentResolveMetadata artifactMetadata, ConfigurationMetadata graphSelectedVariant) {
            this.artifactMetadata = artifactMetadata;
            this.graphSelectedVariant = graphSelectedVariant;
            this.fallbackVariants = graphSelectedVariant.getVariants();
            Optional<List<? extends VariantGraphResolveMetadata>> variantsForGraphTraversal = graphMetadata.getVariantsForGraphTraversal();
            allVariants = buildAllVariants(fallbackVariants, variantsForGraphTraversal);
        }

        @Override
        public ComponentArtifactMetadata resolveArtifact(IvyArtifactName artifact) {
            return graphSelectedVariant.artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            return artifactSelector.resolveArtifacts(new ExternalArtifactResolveMetadata(artifactMetadata), () -> allVariants, fallbackVariants, exclusions, overriddenAttributes);
        }

        private static Set<? extends VariantResolveMetadata> buildAllVariants(Set<? extends VariantResolveMetadata> fallbackVariants, Optional<List<? extends VariantGraphResolveMetadata>> variantsForGraphTraversal) {
            final Set<? extends VariantResolveMetadata> allVariants;
            if (variantsForGraphTraversal.isPresent()) {
                allVariants = variantsForGraphTraversal.get().stream().map(ModuleConfigurationMetadata.class::cast).flatMap(variant -> variant.getVariants().stream()).collect(Collectors.toSet());
            } else {
                allVariants = fallbackVariants;
            }
            return allVariants;
        }
    }

    private static class ExternalArtifactResolveMetadata implements ComponentArtifactResolveMetadata {
        private final ComponentResolveMetadata metadata;

        public ExternalArtifactResolveMetadata(ComponentResolveMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public ComponentIdentifier getId() {
            return metadata.getId();
        }

        @Override
        public ModuleVersionIdentifier getModuleVersionId() {
            return metadata.getModuleVersionId();
        }

        @Override
        public ModuleSources getSources() {
            return metadata.getSources();
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return metadata.getAttributes();
        }

        @Override
        public AttributesSchemaInternal getAttributesSchema() {
            return metadata.getAttributesSchema();
        }

        @Override
        public ComponentResolveMetadata getMetadata() {
            return metadata;
        }
    }
}
