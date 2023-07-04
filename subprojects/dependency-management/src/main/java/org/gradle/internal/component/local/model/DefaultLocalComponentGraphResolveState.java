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

package org.gradle.internal.component.local.model;

import org.gradle.api.Transformer;
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
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantArtifactGraphResolveMetadata;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantArtifactSelectionCandidates;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantResolveMetadata;
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
 * Holds the resolution state for a local component. The state is calculated as required, and an instance can be used for multiple resolutions across a build tree.
 *
 * <p>The aim is to create only a single instance of this type per project and reuse that for all resolution that happens in a build tree. This isn't quite the case yet.
 */
public class DefaultLocalComponentGraphResolveState extends AbstractComponentGraphResolveState<LocalComponentMetadata, LocalComponentMetadata> implements LocalComponentGraphResolveState {
    private final ComponentIdGenerator idGenerator;

    // The graph resolve state for each configuration of this component
    private final ConcurrentMap<String, DefaultLocalConfigurationGraphResolveState> configurations = new ConcurrentHashMap<>();

    // The variants to use for variant selection during graph resolution
    private final Lazy<Optional<List<? extends VariantGraphResolveState>>> allVariantsForGraphResolution;

    // The variants of this component to use for artifact selection when variant reselection is enabled
    private final Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariantsForArtifactSelection;

    // The public view of all selectable variants of this component
    private final Lazy<List<ResolvedVariantResult>> selectableVariantResults;

    public DefaultLocalComponentGraphResolveState(long instanceId, LocalComponentMetadata metadata, AttributeDesugaring attributeDesugaring, ComponentIdGenerator idGenerator) {
        super(instanceId, metadata, metadata, attributeDesugaring);
        allVariantsForGraphResolution = Lazy.locking().of(() -> metadata.getVariantsForGraphTraversal().map(variants ->
            variants.stream()
                .map(variant -> getConfiguration(variant.getName()).asVariant())
                .collect(Collectors.toList())));
        allVariantsForArtifactSelection = Lazy.locking().of(() -> metadata.getVariantsForGraphTraversal().map(variants ->
            variants.stream().
                map(LocalConfigurationGraphResolveMetadata.class::cast).
                map(LocalConfigurationGraphResolveMetadata::prepareToResolveArtifacts).
                flatMap(variant -> variant.getVariants().stream()).
                collect(Collectors.toSet())));
        this.idGenerator = idGenerator;
        selectableVariantResults = Lazy.locking().of(() -> metadata.getVariantsForGraphTraversal().orElse(Collections.emptyList()).stream().
            map(LocalConfigurationGraphResolveMetadata.class::cast).
            flatMap(variant -> variant.getVariants().stream()).
            map(variant -> new DefaultResolvedVariantResult(
                getId(),
                Describables.of(variant.getName()),
                attributeDesugaring.desugar(variant.getAttributes().asImmutable()),
                capabilitiesFor(variant.getCapabilities()),
                null
            )).
            collect(Collectors.toList()));
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return getMetadata().getModuleVersionId();
    }

    @Override
    public LocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts) {
        return getMetadata().copy(componentIdentifier, artifacts);
    }

    @Override
    public ComponentArtifactResolveMetadata getResolveMetadata() {
        return new LocalComponentArtifactResolveMetadata(getMetadata());
    }

    @Override
    public ModuleSources getSources() {
        return getMetadata().getSources();
    }

    @Override
    public List<ResolvedVariantResult> getAllSelectableVariantResults() {
        return selectableVariantResults.get();
    }

    @Override
    protected Optional<List<? extends VariantGraphResolveState>> getVariantsForGraphTraversal() {
        return allVariantsForGraphResolution.get();
    }

    @Nullable
    @Override
    public ConfigurationGraphResolveState getConfiguration(String configurationName) {
        return configurations.computeIfAbsent(configurationName, n -> {
            LocalConfigurationMetadata configuration = getMetadata().getConfiguration(configurationName);
            if (configuration == null) {
                return null;
            } else {
                return new DefaultLocalConfigurationGraphResolveState(idGenerator.nextVariantId(), getMetadata(), configuration, allVariantsForArtifactSelection);
            }
        });
    }

    private class DefaultLocalConfigurationGraphResolveState extends AbstractVariantGraphResolveState implements VariantGraphResolveState, ConfigurationGraphResolveState {
        private final long instanceId;
        private final LocalConfigurationMetadata configuration;
        private final Lazy<DefaultLocalConfigurationArtifactResolveState> artifactResolveState;

        public DefaultLocalConfigurationGraphResolveState(long instanceId, LocalComponentMetadata component, LocalConfigurationMetadata configuration, Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariantsForArtifactSelection) {
            this.instanceId = instanceId;
            this.configuration = configuration;
            // We deliberately avoid locking the initialization of `artifactResolveState`.
            // This object may be shared across multiple worker threads, and the computation of
            // `legacyVariants` below is likely to require acquiring the state lock for the
            // project that owns this `Configuration` leading to a potential deadlock situation.
            // For instance, a thread could acquire the `artifactResolveState` lock while another thread,
            // which already owns the project lock, attempts to acquire the `artifactResolveState` lock.
            // See https://github.com/gradle/gradle/issues/25416
            this.artifactResolveState = Lazy.atomic().of(() -> {
                Set<? extends VariantResolveMetadata> legacyVariants = configuration.prepareToResolveArtifacts().getVariants();
                return new DefaultLocalConfigurationArtifactResolveState(component, configuration, allVariantsForArtifactSelection, legacyVariants);
            });
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
        public ConfigurationGraphResolveMetadata getMetadata() {
            return configuration;
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
        public VariantGraphResolveState asVariant() {
            return this;
        }

        @Override
        public VariantArtifactGraphResolveMetadata resolveArtifacts() {
            return artifactResolveState.get();
        }

        @Override
        public VariantArtifactResolveState prepareForArtifactResolution() {
            return artifactResolveState.get();
        }
    }

    private static class DefaultLocalConfigurationArtifactResolveState implements VariantArtifactResolveState, VariantArtifactGraphResolveMetadata {
        private final LocalComponentMetadata component;
        private final LocalConfigurationGraphResolveMetadata graphSelectedConfiguration;
        private final Set<? extends VariantResolveMetadata> legacyVariants;
        private final Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariants;

        public DefaultLocalConfigurationArtifactResolveState(LocalComponentMetadata component, LocalConfigurationGraphResolveMetadata graphSelectedConfiguration, Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariantsForArtifactSelection, Set<? extends VariantResolveMetadata> legacyVariants) {
            this.component = component;
            this.graphSelectedConfiguration = graphSelectedConfiguration;
            this.legacyVariants = legacyVariants;
            this.allVariants = allVariantsForArtifactSelection;
        }

        @Override
        public List<? extends ComponentArtifactMetadata> getArtifacts() {
            return graphSelectedConfiguration.prepareToResolveArtifacts().getArtifacts();
        }

        @Override
        public ComponentArtifactMetadata resolveArtifact(IvyArtifactName artifact) {
            return graphSelectedConfiguration.prepareToResolveArtifacts().artifact(artifact);
        }

        @Override
        public ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, ExcludeSpec exclusions, ImmutableAttributes overriddenAttributes) {
            return artifactSelector.resolveArtifacts(new LocalComponentArtifactResolveMetadata(component), new LocalVariantArtifactSelectionCandidates(allVariants, legacyVariants), exclusions, overriddenAttributes);
        }
    }

    private static class LocalVariantArtifactSelectionCandidates implements VariantArtifactSelectionCandidates {
        private final Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariants;
        private final Set<? extends VariantResolveMetadata> legacyVariants;

        public LocalVariantArtifactSelectionCandidates(Lazy<Optional<Set<? extends VariantResolveMetadata>>> allVariants, Set<? extends VariantResolveMetadata> legacyVariants) {
            this.allVariants = allVariants;
            this.legacyVariants = legacyVariants;
        }

        @Override
        public Set<? extends VariantResolveMetadata> getAllVariants() {
            return allVariants.get().orElse(legacyVariants);
        }

        @Override
        public Set<? extends VariantResolveMetadata> getLegacyVariants() {
            return legacyVariants;
        }
    }

    private static class LocalComponentArtifactResolveMetadata implements ComponentArtifactResolveMetadata {
        private final LocalComponentMetadata metadata;

        public LocalComponentArtifactResolveMetadata(LocalComponentMetadata metadata) {
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
