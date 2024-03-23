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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.GraphSelectionCandidates;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantArtifactGraphResolveMetadata;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.component.resolution.failure.exception.ConfigurationSelectionException;
import org.gradle.internal.component.resolution.failure.type.ConfigurationNotConsumableFailure;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.resolve.resolver.VariantArtifactResolver;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Holds the resolution state for a local component. The state is calculated as required, and an instance can be used for multiple resolutions across a build tree.
 *
 * <p>The aim is to create only a single instance of this type per project and reuse that for all resolution that happens in a build tree. This isn't quite the case yet.
 */
public class DefaultLocalComponentGraphResolveState extends AbstractComponentGraphResolveState<LocalComponentGraphResolveMetadata> implements LocalComponentGraphResolveState {
    private final ComponentIdGenerator idGenerator;
    private final boolean adHoc;

    // The graph resolve state for each configuration of this component
    private final ConcurrentMap<String, LocalVariantGraphResolveState> variants = new ConcurrentHashMap<>();

    // The variants to use for variant selection during graph resolution
    private final Lazy<List<? extends VariantGraphResolveState>> allVariantsForGraphResolution;

    // The public view of all selectable variants of this component
    private final Lazy<List<ResolvedVariantResult>> selectableVariantResults;

    public DefaultLocalComponentGraphResolveState(long instanceId, LocalComponentGraphResolveMetadata metadata, AttributeDesugaring attributeDesugaring, ComponentIdGenerator idGenerator, boolean adHoc) {
        super(instanceId, metadata, attributeDesugaring);
        this.allVariantsForGraphResolution = Lazy.locking().of(() ->
            metadata.getVariantsForGraphTraversal()
                .stream()
                .map(variant -> getVariantByConfigurationName(variant.getName()))
                .collect(Collectors.toList())
        );
        this.idGenerator = idGenerator;
        this.adHoc = adHoc;
        this.selectableVariantResults = Lazy.locking().of(() -> metadata.getVariantsForGraphTraversal().stream()
            .flatMap(variant -> variant.getVariants().stream())
            .map(variant -> new DefaultResolvedVariantResult(
                getId(),
                Describables.of(variant.getName()),
                attributeDesugaring.desugar(variant.getAttributes().asImmutable()),
                capabilitiesFor(variant.getCapabilities()),
                null
            ))
            .collect(Collectors.toList())
        );
    }

    @Override
    public void reevaluate() {
        variants.clear();
        getMetadata().reevaluate();
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return getMetadata().getModuleVersionId();
    }

    @Override
    public boolean isAdHoc() {
        return adHoc;
    }

    @Override
    public LocalComponentGraphResolveMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts) {
        return getMetadata().copy(componentIdentifier, artifacts);
    }

    @Override
    public ComponentArtifactResolveMetadata getResolveMetadata() {
        return new LocalComponentArtifactResolveMetadata(getMetadata());
    }

    @Override
    public ModuleSources getSources() {
        return ImmutableModuleSources.of();
    }

    @Override
    public List<ResolvedVariantResult> getAllSelectableVariantResults() {
        return selectableVariantResults.get();
    }

    @Override
    public GraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        return new LocalComponentGraphSelectionCandidates(this);
    }

    private List<? extends VariantGraphResolveState> getVariantsForGraphTraversal() {
        return allVariantsForGraphResolution.get();
    }

    @Nullable
    @Override
    public LocalVariantGraphResolveState getVariantByConfigurationName(String configurationName) {
        return variants.computeIfAbsent(configurationName, n -> {
            LocalVariantGraphResolveMetadata variant = getMetadata().getVariantByConfigurationName(configurationName);
            if (variant == null) {
                return null;
            } else {
                return new DefaultLocalVariantGraphResolveState(idGenerator.nextVariantId(), this, getMetadata(), variant);
            }
        });
    }

    private static class DefaultLocalVariantGraphResolveState extends AbstractVariantGraphResolveState implements LocalVariantGraphResolveState {
        private final long instanceId;
        private final LocalVariantGraphResolveMetadata variant;
        private final Lazy<DefaultLocalConfigurationArtifactResolveState> artifactResolveState;

        public DefaultLocalVariantGraphResolveState(long instanceId, AbstractComponentGraphResolveState<?> componentState, ComponentGraphResolveMetadata component, LocalVariantGraphResolveMetadata variant) {
            super(componentState);
            this.instanceId = instanceId;
            this.variant = variant;
            // We deliberately avoid locking the initialization of `artifactResolveState`.
            // This object may be shared across multiple worker threads, and the computation of
            // `legacyVariants` below is likely to require acquiring the state lock for the
            // project that owns this `Configuration` leading to a potential deadlock situation.
            // For instance, a thread could acquire the `artifactResolveState` lock while another thread,
            // which already owns the project lock, attempts to acquire the `artifactResolveState` lock.
            // See https://github.com/gradle/gradle/issues/25416
            this.artifactResolveState = Lazy.atomic().of(() -> {
                Set<? extends VariantResolveMetadata> legacyVariants = variant.prepareToResolveArtifacts().getVariants();
                return new DefaultLocalConfigurationArtifactResolveState(component, variant, legacyVariants);
            });
        }

        @Override
        public long getInstanceId() {
            return instanceId;
        }

        @Override
        public String getName() {
            return variant.getName();
        }

        @Override
        public LocalVariantGraphResolveMetadata getMetadata() {
            return variant;
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return variant.getAttributes();
        }

        @Override
        public ImmutableCapabilities getCapabilities() {
            return variant.getCapabilities();
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
        private final ComponentGraphResolveMetadata component;
        private final LocalVariantGraphResolveMetadata graphSelectedConfiguration;
        private final Set<? extends VariantResolveMetadata> variants;

        public DefaultLocalConfigurationArtifactResolveState(ComponentGraphResolveMetadata component, LocalVariantGraphResolveMetadata graphSelectedConfiguration, Set<? extends VariantResolveMetadata> variants) {
            this.component = component;
            this.graphSelectedConfiguration = graphSelectedConfiguration;
            this.variants = variants;
        }

        @Override
        public List<? extends ComponentArtifactMetadata> getArtifacts() {
            return graphSelectedConfiguration.prepareToResolveArtifacts().getArtifacts();
        }

        @Override
        public ResolvedVariant resolveAdhocVariant(VariantArtifactResolver variantResolver, List<IvyArtifactName> dependencyArtifacts) {
            ImmutableList.Builder<ComponentArtifactMetadata> artifacts = ImmutableList.builderWithExpectedSize(dependencyArtifacts.size());
            for (IvyArtifactName dependencyArtifact : dependencyArtifacts) {
                artifacts.add(getArtifactWithName(dependencyArtifact));
            }
            return variantResolver.resolveAdhocVariant(new LocalComponentArtifactResolveMetadata(component), artifacts.build());
        }

        private ComponentArtifactMetadata getArtifactWithName(IvyArtifactName ivyArtifactName) {
            for (ComponentArtifactMetadata candidate : getArtifacts()) {
                if (candidate.getName().equals(ivyArtifactName)) {
                    return candidate;
                }
            }

            return new MissingLocalArtifactMetadata(component.getId(), ivyArtifactName);
        }

        @Override
        public Set<? extends VariantResolveMetadata> getArtifactVariants() {
            return variants;
        }
    }

    private static class LocalComponentArtifactResolveMetadata implements ComponentArtifactResolveMetadata {
        private final ComponentGraphResolveMetadata metadata;

        public LocalComponentArtifactResolveMetadata(ComponentGraphResolveMetadata metadata) {
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
            return ImmutableModuleSources.of();
        }

        @Override
        public ImmutableAttributes getAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        public AttributesSchemaInternal getAttributesSchema() {
            return metadata.getAttributesSchema();
        }
    }

    private static class LocalComponentGraphSelectionCandidates implements GraphSelectionCandidates {
        private final List<? extends VariantGraphResolveState> variants;
        private final DefaultLocalComponentGraphResolveState component;

        public LocalComponentGraphSelectionCandidates(DefaultLocalComponentGraphResolveState component) {
            this.variants = component.getVariantsForGraphTraversal();
            this.component = component;
        }

        @Override
        public boolean supportsAttributeMatching() {
            return !variants.isEmpty();
        }

        @Override
        public List<? extends VariantGraphResolveState> getVariantsForAttributeMatching() {
            if (variants.isEmpty()) {
                throw new IllegalStateException("No variants available for attribute matching.");
            }
            return variants;
        }

        @Nullable
        @Override
        public VariantGraphResolveState getVariantByConfigurationName(String name) {
            LocalVariantGraphResolveState conf = component.getVariantByConfigurationName(name);
            if (conf == null) {
                return null;
            }

            // A variant must be consuamble to be selected in the graph.
            LocalVariantGraphResolveMetadata metadata = conf.getMetadata();
            if (!metadata.isCanBeConsumed()) {
                ConfigurationNotConsumableFailure failure = new ConfigurationNotConsumableFailure(name, component.getId().getDisplayName());
                String message = String.format("Selected configuration '" + failure.getRequestedName() + "' on '" + failure.getRequestedComponentDisplayName() + "' but it can't be used as a project dependency because it isn't intended for consumption by other components.");
                throw new ConfigurationSelectionException(message, failure, Collections.emptyList());
            }

            return conf;
        }
    }
}
