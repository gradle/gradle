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
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.AbstractComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveState;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
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

    // The graph resolve state for each configuration of this component
    private final ConcurrentMap<String, DefaultLocalConfigurationGraphResolveState> configurations = new ConcurrentHashMap<>();

    // The variants to use for variant selection during graph resolution
    private final Lazy<LocalComponentGraphSelectionCandidates> graphSelectionCandidates;

    // The public view of all selectable variants of this component
    private final Lazy<List<ResolvedVariantResult>> selectableVariantResults;

    public DefaultLocalComponentGraphResolveState(
        long instanceId,
        LocalComponentGraphResolveMetadata metadata,
        AttributeDesugaring attributeDesugaring,
        ComponentIdGenerator idGenerator,
        boolean adHoc,
        ConfigurationMetadataFactory configurationFactory,
        @Nullable Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer
    ) {
        super(instanceId, metadata, attributeDesugaring);
        this.idGenerator = idGenerator;
        this.adHoc = adHoc;
        this.configurationFactory = configurationFactory;
        this.artifactTransformer = artifactTransformer;

        // TODO: We should be using the CalculatedValue infrastructure to lazily compute these values
        //       in order to properly manage project locks.
        this.graphSelectionCandidates = Lazy.locking().of(() ->
            computeGraphSelectionCandidates(this, idGenerator, configurationFactory, artifactTransformer)
        );
        this.selectableVariantResults = Lazy.locking().of(() ->
            computeSelectableVariantResults(this)
        );
    }

    @Override
    public void reevaluate() {
        // TODO: This is not thread-safe. We do not atomically clear all the different fields at once.
        configurations.clear();
        configurationFactory.invalidate();
        // TODO: We are missing logic to invalidate allVariantsForGraphResolution and selectableVariantResults.
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
            cachedTransformer
        );
    }

    @Override
    public ComponentArtifactResolveMetadata getArtifactMetadata() {
        return new LocalComponentArtifactResolveMetadata(getMetadata());
    }

    @Override
    public LocalComponentGraphSelectionCandidates getCandidatesForGraphVariantSelection() {
        return graphSelectionCandidates.get();
    }

    private static LocalComponentGraphSelectionCandidates computeGraphSelectionCandidates(
        DefaultLocalComponentGraphResolveState component,
        ComponentIdGenerator idGenerator,
        ConfigurationMetadataFactory configurationFactory,
        @Nullable Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer
    ) {
        ImmutableList.Builder<VariantGraphResolveState> configurationsWithAttributes = new ImmutableList.Builder<>();
        ImmutableMap.Builder<String, VariantGraphResolveState> configurationByName = ImmutableMap.builder();

        configurationFactory.visitConsumableConfigurations(configuration -> {
            if (artifactTransformer != null) {
                configuration = configuration.copyWithTransformedArtifacts(artifactTransformer);
            }

            VariantGraphResolveState variantState = new DefaultLocalConfigurationGraphResolveState(
                idGenerator.nextVariantId(), component, component.getMetadata(), configuration
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
        return selectableVariantResults.get();
    }

    private static List<ResolvedVariantResult> computeSelectableVariantResults(DefaultLocalComponentGraphResolveState component) {
        GraphSelectionCandidates candidates = component.getCandidatesForGraphVariantSelection();
        if (!candidates.supportsAttributeMatching()) {
            return Collections.emptyList();
        }

        return candidates
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
            return new DefaultLocalConfigurationGraphResolveState(idGenerator.nextVariantId(), this, getMetadata(), md);
        });
    }

    private static class DefaultLocalConfigurationGraphResolveState extends AbstractVariantGraphResolveState implements VariantGraphResolveState, ConfigurationGraphResolveState {
        private final long instanceId;
        private final LocalConfigurationGraphResolveMetadata configuration;
        private final Lazy<DefaultLocalConfigurationArtifactResolveState> artifactResolveState;

        public DefaultLocalConfigurationGraphResolveState(long instanceId, AbstractComponentGraphResolveState<?> componentState, ComponentGraphResolveMetadata component, LocalConfigurationGraphResolveMetadata configuration) {
            super(componentState);
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
            return artifactResolveState.get();
        }

        @Override
        public VariantArtifactResolveState prepareForArtifactResolution() {
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
        public boolean supportsAttributeMatching() {
            return !variantsWithAttributes.isEmpty();
        }

        @Override
        public List<? extends VariantGraphResolveState> getVariantsForAttributeMatching() {
            if (variantsWithAttributes.isEmpty()) {
                throw new IllegalStateException("No variants available for attribute matching.");
            }
            return variantsWithAttributes;
        }

        @Nullable
        @Override
        public VariantGraphResolveState getVariantByConfigurationName(String name) {
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

            ConfigurationNotConsumableFailure failure = new ConfigurationNotConsumableFailure(name, component.getId().getDisplayName());
            String message = String.format("Selected configuration '" + failure.getRequestedName() + "' on '" + failure.getRequestedComponentDisplayName() + "' but it can't be used as a project dependency because it isn't intended for consumption by other components.");
            throw new ConfigurationSelectionException(message, failure, Collections.emptyList());
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

    /**
     * Constructs {@link LocalConfigurationGraphResolveMetadata} instances advertised by a given component.
     * This allows the component metadata to source configuration data from multiple sources, both lazy and eager.
     */
    public interface ConfigurationMetadataFactory {

        /**
         * Visit all configurations in this component that can be selected in a dependency graph.
         *
         * <p>This includes all consumable configurations with and without attributes. Configurations visited
         * by this method may not be suitable for selection via attribute matching.</p>
         */
        void visitConsumableConfigurations(Consumer<LocalConfigurationGraphResolveMetadata> visitor);

        /**
         * Invalidates any caching used for producing configuration metadata.
         */
        void invalidate();

        /**
         * Produces a configuration metadata instance from the configuration with the given {@code name}.
         *
         * @return Null if the configuration with the given name does not exist.
         */
        @Nullable
        LocalConfigurationGraphResolveMetadata getConfiguration(String name);

        /**
         * Get all names such that {@link #getConfiguration(String)} return a non-null value.
         */
        Set<String> getConfigurationNames();
    }
}
