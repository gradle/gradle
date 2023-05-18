/*
 * Copyright 2013 the original author or authors.
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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalConfigurationMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Default implementation of {@link LocalComponentMetadata}. This component is lazy in that it
 * will only initialize {@link LocalConfigurationMetadata} instances on-demand as they are needed.
 * <p>
 * TODO: Eventually, this class should be updated to only create metadata instances for consumable configurations.
 * However, we currently need to expose resolvable configuration since the
 * {@link org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState} implementation
 * sources its root component metadata, for a resolvable configuration, from this component metadata.
 */
@SuppressWarnings("JavadocReference")
public final class DefaultLocalComponentMetadata implements LocalComponentMetadata {

    private static final ModuleSources MODULE_SOURCES = ImmutableModuleSources.of();

    private final ComponentIdentifier componentId;
    private final ModuleVersionIdentifier moduleVersionId;
    private final String status;
    private final AttributesSchemaInternal attributesSchema;
    private final ConfigurationMetadataFactory configurationFactory;
    private final Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer;

    private final Map<String, LocalConfigurationMetadata> allConfigurations = new LinkedHashMap<>();
    private Optional<List<? extends VariantGraphResolveMetadata>> consumableConfigurations;

    public DefaultLocalComponentMetadata(
        ModuleVersionIdentifier moduleVersionId,
        ComponentIdentifier componentId,
        String status,
        AttributesSchemaInternal attributesSchema,
        ConfigurationMetadataFactory configurationFactory,
        @Nullable Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer
    ) {
        this.moduleVersionId = moduleVersionId;
        this.componentId = componentId;
        this.status = status;
        this.attributesSchema = attributesSchema;
        this.configurationFactory = configurationFactory;
        this.artifactTransformer = artifactTransformer;
    }

    @Override
    public ComponentIdentifier getId() {
        return componentId;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return moduleVersionId;
    }

    /**
     * Creates a copy of this metadata, transforming the artifacts of this component.
     */
    @Override
    public DefaultLocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformer) {
        // Keep track of transformed artifacts as a given artifact may appear in multiple variants and configurations
        Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts = new HashMap<>();
        Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> cachedTransformer = oldArtifact ->
            transformedArtifacts.computeIfAbsent(oldArtifact, transformer::transform);

        return new DefaultLocalComponentMetadata(moduleVersionId, componentIdentifier, status, attributesSchema, configurationFactory, cachedTransformer);
    }

    @Override
    public String toString() {
        return componentId.getDisplayName();
    }

    @Override
    public ModuleSources getSources() {
        return MODULE_SOURCES;
    }

    @Override
    public boolean isMissing() {
        return false;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public List<String> getStatusScheme() {
        return DEFAULT_STATUS_SCHEME;
    }

    @Override
    public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
        return ImmutableList.of();
    }

    @Override
    public Set<String> getConfigurationNames() {
        return configurationFactory.getConfigurationNames();
    }

    /**
     * For a local project component, the `variantsForGraphTraversal` are any _consumable_ configurations that have attributes defined.
     */
    @Override
    public synchronized Optional<List<? extends VariantGraphResolveMetadata>> getVariantsForGraphTraversal() {
        if (consumableConfigurations == null) {
            ImmutableList.Builder<VariantGraphResolveMetadata> builder = new ImmutableList.Builder<>();
            configurationFactory.visitConfigurations(candidate -> {
                if (candidate.isConsumable() && candidate.hasAttributes()) {
                    builder.add(getConfiguration(candidate.getName()));
                }
            });

            ImmutableList<VariantGraphResolveMetadata> variants = builder.build();
            consumableConfigurations = !variants.isEmpty() ? Optional.of(variants) : Optional.empty();
        }
        return consumableConfigurations;
    }

    @Override
    public LocalConfigurationMetadata getConfiguration(final String name) {
        LocalConfigurationMetadata md = allConfigurations.get(name);
        if (md == null) {
            md = configurationFactory.getConfiguration(name, this);
            if (md == null) {
                return null;
            }
            if (artifactTransformer != null) {
                md = md.copy(artifactTransformer);
            }
            allConfigurations.put(name, md);
        }
        return md;
    }

    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return attributesSchema;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        // a local component cannot have attributes (for now). However, variants of the component
        // itself may.
        return ImmutableAttributes.EMPTY;
    }

    @Override
    public void reevaluate() {
        allConfigurations.clear();
        configurationFactory.invalidate();
        synchronized (this) {
            consumableConfigurations = null;
        }
    }

    @Override
    public boolean isConfigurationRealized(String configName) {
        return allConfigurations.get(configName) != null;
    }

    /**
     * Constructs {@link LocalConfigurationMetadata} given a configuration's name. This allows
     * the component metadata to source configuration data from multiple sources, both lazy and eager.
     */
    public interface ConfigurationMetadataFactory {
        void visitConfigurations(Consumer<Candidate> visitor);

        /**
         * Get the names of all configurations which this factory can produce.
         */
        Set<String> getConfigurationNames();

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
        LocalConfigurationMetadata getConfiguration(
            String name,
            DefaultLocalComponentMetadata parent
        );

        interface Candidate {
            String getName();

            boolean isConsumable();

            boolean hasAttributes();
        }
    }

    /**
     * A {@link ConfigurationMetadataFactory} which uses a map of pre-constructed configuration
     * metadata as its data source.
     */
    public static class ConfigurationsMapMetadataFactory implements ConfigurationMetadataFactory {
        private final Map<String, LocalConfigurationMetadata> metadata;

        public ConfigurationsMapMetadataFactory(Map<String, LocalConfigurationMetadata> metadata) {
            this.metadata = metadata;
        }

        @Override
        public Set<String> getConfigurationNames() {
            return metadata.keySet();
        }

        @Override
        public void visitConfigurations(Consumer<Candidate> visitor) {
            for (LocalConfigurationMetadata configuration : metadata.values()) {
                visitor.accept(new Candidate() {
                    @Override
                    public String getName() {
                        return configuration.getName();
                    }

                    @Override
                    public boolean isConsumable() {
                        return configuration.isCanBeConsumed();
                    }

                    @Override
                    public boolean hasAttributes() {
                        return !configuration.getAttributes().isEmpty();
                    }
                });
            }
        }

        @Override
        public void invalidate() {}

        @Override
        public LocalConfigurationMetadata getConfiguration(
            String name,
            DefaultLocalComponentMetadata parent
        ) {
            return metadata.get(name);
        }
    }

    /**
     * A {@link ConfigurationMetadataFactory} which uses a {@link ConfigurationsProvider} as its data source.
     */
    public static class ConfigurationsProviderMetadataFactory implements ConfigurationMetadataFactory {

        private final ConfigurationsProvider configurationsProvider;
        private final LocalConfigurationMetadataBuilder metadataBuilder;
        private final ModelContainer<?> model;
        private final CalculatedValueContainerFactory calculatedValueContainerFactory;
        private final DefaultLocalConfigurationMetadataBuilder.DependencyCache cache;

        public ConfigurationsProviderMetadataFactory(
            ConfigurationsProvider configurationsProvider,
            LocalConfigurationMetadataBuilder metadataBuilder,
            ModelContainer<?> model,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            this.configurationsProvider = configurationsProvider;
            this.metadataBuilder = metadataBuilder;
            this.model = model;
            this.calculatedValueContainerFactory = calculatedValueContainerFactory;
            this.cache = new LocalConfigurationMetadataBuilder.DependencyCache();
        }

        @Override
        public Set<String> getConfigurationNames() {
            ImmutableSet.Builder<String> builder = ImmutableSet.builder();
            configurationsProvider.visitAll(configuration -> builder.add(configuration.getName()));
            return builder.build();
        }

        @Override
        public void visitConfigurations(Consumer<Candidate> visitor) {
            configurationsProvider.visitAll(configuration -> {
                visitor.accept(new Candidate() {
                    @Override
                    public String getName() {
                        return configuration.getName();
                    }

                    @Override
                    public boolean isConsumable() {
                        return configuration.isCanBeConsumed();
                    }

                    @Override
                    public boolean hasAttributes() {
                        return !configuration.getAttributes().isEmpty();
                    }
                });
            });
        }

        @Override
        public void invalidate() {
            cache.invalidate();
        }

        @Override
        public LocalConfigurationMetadata getConfiguration(
            String name,
            DefaultLocalComponentMetadata parent
        ) {
            ConfigurationInternal configuration = configurationsProvider.findByName(name);
            if (configuration == null) {
                return null;
            }

            return metadataBuilder.create(configuration, configurationsProvider, parent, cache, model, calculatedValueContainerFactory);
        }
    }

}
