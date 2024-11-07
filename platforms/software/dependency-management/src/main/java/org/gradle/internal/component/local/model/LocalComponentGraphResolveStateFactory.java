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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.VariantIdentityUniquenessVerifier;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantGraphResolveStateBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantGraphResolveStateBuilder;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@ServiceScope(Scope.BuildTree.class)
public class LocalComponentGraphResolveStateFactory {
    private final AttributeDesugaring attributeDesugaring;
    private final ComponentIdGenerator idGenerator;
    private final LocalVariantGraphResolveStateBuilder metadataBuilder;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final InMemoryCacheFactory cacheFactory;

    public LocalComponentGraphResolveStateFactory(
        AttributeDesugaring attributeDesugaring,
        ComponentIdGenerator idGenerator,
        LocalVariantGraphResolveStateBuilder metadataBuilder,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        InMemoryCacheFactory cacheFactory
    ) {
        this.attributeDesugaring = attributeDesugaring;
        this.idGenerator = idGenerator;
        this.metadataBuilder = metadataBuilder;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.cacheFactory = cacheFactory;
    }

    /**
     * Creates state for a component loaded from the configuration cache.
     */
    public LocalComponentGraphResolveState realizedStateFor(
        LocalComponentGraphResolveMetadata metadata,
        List<? extends LocalVariantGraphResolveState> variants
    ) {
        LocalVariantGraphResolveStateFactory configurationFactory = new RealizedListVariantFactory(variants);
        return createLocalComponentState(false, metadata, configurationFactory);
    }

    /**
     * Creates state for a variant loaded from the configuration cache.
     */
    public LocalVariantGraphResolveState realizedVariantStateFor(
        ComponentIdentifier componentId,
        LocalVariantGraphResolveMetadata metadata,
        DefaultLocalVariantGraphResolveState.VariantDependencyMetadata dependencyMetadata,
        Set<LocalVariantMetadata> variants
    ) {
        CalculatedValue<DefaultLocalVariantGraphResolveState.VariantDependencyMetadata> calculatedDependencies =
            calculatedValueContainerFactory.create(Describables.of("dependencies for", metadata), context -> dependencyMetadata);

        return new DefaultLocalVariantGraphResolveState(
            idGenerator.nextVariantId(),
            componentId,
            metadata,
            idGenerator,
            calculatedValueContainerFactory,
            calculatedDependencies,
            variants
        );
    }

    /**
     * Creates state for a standard local component. Standard components are by definition non-adhoc.
     *
     * @see #adHocStateFor
     */
    public LocalComponentGraphResolveState stateFor(
        ModelContainer<?> model,
        LocalComponentGraphResolveMetadata metadata,
        ConfigurationsProvider configurations
    ) {
        return lazyStateFor(model, metadata, configurations, false);
    }

    /**
     * Creates state for an ad hoc component, see {@link org.gradle.internal.component.model.ComponentGraphResolveState#isAdHoc()} for the definition of "ad hoc component".
     */
    public LocalComponentGraphResolveState adHocStateFor(
        ModelContainer<?> model,
        LocalComponentGraphResolveMetadata metadata,
        ConfigurationsProvider configurations
    ) {
        return lazyStateFor(model, metadata, configurations, true);
    }

    private LocalComponentGraphResolveState lazyStateFor(
        ModelContainer<?> model,
        LocalComponentGraphResolveMetadata metadata,
        ConfigurationsProvider configurations,
        boolean adHoc
    ) {
        LocalVariantGraphResolveStateFactory variantsFactory = new ConfigurationsProviderVariantFactory(
            metadata.getId(),
            configurations,
            metadataBuilder,
            model,
            calculatedValueContainerFactory
        );

        return createLocalComponentState(adHoc, metadata, variantsFactory);
    }

    private DefaultLocalComponentGraphResolveState createLocalComponentState(
        boolean adHoc,
        LocalComponentGraphResolveMetadata metadata,
        LocalVariantGraphResolveStateFactory variantsFactory
    ) {
        return new DefaultLocalComponentGraphResolveState(
            idGenerator.nextComponentId(),
            metadata,
            attributeDesugaring,
            idGenerator,
            adHoc,
            variantsFactory,
            calculatedValueContainerFactory,
            cacheFactory,
            null
        );
    }

    /**
     * A {@link LocalVariantGraphResolveStateFactory} which uses a list of pre-constructed variant
     * states as its data source.
     */
    private static class RealizedListVariantFactory implements LocalVariantGraphResolveStateFactory {
        private final List<? extends LocalVariantGraphResolveState> variants;

        public RealizedListVariantFactory(List<? extends LocalVariantGraphResolveState> variants) {
            this.variants = variants;
        }

        @Override
        public void visitConsumableVariants(Consumer<LocalVariantGraphResolveState> visitor) {
            for (LocalVariantGraphResolveState variant : variants) {
                visitor.accept(variant);
            }
        }

        @Override
        public void invalidate() {}

        @Override
        public LocalVariantGraphResolveState getVariantByConfigurationName(String name) {
            return variants.stream()
                .filter(variant -> name.equals(variant.getMetadata().getConfigurationName()))
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * A {@link LocalVariantGraphResolveStateFactory} which uses a {@link ConfigurationsProvider} as its data source.
     */
    private static class ConfigurationsProviderVariantFactory implements LocalVariantGraphResolveStateFactory {

        private final ComponentIdentifier componentId;
        private final ConfigurationsProvider configurationsProvider;
        private final LocalVariantGraphResolveStateBuilder stateBuilder;
        private final ModelContainer<?> model;
        private final CalculatedValueContainerFactory calculatedValueContainerFactory;
        private final DefaultLocalVariantGraphResolveStateBuilder.DependencyCache cache;

        public ConfigurationsProviderVariantFactory(
            ComponentIdentifier componentId,
            ConfigurationsProvider configurationsProvider,
            LocalVariantGraphResolveStateBuilder stateBuilder,
            ModelContainer<?> model,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            this.componentId = componentId;
            this.configurationsProvider = configurationsProvider;
            this.stateBuilder = stateBuilder;
            this.model = model;
            this.calculatedValueContainerFactory = calculatedValueContainerFactory;
            this.cache = new DefaultLocalVariantGraphResolveStateBuilder.DependencyCache();
        }

        @Override
        public void visitConsumableVariants(Consumer<LocalVariantGraphResolveState> visitor) {
            model.applyToMutableState(p -> {
                VariantIdentityUniquenessVerifier.buildReport(configurationsProvider).assertNoConflicts();
                configurationsProvider.visitConsumable(configuration -> {
                    visitor.accept(createVariantState(configuration));
                });
            });
        }

        @Override
        public void invalidate() {
            cache.invalidate();
        }

        @Nullable
        @Override
        public LocalVariantGraphResolveState getVariantByConfigurationName(String name) {
            return model.fromMutableState(p -> {
                ConfigurationInternal configuration = configurationsProvider.findByName(name);
                if (configuration == null) {
                    return null;
                }

                return createVariantState(configuration);
            });
        }

        private LocalVariantGraphResolveState createVariantState(ConfigurationInternal configuration) {
            return stateBuilder.create(
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
