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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.VariantIdentityUniquenessVerifier;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Default implementation of {@link LocalComponentGraphResolveMetadata}. This component is lazy in that it
 * will only initialize {@link LocalVariantGraphResolveMetadata} instances on-demand as they are needed.
 * <p>
 * TODO: Eventually, this class should be updated to only create metadata instances for consumable variants.
 * However, we currently need to expose resolvable variants since the
 * {@link org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState} implementation
 * sources its root component metadata, for a resolvable configuration, from this component metadata.
 */
@SuppressWarnings("JavadocReference")
public final class DefaultLocalComponentGraphResolveMetadata implements LocalComponentGraphResolveMetadata {

    private final ComponentIdentifier componentId;
    private final ModuleVersionIdentifier moduleVersionId;
    private final String status;
    private final AttributesSchemaInternal attributesSchema;

    // TODO: All this lazy state should be moved to DefaultLocalComponentGraphResolveState
    private final VariantMetadataFactory variantFactory;
    private final Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer;
    private final Map<String, LocalVariantGraphResolveMetadata> rootVariants = new LinkedHashMap<>();
    private List<? extends LocalVariantGraphResolveMetadata> consumableVariants;

    public DefaultLocalComponentGraphResolveMetadata(
        ModuleVersionIdentifier moduleVersionId,
        ComponentIdentifier componentId,
        String status,
        AttributesSchemaInternal attributesSchema,
        VariantMetadataFactory variantFactory,
        @Nullable Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifactTransformer
    ) {
        this.moduleVersionId = moduleVersionId;
        this.componentId = componentId;
        this.status = status;
        this.attributesSchema = attributesSchema;
        this.variantFactory = variantFactory;
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
    public DefaultLocalComponentGraphResolveMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformer) {
        // Keep track of transformed artifacts as a given artifact may appear in multiple variants and configurations
        Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts = new HashMap<>();
        Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> cachedTransformer = oldArtifact ->
            transformedArtifacts.computeIfAbsent(oldArtifact, transformer::transform);

        return new DefaultLocalComponentGraphResolveMetadata(moduleVersionId, componentIdentifier, status, attributesSchema, variantFactory, cachedTransformer);
    }

    @Override
    public String toString() {
        return componentId.getDisplayName();
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
    public ImmutableList<? extends VirtualComponentIdentifier> getPlatformOwners() {
        return ImmutableList.of();
    }

    /**
     * For a local project component, the `variantsForGraphTraversal` are any _consumable_ variants that have attributes defined.
     */
    @Override
    public synchronized List<? extends LocalVariantGraphResolveMetadata> getVariantsForGraphTraversal() {
        if (consumableVariants == null) {
            ImmutableList.Builder<LocalVariantGraphResolveMetadata> builder = new ImmutableList.Builder<>();
            variantFactory.visitConsumableVariants(candidate -> builder.add(createVariantMetadata(candidate)));
            consumableVariants = builder.build();
        }
        return consumableVariants;
    }

    @Override
    public LocalVariantGraphResolveMetadata getRootVariant(String name) {
        return rootVariants.computeIfAbsent(name, n -> {
            LocalVariantGraphResolveMetadata md = createVariantMetadata(n);
            if (md == null || !md.isCanBeResolved()) {
                throw new IllegalArgumentException(String.format("Expected root variant '%s' to be present in %s", n, componentId));
            }
            return md;
        });
    }

    @Nullable
    private LocalVariantGraphResolveMetadata createVariantMetadata(String name) {
        LocalVariantGraphResolveMetadata md;
        md = variantFactory.getVariantByConfigurationName(name);
        if (md == null) {
            return null;
        }
        if (artifactTransformer != null) {
            md = md.copyWithTransformedArtifacts(artifactTransformer);
        }
        return md;
    }

    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return attributesSchema;
    }

    @Override
    public void reevaluate() {
        rootVariants.clear();
        variantFactory.invalidate();
        synchronized (this) {
            consumableVariants = null;
        }
    }

    @Override
    public boolean isConfigurationRealized(String configName) {
        return rootVariants.get(configName) != null;
    }

    /**
     * Constructs {@link LocalVariantGraphResolveMetadata} given a configuration's name. This allows
     * the component metadata to source variant data from multiple sources, both lazy and eager.
     */
    public interface VariantMetadataFactory {
        /**
         * Visit the names of all variants that can be selected in a dependency graph.
         */
        void visitConsumableVariants(Consumer<String> visitor);

        /**
         * Invalidates any caching used for producing variant metadata.
         */
        void invalidate();

        /**
         * Produces a variant metadata instance from the configuration with the given {@code name}.
         *
         * @return Null if the variant with the given configuration name does not exist.
         */
        @Nullable
        LocalVariantGraphResolveMetadata getVariantByConfigurationName(String name);
    }

    /**
     * A {@link VariantMetadataFactory} which uses a map of pre-constructed variant
     * metadata as its data source.
     */
    public static class VariantsMapMetadataFactory implements VariantMetadataFactory {
        private final Map<String, LocalVariantGraphResolveMetadata> metadata;

        public VariantsMapMetadataFactory(Map<String, LocalVariantGraphResolveMetadata> metadata) {
            this.metadata = metadata;
        }

        @Override
        public void visitConsumableVariants(Consumer<String> visitor) {
            for (LocalVariantGraphResolveMetadata variant : metadata.values()) {
                if (variant.isCanBeConsumed()) {
                    visitor.accept(variant.getName());
                }
            }
        }

        @Override
        public void invalidate() {}

        @Override
        public LocalVariantGraphResolveMetadata getVariantByConfigurationName(String name) {
            return metadata.get(name);
        }
    }

    /**
     * A {@link VariantMetadataFactory} which uses a {@link ConfigurationsProvider} as its data source.
     */
    public static class ConfigurationsProviderMetadataFactory implements VariantMetadataFactory {

        private final ComponentIdentifier componentId;
        private final ConfigurationsProvider configurationsProvider;
        private final LocalVariantMetadataBuilder metadataBuilder;
        private final ModelContainer<?> model;
        private final CalculatedValueContainerFactory calculatedValueContainerFactory;
        private final DefaultLocalVariantMetadataBuilder.DependencyCache cache;

        public ConfigurationsProviderMetadataFactory(
            ComponentIdentifier componentId,
            ConfigurationsProvider configurationsProvider,
            LocalVariantMetadataBuilder metadataBuilder,
            ModelContainer<?> model,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            this.componentId = componentId;
            this.configurationsProvider = configurationsProvider;
            this.metadataBuilder = metadataBuilder;
            this.model = model;
            this.calculatedValueContainerFactory = calculatedValueContainerFactory;
            this.cache = new LocalVariantMetadataBuilder.DependencyCache();
        }

        @Override
        public void visitConsumableVariants(Consumer<String> visitor) {
            VariantIdentityUniquenessVerifier.buildReport(configurationsProvider).assertNoConflicts();

            configurationsProvider.visitAll(configuration -> {
                if (configuration.isCanBeConsumed()) {
                    visitor.accept(configuration.getName());
                }
            });
        }

        @Override
        public void invalidate() {
            cache.invalidate();
        }

        @Override
        public LocalVariantGraphResolveMetadata getVariantByConfigurationName(String name) {
            ConfigurationInternal configuration = configurationsProvider.findByName(name);
            if (configuration == null) {
                return null;
            }

            return metadataBuilder.create(configuration, configurationsProvider, componentId, cache, model, calculatedValueContainerFactory);
        }
    }

}
