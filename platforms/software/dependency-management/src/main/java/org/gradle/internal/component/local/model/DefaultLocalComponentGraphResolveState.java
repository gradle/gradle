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
import com.google.common.collect.ImmutableMap;
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
import org.gradle.internal.component.ResolutionFailureHandler;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantArtifactGraphResolveMetadata;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueCache;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.resolve.resolver.VariantArtifactResolver;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Holds the resolution state for a local component. The state is calculated as required, and an instance can be used for multiple resolutions across a build tree.
 *
 * <p>The aim is to create only a single instance of this type per project and reuse that for all resolution that happens in a build tree. This isn't quite the case yet.
 */
public class DefaultLocalComponentGraphResolveState extends AbstractComponentGraphResolveState<LocalComponentGraphResolveMetadata> implements LocalComponentGraphResolveState {
    private final ComponentIdGenerator idGenerator;
    private final boolean adHoc;
    private final ConfigurationMetadataFactory configurationFactory;
    private final Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    // The graph resolve state for each configuration of this component
    private final CalculatedValueCache<String, DefaultLocalConfigurationGraphResolveState> configurations;

    // The variants to use for variant selection during graph resolution
    private final AtomicReference<CalculatedValue<LocalComponentGraphSelectionCandidates>> graphSelectionCandidates = new AtomicReference<>();

    // The public view of all selectable variants of this component
    private final AtomicReference<CalculatedValue<List<ResolvedVariantResult>>> selectableVariantResults = new AtomicReference<>();

    public DefaultLocalComponentGraphResolveState(
        long instanceId,
        LocalComponentGraphResolveMetadata metadata,
        AttributeDesugaring attributeDesugaring,
        ComponentIdGenerator idGenerator,
        boolean adHoc,
        ConfigurationMetadataFactory configurationFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        @Nullable Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer
    ) {
        super(instanceId, metadata, attributeDesugaring);
        this.idGenerator = idGenerator;
        this.adHoc = adHoc;
        this.configurationFactory = configurationFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.artifactTransformer = artifactTransformer;

        // Mutable state
        this.configurations = calculatedValueContainerFactory.createCache(Describables.of("configurations"));
        initCalculatedValues();
    }

    @Override
    public void reevaluate() {
        // TODO: This is not really thread-safe.
        //       We should atomically clear all the different fields at once.
        //       Or better yet, we should not allow reevaluation of the state.
        configurations.clear();
        configurationFactory.invalidate();
        initCalculatedValues();
    }

    private void initCalculatedValues() {
        // TODO: We wrap the CalculatedValues in an AtomicReference so that we can reset their state, however
        //       CalculatedValues are not resettable for a reason. This is a pretty terrible hack.
        //       We should get rid of reevaluate entirely, so that we do not need these AtomicReferences.
        //       We are already on this path -- we deprecated mutating a configuration after observation.
        //       However, while mutation is still allowed, we need hacks like this, as plugins are relying
        //       on the deprecated behavior, for example the Spring dependency management plugin which adds
        //       excludes to dependencies in a beforeResolve.
        this.graphSelectionCandidates.set(
            calculatedValueContainerFactory.create(Describables.of("variants of", getMetadata()), context ->
                computeGraphSelectionCandidates(this, idGenerator, configurationFactory, calculatedValueContainerFactory, artifactTransformer)
            )
        );
        this.selectableVariantResults.set(
            calculatedValueContainerFactory.create(Describables.of("public variants of", getMetadata()), context ->
                computeSelectableVariantResults(this)
            )
        );
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
    public LocalComponentGraphResolveState copy(ComponentIdentifier newComponentId, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformer) {
        // Keep track of transformed artifacts as a given artifact may appear in multiple variants and configurations
        Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts = new HashMap<>();
        Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> cachedTransformer = oldArtifact ->
            transformedArtifacts.computeIfAbsent(oldArtifact, transformer::transform);

        LocalComponentGraphResolveMetadata originalMetadata = getMetadata();
        LocalComponentGraphResolveMetadata copiedMetadata = new LocalComponentGraphResolveMetadata(
            originalMetadata.getModuleVersionId(),
            newComponentId,
            originalMetadata.getStatus(),
            originalMetadata.getAttributesSchema()
        );

        return new DefaultLocalComponentGraphResolveState(
            idGenerator.nextComponentId(),
            copiedMetadata,
            getAttributeDesugaring(),
            idGenerator,
            adHoc,
            configurationFactory,
            calculatedValueContainerFactory,
            cachedTransformer
        );
    }

    @Override
    public ComponentArtifactResolveMetadata getArtifactMetadata() {
        return new LocalComponentArtifactResolveMetadata(getMetadata());
    }

    @Override
    public LocalComponentGraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        CalculatedValue<LocalComponentGraphSelectionCandidates> value = graphSelectionCandidates.get();
        value.finalizeIfNotAlready();
        return value.get();
    }

    private static LocalComponentGraphSelectionCandidates computeGraphSelectionCandidates(
        DefaultLocalComponentGraphResolveState component,
        ComponentIdGenerator idGenerator,
        ConfigurationMetadataFactory configurationFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        @Nullable Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer
    ) {
        ImmutableList.Builder<VariantGraphResolveState> configurationsWithAttributes = new ImmutableList.Builder<>();
        ImmutableMap.Builder<String, VariantGraphResolveState> configurationByName = ImmutableMap.builder();

        configurationFactory.visitConsumableConfigurations(configuration -> {
            if (artifactTransformer != null) {
                configuration = configuration.copyWithTransformedArtifacts(artifactTransformer);
            }

            VariantGraphResolveState variantState = new DefaultLocalConfigurationGraphResolveState(
                idGenerator.nextVariantId(), component, component.getMetadata(), configuration, calculatedValueContainerFactory
            ).asVariant();

            if (!configuration.getAttributes().isEmpty()) {
                configurationsWithAttributes.add(variantState);
            }

            configurationByName.put(configuration.getName(), variantState);
        });

        return new DefaultLocalComponentGraphSelectionCandidates(
            configurationsWithAttributes.build(),
            configurationByName.build(),
            component
        );
    }

    @Override
    public List<ResolvedVariantResult> getAllSelectableVariantResults() {
        CalculatedValue<List<ResolvedVariantResult>> value = selectableVariantResults.get();
        value.finalizeIfNotAlready();
        return value.get();
    }

    private static List<ResolvedVariantResult> computeSelectableVariantResults(DefaultLocalComponentGraphResolveState component) {
        return component.getCandidatesForGraphVariantSelection()
            .getVariantsForAttributeMatching()
            .stream()
            .flatMap(variant -> variant.prepareForArtifactResolution().getArtifactVariants().stream())
            .map(variant -> new DefaultResolvedVariantResult(
                component.getId(),
                Describables.of(variant.getName()),
                component.getAttributeDesugaring().desugar(variant.getAttributes().asImmutable()),
                component.capabilitiesFor(variant.getCapabilities()),
                null
            ))
            .collect(Collectors.toList());
    }

    @Override
    public Set<String> getConfigurationNames() {
        return configurationFactory.getConfigurationNames();
    }

    @Nullable
    @Override
    public ConfigurationGraphResolveState getConfiguration(String configurationName) {
        return configurations.computeIfAbsent(configurationName, n -> {
            LocalConfigurationGraphResolveMetadata md = configurationFactory.getConfiguration(configurationName);
            if (md == null) {
                return null;
            }
            if (artifactTransformer != null) {
                md = md.copyWithTransformedArtifacts(artifactTransformer);
            }
            return new DefaultLocalConfigurationGraphResolveState(idGenerator.nextVariantId(), this, getMetadata(), md, calculatedValueContainerFactory);
        });
    }

    private static class DefaultLocalConfigurationGraphResolveState extends AbstractVariantGraphResolveState implements VariantGraphResolveState, ConfigurationGraphResolveState {
        private final long instanceId;
        private final LocalConfigurationGraphResolveMetadata configuration;
        private final CalculatedValue<DefaultLocalConfigurationArtifactResolveState> artifactResolveState;

        public DefaultLocalConfigurationGraphResolveState(
            long instanceId,
            AbstractComponentGraphResolveState<?> componentState,
            ComponentGraphResolveMetadata component,
            LocalConfigurationGraphResolveMetadata configuration,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            super(componentState);
            this.instanceId = instanceId;
            this.configuration = configuration;
            this.artifactResolveState = calculatedValueContainerFactory.create(Describables.of("artifacts of", configuration), context -> {
                Set<? extends VariantResolveMetadata> legacyVariants = configuration.prepareToResolveArtifacts().getVariants();
                return new DefaultLocalConfigurationArtifactResolveState(component, configuration, legacyVariants);
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
        public String toString() {
            return configuration.toString();
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
        public ImmutableCapabilities getCapabilities() {
            return configuration.getCapabilities();
        }

        @Override
        public VariantGraphResolveState asVariant() {
            return this;
        }

        @Override
        public VariantArtifactGraphResolveMetadata resolveArtifacts() {
            artifactResolveState.finalizeIfNotAlready();
            return artifactResolveState.get();
        }

        @Override
        public VariantArtifactResolveState prepareForArtifactResolution() {
            artifactResolveState.finalizeIfNotAlready();
            return artifactResolveState.get();
        }
    }

    private static class DefaultLocalConfigurationArtifactResolveState implements VariantArtifactResolveState, VariantArtifactGraphResolveMetadata {
        private final ComponentGraphResolveMetadata component;
        private final LocalConfigurationGraphResolveMetadata graphSelectedConfiguration;
        private final Set<? extends VariantResolveMetadata> variants;

        public DefaultLocalConfigurationArtifactResolveState(ComponentGraphResolveMetadata component, LocalConfigurationGraphResolveMetadata graphSelectedConfiguration, Set<? extends VariantResolveMetadata> variants) {
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
                artifacts.add(graphSelectedConfiguration.prepareToResolveArtifacts().artifact(dependencyArtifact));
            }
            return variantResolver.resolveAdhocVariant(new LocalComponentArtifactResolveMetadata(component), artifacts.build());
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

    private static class DefaultLocalComponentGraphSelectionCandidates implements LocalComponentGraphSelectionCandidates {
        private final List<? extends VariantGraphResolveState> variantsWithAttributes;
        private final Map<String, ? extends VariantGraphResolveState> variantsByConfigurationName;
        private final AbstractComponentGraphResolveState<?> component;

        public DefaultLocalComponentGraphSelectionCandidates(
            List<? extends VariantGraphResolveState> variantsWithAttributes,
            Map<String, ? extends VariantGraphResolveState> variantsByConfigurationName,
            AbstractComponentGraphResolveState<?> component
        ) {
            this.variantsWithAttributes = variantsWithAttributes;
            this.variantsByConfigurationName = variantsByConfigurationName;
            this.component = component;
        }

        @Override
        public List<? extends VariantGraphResolveState> getVariantsForAttributeMatching() {
            return variantsWithAttributes;
        }

        @Nullable
        @Override
        public VariantGraphResolveState getVariantByConfigurationName(String name, ResolutionFailureHandler failureHandler) {
            VariantGraphResolveState variant = variantsByConfigurationName.get(name);
            if (variant != null) {
                return variant;
            }

            // There is no consumable variant with the given name.
            // Perhaps there is a configuration with the same name, but it is not consumable.
            // In that case, throw an error.
            ConfigurationGraphResolveState conf = component.getConfiguration(name);
            if (conf == null) {
                return null;
            }

            // If the configuration exists, it must not be consumable, since variantsByConfigurationName contains
            // all consumable configurations.
            assert !conf.getMetadata().isCanBeConsumed() : "Expected configuration to be non-consumable";

            throw failureHandler.nonConsumableConfigurationFailure(name, component.getId());
        }

        @Override
        public List<VariantGraphResolveState> getAllSelectableVariants() {
            // Find the names of all selectable configurations that are not in the variantsWithAttributes
            Set<String> configurationNames = new HashSet<>(variantsByConfigurationName.keySet());
            for (VariantGraphResolveState variant : variantsWithAttributes) {
                configurationNames.remove(variant.getName());
            }

            // Join the list of variants with attributes with the list of variants by configuration name
            List<VariantGraphResolveState> result = new ArrayList<>(variantsWithAttributes);
            for (String configurationName : configurationNames) {
                result.add(variantsByConfigurationName.get(configurationName));
            }

            return result;
        }
    }

}
