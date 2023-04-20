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
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.resolve.resolver.ArtifactSelector;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Holds the resolution state for an external component.
 */
public class DefaultComponentGraphResolveState<T extends ComponentGraphResolveMetadata, S extends ComponentResolveMetadata> extends AbstractComponentGraphResolveState<T, S> {
    // The artifact resolve state for each variant of this component
    private final ConcurrentMap<ConfigurationMetadata, DefaultVariantArtifactResolveState> variants = new ConcurrentHashMap<>();
    // The variants of this component to use when variant reselection is enabled
    private final Optional<Set<? extends VariantResolveMetadata>> allVariantsForArtifactSelection;
    // The public view of all selectable variants of this component
    private final List<ResolvedVariantResult> selectableVariantResults;

    public DefaultComponentGraphResolveState(T graphMetadata, S artifactMetadata, AttributeDesugaring attributeDesugaring) {
        super(graphMetadata, artifactMetadata, attributeDesugaring);
        allVariantsForArtifactSelection = graphMetadata.getVariantsForGraphTraversal().map(variants ->
            variants.stream()
                .map(ModuleConfigurationMetadata.class::cast)
                .flatMap(variant -> variant.getVariants().stream())
                .collect(Collectors.toSet()));
        selectableVariantResults = graphMetadata.getVariantsForGraphTraversal().orElse(Collections.emptyList()).stream().
            flatMap(variant -> variant.getVariants().stream()).
            map(variant -> new DefaultResolvedVariantResult(
                getId(),
                Describables.of(variant.getName()),
                attributeDesugaring.desugar(variant.getAttributes().asImmutable()),
                capabilitiesFor(variant.getCapabilities()),
                null
            )).
            collect(Collectors.toList());
    }

    @Override
    public ComponentArtifactResolveMetadata getResolveMetadata() {
        return new ExternalArtifactResolveMetadata(getArtifactMetadata());
    }

    @Override
    public List<ResolvedVariantResult> getAllSelectableVariantResults() {
        return selectableVariantResults;
    }

    @Override
    public VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant) {
        return (VariantArtifactGraphResolveMetadata) variant;
    }

    @Override
    public VariantArtifactResolveState prepareForArtifactResolution(VariantGraphResolveMetadata variant) {
        ConfigurationMetadata configurationMetadata = (ConfigurationMetadata) variant;
        return variants.computeIfAbsent(configurationMetadata, c -> new DefaultVariantArtifactResolveState(getArtifactMetadata(), configurationMetadata, allVariantsForArtifactSelection));
    }

    private static class DefaultVariantArtifactResolveState implements VariantArtifactResolveState {
        private final ComponentResolveMetadata artifactMetadata;
        private final ConfigurationMetadata graphSelectedVariant;
        private final Set<? extends VariantResolveMetadata> legacyVariants;
        private final Set<? extends VariantResolveMetadata> allVariants;

        public DefaultVariantArtifactResolveState(ComponentResolveMetadata artifactMetadata, ConfigurationMetadata graphSelectedVariant, Optional<Set<? extends VariantResolveMetadata>> allVariantsForArtifactSelection) {
            this.artifactMetadata = artifactMetadata;
            this.graphSelectedVariant = graphSelectedVariant;
            this.legacyVariants = graphSelectedVariant.getVariants();
            allVariants = allVariantsForArtifactSelection.orElse(legacyVariants);
        }

        @Override
        public ComponentArtifactMetadata resolveArtifact(IvyArtifactName artifact) {
            return graphSelectedVariant.artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            return artifactSelector.resolveArtifacts(new ExternalArtifactResolveMetadata(artifactMetadata), new ExternalVariantArtifactSelectionMetadata(allVariants, legacyVariants), exclusions, overriddenAttributes);
        }
    }

    private static class ExternalVariantArtifactSelectionMetadata implements VariantArtifactSelectionMetadata {
        private final Set<? extends VariantResolveMetadata> allVariants;
        private final Set<? extends VariantResolveMetadata> legacyVariants;

        public ExternalVariantArtifactSelectionMetadata(Set<? extends VariantResolveMetadata> allVariants, Set<? extends VariantResolveMetadata> legacyVariants) {
            this.allVariants = allVariants;
            this.legacyVariants = legacyVariants;
        }

        @Override
        public Set<? extends VariantResolveMetadata> getAllVariants() {
            return allVariants;
        }

        @Override
        public Set<? extends VariantResolveMetadata> getLegacyVariants() {
            return legacyVariants;
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
