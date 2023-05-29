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
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ExternalComponentResolveMetadata;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.resolve.resolver.ArtifactSelector;

import javax.annotation.Nullable;
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
public class DefaultComponentGraphResolveState<T extends ComponentGraphResolveMetadata, S extends ExternalComponentResolveMetadata> extends AbstractComponentGraphResolveState<T, S> {
    private final ComponentIdGenerator idGenerator;

    // The resolve state for each configuration of this component
    private final ConcurrentMap<ModuleConfigurationMetadata, DefaultConfigurationGraphResolveState> variants = new ConcurrentHashMap<>();

    // The variants to use for variant selection during graph resolution
    private final Lazy<Optional<List<? extends VariantGraphResolveState>>> allVariantsForGraphResolution;

    // The variants of this component to use when variant reselection is enabled
    private final Optional<Set<? extends VariantResolveMetadata>> allVariantsForArtifactSelection;

    // The public view of all selectable variants of this component
    private final List<ResolvedVariantResult> selectableVariantResults;

    public DefaultComponentGraphResolveState(long instanceId, T graphMetadata, S artifactMetadata, AttributeDesugaring attributeDesugaring, ComponentIdGenerator idGenerator) {
        super(instanceId, graphMetadata, artifactMetadata, attributeDesugaring);
        allVariantsForGraphResolution = Lazy.locking().of(() -> graphMetadata.getVariantsForGraphTraversal().map(variants ->
            variants.stream()
                .map(ModuleConfigurationMetadata.class::cast)
                .map(variant -> resolveStateFor(variant).asVariant())
                .collect(Collectors.toList())));
        allVariantsForArtifactSelection = graphMetadata.getVariantsForGraphTraversal().map(variants ->
            variants.stream()
                .map(ModuleConfigurationMetadata.class::cast)
                .flatMap(variant -> variant.getVariants().stream())
                .collect(Collectors.toSet()));
        this.idGenerator = idGenerator;
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
    public ModuleSources getSources() {
        return getArtifactMetadata().getSources();
    }

    @Override
    public List<ResolvedVariantResult> getAllSelectableVariantResults() {
        return selectableVariantResults;
    }

    @Override
    protected Optional<List<? extends VariantGraphResolveState>> getVariantsForGraphTraversal() {
        return allVariantsForGraphResolution.get();
    }

    @Nullable
    @Override
    public ConfigurationGraphResolveState getConfiguration(String configurationName) {
        ModuleConfigurationMetadata configuration = (ModuleConfigurationMetadata) getMetadata().getConfiguration(configurationName);
        if (configuration == null) {
            return null;
        } else {
            return resolveStateFor(configuration);
        }
    }

    private DefaultConfigurationGraphResolveState resolveStateFor(ModuleConfigurationMetadata configuration) {
        return variants.computeIfAbsent(configuration, c -> newVariantState(configuration));
    }

    protected VariantGraphResolveState newResolveStateFor(ModuleConfigurationMetadata configuration) {
        return newVariantState(configuration);
    }

    private DefaultConfigurationGraphResolveState newVariantState(ModuleConfigurationMetadata configuration) {
        return new DefaultConfigurationGraphResolveState(idGenerator.nextVariantId(), getArtifactMetadata(), configuration, allVariantsForArtifactSelection);
    }

    private class DefaultConfigurationGraphResolveState extends AbstractVariantGraphResolveState implements VariantGraphResolveState, ConfigurationGraphResolveState {
        private final long instanceId;
        private final ModuleConfigurationMetadata configuration;
        private final Lazy<DefaultConfigurationArtifactResolveState> artifactResolveState;

        public DefaultConfigurationGraphResolveState(long instanceId, ExternalComponentResolveMetadata component, ModuleConfigurationMetadata configuration, Optional<Set<? extends VariantResolveMetadata>> allVariantsForArtifactSelection) {
            this.instanceId = instanceId;
            this.configuration = configuration;
            this.artifactResolveState = Lazy.locking().of(() -> new DefaultConfigurationArtifactResolveState(component, configuration, allVariantsForArtifactSelection));
        }

        @Override
        public long getInstanceId() {
            return instanceId;
        }

        @Override
        public String getName() {
            return configuration.getName();
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return configuration.getAttributes();
        }

        @Override
        public CapabilitiesMetadata getCapabilities() {
            return configuration.getCapabilities();
        }

        @Override
        public ConfigurationGraphResolveMetadata getMetadata() {
            return configuration;
        }

        @Override
        public VariantGraphResolveState asVariant() {
            return this;
        }

        @Override
        public VariantArtifactGraphResolveMetadata resolveArtifacts() {
            return configuration;
        }

        @Override
        public VariantArtifactResolveState prepareForArtifactResolution() {
            return artifactResolveState.get();
        }
    }

    private static class DefaultConfigurationArtifactResolveState implements VariantArtifactResolveState {
        private final ExternalComponentResolveMetadata artifactMetadata;
        private final ConfigurationMetadata graphSelectedConfiguration;
        private final Set<? extends VariantResolveMetadata> legacyVariants;
        private final Set<? extends VariantResolveMetadata> allVariants;

        public DefaultConfigurationArtifactResolveState(ExternalComponentResolveMetadata artifactMetadata, ConfigurationMetadata graphSelectedConfiguration, Optional<Set<? extends VariantResolveMetadata>> allVariantsForArtifactSelection) {
            this.artifactMetadata = artifactMetadata;
            this.graphSelectedConfiguration = graphSelectedConfiguration;
            this.legacyVariants = graphSelectedConfiguration.getVariants();
            allVariants = allVariantsForArtifactSelection.orElse(legacyVariants);
        }

        @Override
        public ComponentArtifactMetadata resolveArtifact(IvyArtifactName artifact) {
            return graphSelectedConfiguration.artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            return artifactSelector.resolveArtifacts(new ExternalArtifactResolveMetadata(artifactMetadata), new ExternalVariantArtifactSelectionCandidates(allVariants, legacyVariants), exclusions, overriddenAttributes);
        }
    }

    private static class ExternalVariantArtifactSelectionCandidates implements VariantArtifactSelectionCandidates {
        private final Set<? extends VariantResolveMetadata> allVariants;
        private final Set<? extends VariantResolveMetadata> legacyVariants;

        public ExternalVariantArtifactSelectionCandidates(Set<? extends VariantResolveMetadata> allVariants, Set<? extends VariantResolveMetadata> legacyVariants) {
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
        private final ExternalComponentResolveMetadata metadata;

        public ExternalArtifactResolveMetadata(ExternalComponentResolveMetadata metadata) {
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
