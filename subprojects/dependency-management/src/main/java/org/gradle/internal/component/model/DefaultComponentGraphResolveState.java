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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.resolve.resolver.ArtifactSelector;

import java.util.Set;
import java.util.stream.Collectors;

public class DefaultComponentGraphResolveState<T extends ComponentGraphResolveMetadata, S extends ComponentResolveMetadata> extends AbstractComponentGraphResolveState<T, S> {
    public DefaultComponentGraphResolveState(T graphMetadata, S artifactMetadata) {
        super(graphMetadata, artifactMetadata);
    }

    public static ComponentGraphResolveState of(ModuleComponentResolveMetadata metadata) {
        return new DefaultComponentGraphResolveState<>(metadata, metadata);
    }

    @Override
    public VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant) {
        return (VariantArtifactGraphResolveMetadata) variant;
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution(VariantGraphResolveMetadata variant) {
        ConfigurationMetadata configurationMetadata = (ConfigurationMetadata) variant;
        return new DefaultVariantArtifactResolveState(getMetadata(), getArtifactMetadata(), configurationMetadata);
    }

    private static class DefaultVariantArtifactResolveState implements VariantArtifactResolveState {
        private final ComponentGraphResolveMetadata graphMetadata;
        private final ComponentResolveMetadata artifactMetadata;
        private final ConfigurationMetadata graphSelectedVariant;

        public DefaultVariantArtifactResolveState(ComponentGraphResolveMetadata graphMetadata, ComponentResolveMetadata artifactMetadata, ConfigurationMetadata graphSelectedVariant) {
            this.graphMetadata = graphMetadata;
            this.artifactMetadata = artifactMetadata;
            this.graphSelectedVariant = graphSelectedVariant;
        }

        @Override
        public ComponentArtifactMetadata resolveArtifact(IvyArtifactName artifact) {
            return graphSelectedVariant.artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            // We do not currently cache ResolvedVariants for external modules
            return artifactSelector.resolveArtifacts(artifactMetadata, null, this::buildAllVariants, graphSelectedVariant.getVariants(), exclusions, overriddenAttributes);
        }

        private Set<? extends VariantResolveMetadata> buildAllVariants() {
            final Set<? extends VariantResolveMetadata> allVariants;
            if (graphMetadata.getVariantsForGraphTraversal().isPresent()) {
                allVariants = graphMetadata.getVariantsForGraphTraversal().get().stream().map(ModuleConfigurationMetadata.class::cast).flatMap(variant -> variant.getVariants().stream()).collect(Collectors.toSet());
            } else {
                allVariants = graphSelectedVariant.getVariants();
            }
            return allVariants;
        }
    }
}
