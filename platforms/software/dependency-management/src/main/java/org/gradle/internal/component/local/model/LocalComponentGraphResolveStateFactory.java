/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.VariantIdentityUniquenessVerifier;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantMetadataBuilder;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.local.model.DefaultLocalComponentGraphResolveState.VariantMetadataFactory;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Consumer;

@ServiceScope(Scope.BuildTree.class)
public class LocalComponentGraphResolveStateFactory {
    private final AttributeDesugaring attributeDesugaring;
    private final ComponentIdGenerator idGenerator;
    private final LocalVariantMetadataBuilder metadataBuilder;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    @Inject
    public LocalComponentGraphResolveStateFactory(
        AttributeDesugaring attributeDesugaring,
        ComponentIdGenerator idGenerator,
        LocalVariantMetadataBuilder metadataBuilder,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.attributeDesugaring = attributeDesugaring;
        this.idGenerator = idGenerator;
        this.metadataBuilder = metadataBuilder;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    public LocalComponentGraphResolveState stateFor(
        ModelContainer<?> model,
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId,
        ConfigurationsProvider configurations,
        String status,
        AttributesSchemaInternal schema
    ) {
        return lazyStateFor(model, componentIdentifier, configurations, moduleVersionId, status, schema, false);
    }

    /**
     * Creates state for an ad hoc component, see {@link org.gradle.internal.component.model.ComponentGraphResolveState#isAdHoc()} for the definition of "ad hoc component".
     */
    public LocalComponentGraphResolveState adHocStateFor(
        ModelContainer<?> model,
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId,
        ConfigurationsProvider configurations,
        String status,
        AttributesSchemaInternal schema
    ) {
        return lazyStateFor(model, componentIdentifier, configurations, moduleVersionId, status, schema, true);
    }

    /**
     * Creates state for a component loaded from the configuration cache.
     */
    public LocalComponentGraphResolveState realizedStateFor(
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        List<? extends LocalVariantGraphResolveMetadata> variants
    ) {
        VariantMetadataFactory variantsFactory = new VariantsMapMetadataFactory(variants);
        return createLocalComponentState(componentIdentifier, moduleVersionId, status, schema, false, variantsFactory);
    }

    private LocalComponentGraphResolveState lazyStateFor(
        ModelContainer<?> model,
        ComponentIdentifier componentIdentifier,
        ConfigurationsProvider configurations,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        boolean adHoc
    ) {
        VariantMetadataFactory variantsFactory = new ConfigurationsProviderMetadataFactory(
            componentIdentifier,
            configurations,
            metadataBuilder,
            model,
            calculatedValueContainerFactory
        );

        return createLocalComponentState(componentIdentifier, moduleVersionId, status, schema, adHoc, variantsFactory);
    }

    private DefaultLocalComponentGraphResolveState createLocalComponentState(
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        boolean adHoc,
        VariantMetadataFactory configurationMetadataFactory
    ) {
        ComponentGraphResolveMetadata metadata = new LocalComponentGraphResolveMetadata(
            moduleVersionId,
            componentIdentifier,
            status,
            schema
        );

        return new DefaultLocalComponentGraphResolveState(
            idGenerator.nextComponentId(),
            metadata,
            attributeDesugaring,
            idGenerator,
            adHoc,
            configurationMetadataFactory,
            null
        );
    }

    /**
     * A {@link VariantMetadataFactory} which uses a list of pre-constructed variant
     * metadata as its data source.
     */
    public static class VariantsMapMetadataFactory implements VariantMetadataFactory {
        private final List<? extends LocalVariantGraphResolveMetadata> variants;

        public VariantsMapMetadataFactory(List<? extends LocalVariantGraphResolveMetadata> variants) {
            this.variants = variants;
        }

        @Override
        public void visitConsumableVariants(Consumer<LocalVariantGraphResolveMetadata> visitor) {
            for (LocalVariantGraphResolveMetadata variant : variants) {
                visitor.accept(variant);
            }
        }

        @Override
        public void invalidate() {}

        @Override
        public LocalVariantGraphResolveMetadata getRootVariant(String name) {
            return variants.stream()
                .filter(variant -> name.equals(variant.getConfigurationName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Root variant not found: " + name));
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
        public void visitConsumableVariants(Consumer<LocalVariantGraphResolveMetadata> visitor) {
            VariantIdentityUniquenessVerifier.buildReport(configurationsProvider).assertNoConflicts();

            configurationsProvider.visitAll(configuration -> {
                if (configuration.isCanBeConsumed()) {
                    visitor.accept(createVariantMetadata(configuration));
                }
            });
        }

        @Override
        public void invalidate() {
            cache.invalidate();
        }

        @Override
        public LocalVariantGraphResolveMetadata getRootVariant(String name) {
            ConfigurationInternal configuration = configurationsProvider.findByName(name);
            if (configuration == null || !configuration.isCanBeResolved()) {
                throw new IllegalArgumentException(String.format("Expected root variant '%s' to be present in %s", name, componentId));
            }
            return createVariantMetadata(configuration);
        }

        private LocalVariantGraphResolveMetadata createVariantMetadata(ConfigurationInternal configuration) {
            return metadataBuilder.create(
                configuration,
                configurationsProvider,
                componentId,
                cache,
                model,
                calculatedValueContainerFactory
            );
        }
    }
}
