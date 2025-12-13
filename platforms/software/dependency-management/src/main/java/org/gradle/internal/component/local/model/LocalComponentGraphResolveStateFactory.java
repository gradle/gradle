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
import org.gradle.api.internal.artifacts.DefaultRootComponentIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.configurations.VariantIdentityUniquenessVerifier;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalVariantGraphResolveStateBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalVariantGraphResolveStateBuilder;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@ServiceScope(Scope.BuildTree.class)
public class LocalComponentGraphResolveStateFactory {

    private final AttributeDesugaring attributeDesugaring;
    private final ComponentIdGenerator idGenerator;
    private final LocalVariantGraphResolveStateBuilder metadataBuilder;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public LocalComponentGraphResolveStateFactory(
        AttributeDesugaring attributeDesugaring,
        ComponentIdGenerator idGenerator,
        LocalVariantGraphResolveStateBuilder metadataBuilder,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.attributeDesugaring = attributeDesugaring;
        this.idGenerator = idGenerator;
        this.metadataBuilder = metadataBuilder;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    /**
     * Creates state for a component loaded from the configuration cache.
     */
    public LocalComponentGraphResolveState realizedStateFor(
        LocalComponentGraphResolveMetadata metadata,
        List<? extends LocalVariantGraphResolveState> variants
    ) {
        LocalVariantGraphResolveStateFactory configurationFactory = new RealizedListVariantFactory(variants);
        return createLocalComponentState(false, idGenerator.nextComponentId(), metadata, configurationFactory);
    }

    /**
     * Creates state for a variant loaded from the configuration cache.
     */
    public LocalVariantGraphResolveState realizedVariantStateFor(
        LocalVariantGraphResolveMetadata metadata,
        DefaultLocalVariantGraphResolveState.VariantDependencyMetadata dependencyMetadata,
        Set<LocalVariantMetadata> variants
    ) {
        CalculatedValue<DefaultLocalVariantGraphResolveState.VariantDependencyMetadata> calculatedDependencies =
            calculatedValueContainerFactory.create(Describables.of(metadata, "dependencies"), context -> dependencyMetadata);

        return new DefaultLocalVariantGraphResolveState(
            idGenerator.nextVariantId(),
            metadata,
            calculatedDependencies,
            variants
        );
    }

    /**
     * Creates state for a standard local component, with variants derived from the given {@link ConfigurationsProvider}.
     */
    public LocalComponentGraphResolveState stateFor(
        ModelContainer<?> model,
        LocalComponentGraphResolveMetadata metadata,
        ConfigurationsProvider configurations
    ) {
        LocalVariantGraphResolveStateFactory variantsFactory = new ConfigurationsProviderVariantFactory(
            metadata.getId(),
            configurations,
            metadataBuilder,
            model,
            calculatedValueContainerFactory
        );

        return createLocalComponentState(false, idGenerator.nextComponentId(), metadata, variantsFactory);
    }

    /**
     * Creates state for an adhoc root component with no variants.
     */
    public LocalComponentGraphResolveState adhocRootComponentState(
        String status,
        ModuleVersionIdentifier moduleVersionId,
        ImmutableAttributesSchema attributesSchema
    ) {
        long instanceId = idGenerator.nextComponentId();
        ComponentIdentifier componentIdentifier = new DefaultRootComponentIdentifier(instanceId);

        LocalComponentGraphResolveMetadata metadata = new LocalComponentGraphResolveMetadata(
            moduleVersionId,
            componentIdentifier,
            status,
            attributesSchema
        );

        LocalVariantGraphResolveStateFactory configurationFactory = new RealizedListVariantFactory(Collections.emptyList());
        return createLocalComponentState(true, instanceId, metadata, configurationFactory);
    }

    private DefaultLocalComponentGraphResolveState createLocalComponentState(
        boolean adHoc,
        long instanceId,
        LocalComponentGraphResolveMetadata metadata,
        LocalVariantGraphResolveStateFactory variantsFactory
    ) {
        return new DefaultLocalComponentGraphResolveState(
            instanceId,
            metadata,
            attributeDesugaring,
            adHoc,
            variantsFactory,
            calculatedValueContainerFactory
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
        }

        @Override
        @SuppressWarnings("NonCanonicalType") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        public void visitConsumableVariants(Consumer<LocalVariantGraphResolveState> visitor) {
            model.applyToMutableState(p -> {
                DefaultLocalVariantGraphResolveStateBuilder.DependencyCache cache =
                    new DefaultLocalVariantGraphResolveStateBuilder.DependencyCache();

                VariantIdentityUniquenessVerifier.buildReport(configurationsProvider).assertNoConflicts();

                configurationsProvider.visitConsumable(configuration -> {
                    LocalVariantGraphResolveState variantState = stateBuilder.createConsumableVariantState(
                        configuration,
                        componentId,
                        cache,
                        model,
                        calculatedValueContainerFactory
                    );

                    visitor.accept(variantState);
                });
            });
        }

    }

}
