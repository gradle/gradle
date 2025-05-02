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
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ExternalComponentResolveMetadata;
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveMetadata;
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.lazy.Lazy;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link ExternalModuleComponentGraphResolveState}
 * <p>
 * The aim is to create only a single instance of this type per component and reuse that
 * for all resolution that happens in a build tree. This isn't quite the case yet.
 */
public class DefaultExternalModuleComponentGraphResolveState<G extends ExternalModuleComponentGraphResolveMetadata, A extends ExternalComponentResolveMetadata> extends AbstractComponentGraphResolveState<G> implements ExternalModuleComponentGraphResolveState {

    private final ComponentIdGenerator idGenerator;
    private final A legacyMetadata;

    // The resolve state for each configuration of this component
    private final ConcurrentMap<ModuleConfigurationMetadata, DefaultConfigurationGraphResolveState> variants = new ConcurrentHashMap<>();

    // The variants to use for variant selection during graph resolution
    private final Lazy<List<? extends VariantGraphResolveState>> allVariantsForGraphResolution;

    // The public view of all selectable variants of this component
    private final List<ResolvedVariantResult> selectableVariantResults;

    public DefaultExternalModuleComponentGraphResolveState(long instanceId, G graphMetadata, A legacyMetadata, AttributeDesugaring attributeDesugaring, ComponentIdGenerator idGenerator) {
        super(instanceId, graphMetadata, attributeDesugaring);
        this.legacyMetadata = legacyMetadata;
        this.allVariantsForGraphResolution = Lazy.locking().of(() ->
            graphMetadata.getVariantsForGraphTraversal().stream()
                .map(ModuleConfigurationMetadata.class::cast)
                .map(variant -> resolveStateFor(variant).asVariant())
                .collect(Collectors.toList())
        );
        this.idGenerator = idGenerator;
        this.selectableVariantResults = graphMetadata.getVariantsForGraphTraversal().stream()
            .map(ConfigurationMetadata.class::cast)
            .flatMap(variant -> variant.getArtifactVariants().stream())
            .map(variant -> new DefaultResolvedVariantResult(
                getId(),
                Describables.of(variant.getName()),
                attributeDesugaring.desugar(variant.getAttributes().asImmutable()),
                capabilitiesFor(variant.getCapabilities()),
                null
            ))
            .collect(Collectors.toList());
    }

    @Override
    public ModuleComponentIdentifier getId() {
        return getMetadata().getId();
    }

    @Override
    @Deprecated
    public A getLegacyMetadata() {
        return legacyMetadata;
    }

    @Override
    public ComponentArtifactResolveMetadata getArtifactMetadata() {
        A legacyMetadata = getLegacyMetadata();
        return new ExternalArtifactResolveMetadata(legacyMetadata);
    }

    @Override
    public List<ResolvedVariantResult> getAllSelectableVariantResults() {
        return selectableVariantResults;
    }

    @Override
    public GraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        return new ExternalGraphSelectionCandidates(this);
    }

    protected ConfigurationGraphResolveState resolveStateFor(ModuleConfigurationMetadata configuration) {
        return variants.computeIfAbsent(configuration, c -> newVariantState(configuration));
    }

    private DefaultConfigurationGraphResolveState newVariantState(ModuleConfigurationMetadata configuration) {
        return new DefaultConfigurationGraphResolveState(idGenerator.nextVariantId(), configuration);
    }

    private static class DefaultConfigurationGraphResolveState implements VariantGraphResolveState, ConfigurationGraphResolveState {
        private final long instanceId;
        private final ModuleConfigurationMetadata configuration;
        private final DefaultConfigurationArtifactResolveState artifactResolveState;

        public DefaultConfigurationGraphResolveState(long instanceId, ModuleConfigurationMetadata configuration) {
            this.instanceId = instanceId;
            this.configuration = configuration;
            this.artifactResolveState = new DefaultConfigurationArtifactResolveState(configuration);
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
        public ImmutableCapabilities getCapabilities() {
            return configuration.getCapabilities();
        }

        @Override
        public List<? extends DependencyMetadata> getDependencies() {
            return configuration.getDependencies();
        }

        @Override
        public List<? extends ExcludeMetadata> getExcludes() {
            return configuration.getExcludes();
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
        public VariantArtifactResolveState prepareForArtifactResolution() {
            return artifactResolveState;
        }
    }

    private static class DefaultConfigurationArtifactResolveState implements VariantArtifactResolveState {
        private final ConfigurationMetadata configuration;

        public DefaultConfigurationArtifactResolveState(ConfigurationMetadata configuration) {
            this.configuration = configuration;
        }

        @Override
        public ImmutableList<ComponentArtifactMetadata> getAdhocArtifacts(List<IvyArtifactName> dependencyArtifacts) {
            ImmutableList.Builder<ComponentArtifactMetadata> artifacts = ImmutableList.builderWithExpectedSize(dependencyArtifacts.size());
            for (IvyArtifactName dependencyArtifact : dependencyArtifacts) {
                artifacts.add(configuration.artifact(dependencyArtifact));
            }
            return artifacts.build();
        }

        @Override
        public Set<? extends VariantResolveMetadata> getArtifactVariants() {
            return configuration.getArtifactVariants();
        }
    }

    protected static class ExternalArtifactResolveMetadata implements ComponentArtifactResolveMetadata {
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
        public ImmutableAttributesSchema getAttributesSchema() {
            return metadata.getAttributesSchema();
        }
    }

    private static class ExternalGraphSelectionCandidates implements GraphSelectionCandidates {
        private final List<? extends VariantGraphResolveState> variants;
        private final DefaultExternalModuleComponentGraphResolveState<?, ?> component;

        public ExternalGraphSelectionCandidates(DefaultExternalModuleComponentGraphResolveState<?, ?> component) {
            this.variants = component.allVariantsForGraphResolution.get();
            this.component = component;
        }

        @Override
        public List<? extends VariantGraphResolveState> getVariantsForAttributeMatching() {
            return variants;
        }

        @Nullable
        @Override
        public VariantGraphResolveState getLegacyVariant() {
            ConfigurationGraphResolveMetadata defaultVariant = component.getMetadata().getConfiguration(Dependency.DEFAULT_CONFIGURATION);
            if (defaultVariant == null) {
                return null;
            }

            return component.resolveStateFor((ModuleConfigurationMetadata) defaultVariant).asVariant();
        }

    }
}
