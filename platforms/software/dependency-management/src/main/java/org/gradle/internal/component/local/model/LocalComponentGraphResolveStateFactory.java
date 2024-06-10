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
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultLocalConfigurationMetadataBuilder;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@ServiceScope(Scope.BuildTree.class)
public class LocalComponentGraphResolveStateFactory {
    private final AttributeDesugaring attributeDesugaring;
    private final ComponentIdGenerator idGenerator;
    private final LocalConfigurationMetadataBuilder metadataBuilder;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public LocalComponentGraphResolveStateFactory(
        AttributeDesugaring attributeDesugaring,
        ComponentIdGenerator idGenerator,
        LocalConfigurationMetadataBuilder metadataBuilder,
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
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        List<? extends LocalConfigurationGraphResolveMetadata> configurations
    ) {
        ConfigurationMetadataFactory configurationFactory = new ConfigurationsListMetadataFactory(configurations);
        return createLocalComponentState(componentIdentifier, moduleVersionId, status, schema, false, configurationFactory);
    }

    /**
     * Creates state for a standard local component. Standard components are by definition non-adhoc.
     *
     * @see #adHocStateFor
     */
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

    private LocalComponentGraphResolveState lazyStateFor(
        ModelContainer<?> model,
        ComponentIdentifier componentIdentifier,
        ConfigurationsProvider configurations,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        boolean adHoc
    ) {
        ConfigurationMetadataFactory configurationFactory = new ConfigurationsProviderMetadataFactory(
            componentIdentifier,
            configurations,
            metadataBuilder,
            model,
            calculatedValueContainerFactory
        );

        return createLocalComponentState(componentIdentifier, moduleVersionId, status, schema, adHoc, configurationFactory);
    }

    private DefaultLocalComponentGraphResolveState createLocalComponentState(
        ComponentIdentifier componentIdentifier,
        ModuleVersionIdentifier moduleVersionId,
        String status,
        AttributesSchemaInternal schema,
        boolean adHoc,
        ConfigurationMetadataFactory configurationFactory
    ) {
        LocalComponentGraphResolveMetadata metadata = new LocalComponentGraphResolveMetadata(
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
            configurationFactory,
            calculatedValueContainerFactory,
            null
        );
    }

    /**
     * A {@link ConfigurationMetadataFactory} which uses a list of pre-constructed configuration
     * metadata as its data source.
     */
    private static class ConfigurationsListMetadataFactory implements ConfigurationMetadataFactory {
        private final List<? extends LocalConfigurationGraphResolveMetadata> metadata;

        public ConfigurationsListMetadataFactory(List<? extends LocalConfigurationGraphResolveMetadata> metadata) {
            this.metadata = metadata;
        }

        @Override
        public void visitConsumableConfigurations(Consumer<LocalConfigurationGraphResolveMetadata> visitor) {
            for (LocalConfigurationGraphResolveMetadata configuration : metadata) {
                visitor.accept(configuration);
            }
        }

        @Override
        public void invalidate() {}

        @Override
        public LocalConfigurationGraphResolveMetadata getConfiguration(String name) {
            return metadata.stream()
                .filter(configuration -> name.equals(configuration.getName()))
                .findFirst()
                .orElse(null);
        }

        @Override
        public Set<String> getConfigurationNames() {
            return metadata.stream()
                .map(LocalConfigurationGraphResolveMetadata::getName)
                .collect(Collectors.toSet());
        }
    }

    /**
     * A {@link ConfigurationMetadataFactory} which uses a {@link ConfigurationsProvider} as its data source.
     *
     * TODO: This class should acquire a project lock before accessing the configurations provider.
     */
    private static class ConfigurationsProviderMetadataFactory implements ConfigurationMetadataFactory {

        private final ComponentIdentifier componentId;
        private final ConfigurationsProvider configurationsProvider;
        private final LocalConfigurationMetadataBuilder metadataBuilder;
        private final ModelContainer<?> model;
        private final CalculatedValueContainerFactory calculatedValueContainerFactory;
        private final DefaultLocalConfigurationMetadataBuilder.DependencyCache cache;

        public ConfigurationsProviderMetadataFactory(
            ComponentIdentifier componentId,
            ConfigurationsProvider configurationsProvider,
            LocalConfigurationMetadataBuilder metadataBuilder,
            ModelContainer<?> model,
            CalculatedValueContainerFactory calculatedValueContainerFactory
        ) {
            this.componentId = componentId;
            this.configurationsProvider = configurationsProvider;
            this.metadataBuilder = metadataBuilder;
            this.model = model;
            this.calculatedValueContainerFactory = calculatedValueContainerFactory;
            this.cache = new LocalConfigurationMetadataBuilder.DependencyCache();
        }

        @Override
        public void visitConsumableConfigurations(Consumer<LocalConfigurationGraphResolveMetadata> visitor) {
            model.applyToMutableState(p -> {
                VariantIdentityUniquenessVerifier.buildReport(configurationsProvider).assertNoConflicts();

                configurationsProvider.visitAll(configuration -> {
                    if (configuration.isCanBeConsumed()) {
                        visitor.accept(createConfigurationMetadata(configuration));
                    }
                });
            });
        }

        @Override
        public void invalidate() {
            cache.invalidate();
        }

        @Nullable
        @Override
        public LocalConfigurationGraphResolveMetadata getConfiguration(String name) {
            return model.fromMutableState(p -> {
                ConfigurationInternal configuration = configurationsProvider.findByName(name);
                if (configuration == null) {
                    return null;
                }

                return createConfigurationMetadata(configuration);
            });
        }

        @Override
        public Set<String> getConfigurationNames() {
            Set<String> names = new HashSet<>();
            model.applyToMutableState(p ->
                configurationsProvider.visitAll(configuration -> names.add(configuration.getName()))
            );
            return names;
        }

        private LocalConfigurationGraphResolveMetadata createConfigurationMetadata(ConfigurationInternal configuration) {
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
