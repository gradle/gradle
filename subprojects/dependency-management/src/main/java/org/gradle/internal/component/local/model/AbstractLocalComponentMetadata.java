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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Actions;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.model.ComponentConfigurationIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ImmutableModuleSources;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public abstract class AbstractLocalComponentMetadata<T extends DefaultLocalComponentMetadata.DefaultLocalConfigurationMetadata> implements  LocalComponentMetadata {

    private final ModuleVersionIdentifier moduleVersionId;
    private final ComponentIdentifier componentId;
    private final String status;
    private final AttributesSchemaInternal attributesSchema;
    private final ModelContainer<?> model;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ModuleSources moduleSources = ImmutableModuleSources.of();
    private final LocalConfigurationMetadataBuilder configurationMetadataBuilder;

    private Optional<List<? extends VariantGraphResolveMetadata>> variantsForGraphTraversal;

    // TODO: Should these be concurrent maps, maybe a ConcurrentSkipListMap?
    // Implementations of LocalComponentMetadata should be thread-safe, but we
    // also want to maintain performance and memory usage.
    Map<String, Boolean> consumable = new LinkedHashMap<>();
    Map<String, Lazy<? extends T>> allConfigurations = new LinkedHashMap<>();

    public AbstractLocalComponentMetadata(
        ModuleVersionIdentifier moduleVersionIdentifier,
        ComponentIdentifier componentIdentifier,
        String status,
        AttributesSchemaInternal attributesSchema,
        ModelContainer<?> model,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        LocalConfigurationMetadataBuilder configurationMetadataBuilder
    ) {
        this.moduleVersionId = moduleVersionIdentifier;
        this.componentId = componentIdentifier;
        this.status = status;
        this.attributesSchema = attributesSchema;
        this.model = model;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.configurationMetadataBuilder = configurationMetadataBuilder;
    }

    @Override
    public Set<String> getConfigurationNames() {
        return allConfigurations.keySet();
    }

    public void registerConfiguration(ConfigurationInternal configuration, Action<T> configurator) {
        configuration.preventFromFurtherMutation();

        consumable.put(configuration.getName(), configuration.isCanBeConsumed());
        allConfigurations.put(configuration.getName(), Lazy.locking().of(() -> {
            T config = doCreateConfiguration(configuration, configurationMetadataBuilder);
            configurator.execute(config);
            return config;
        }));
    }

    /**
     * Eagerly adds a configuration to this component metadata. This method should be avoided in favor
     * of {@link #registerConfiguration(ConfigurationInternal, Action)}.
     *
     * @return The newly added configuration metadata.
     */
    public BuildableLocalConfigurationMetadata addConfiguration(String name, @Nullable String description, Set<String> extendsFrom, ImmutableSet<String> hierarchy, boolean visible, boolean transitive, ImmutableAttributes attributes, boolean canBeConsumed, @Nullable DeprecationMessageBuilder.WithDocumentation consumptionDeprecation, boolean canBeResolved, ImmutableCapabilities capabilities, Supplier<List<DependencyConstraint>> consistentResolutionConstraints) {
        T conf = createConfiguration(
            name,
            description,
            visible,
            transitive,
            extendsFrom,
            hierarchy,
            attributes,
            canBeConsumed,
            consumptionDeprecation,
            canBeResolved,
            capabilities,
            model,
            calculatedValueContainerFactory,
            consistentResolutionConstraints
        );
        consumable.put(name, canBeConsumed);
        allConfigurations.put(name, Lazy.locking().of(() -> conf));
        return conf;
    }


    @Override
    public T getConfiguration(final String name) {
        Lazy<? extends T> value = allConfigurations.get(name);
        if (value == null) {
            return null;
        }
        return value.get();
    }

    abstract T createConfiguration(
        String name,
        String description,
        boolean visible,
        boolean transitive,
        Set<String> extendsFrom,
        ImmutableSet<String> hierarchy,
        ImmutableAttributes attributes,
        boolean canBeConsumed,
        DeprecationMessageBuilder.WithDocumentation consumptionDeprecation,
        boolean canBeResolved,
        ImmutableCapabilities capabilities,
        ModelContainer<?> model,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        Supplier<List<DependencyConstraint>> consistentResolutionConstraints
    );

    private T doCreateConfiguration(
        ConfigurationInternal configuration,
        LocalConfigurationMetadataBuilder configurationMetadataBuilder
    ) {
        ImmutableSet<String> hierarchy = Configurations.getNames(configuration.getHierarchy());
        ImmutableSet<String> extendsFrom = Configurations.getNames(configuration.getExtendsFrom());
        // Presence of capabilities is bound to the definition of a capabilities extension to the project
        ImmutableCapabilities capabilities = ImmutableCapabilities.copyAsImmutable(
            Configurations.collectCapabilities(configuration, Sets.newHashSet(), Sets.newHashSet()));

        assert hierarchy.contains(configuration.getName());

        T configurationMetadata = createConfiguration(
            configuration.getName(),
            configuration.getDescription(),
            configuration.isVisible(),
            configuration.isTransitive(),
            extendsFrom,
            hierarchy,
            configuration.getAttributes().asImmutable(),
            configuration.isCanBeConsumed(),
            configuration.getConsumptionDeprecation(),
            configuration.isCanBeResolved(),
            capabilities,
            model,
            calculatedValueContainerFactory,
            configuration.getConsistentResolutionConstraints()
        );

        configurationMetadata.configurationMetadataBuilder = configurationMetadataBuilder;
        configurationMetadata.backingConfiguration = configuration;
        ComponentConfigurationIdentifier configurationIdentifier = new ComponentConfigurationIdentifier(getId(), configuration.getName());

        configuration.collectVariants(new ConfigurationInternal.VariantVisitor() {
            @Override
            public void visitArtifacts(Collection<? extends PublishArtifact> artifacts) {
                configurationMetadata.addArtifacts(artifacts);
            }

            @Override
            public void visitOwnVariant(DisplayName displayName, ImmutableAttributes attributes, Collection<? extends Capability> capabilities, Collection<? extends PublishArtifact> artifacts) {
                configurationMetadata.addVariant(configuration.getName(), configurationIdentifier, displayName, attributes, ImmutableCapabilities.of(capabilities), artifacts);
            }

            @Override
            public void visitChildVariant(String name, DisplayName displayName, ImmutableAttributes attributes, Collection<? extends Capability> capabilities, Collection<? extends PublishArtifact> artifacts) {
                configurationMetadata.addVariant(configuration.getName() + "-" + name, new NestedVariantIdentifier(configurationIdentifier, name), displayName, attributes, ImmutableCapabilities.of(capabilities), artifacts);
            }
        });

        return configurationMetadata;
    }

    /**
     * We currently allow a configuration that has been partially observed for resolution to be modified
     * in a beforeResolve callback.
     *
     * To reduce the number of instances of root component metadata we create, we mark all configurations
     * as dirty and in need of re-evaluation when we see certain types of modifications to a configuration.
     *
     * In the future, we could narrow the number of configurations that need to be re-evaluated, but it would
     * be better to get rid of the behavior that allows configurations to be modified once they've been observed.
     *
     * @see org.gradle.api.internal.artifacts.ivyservice.moduleconverter.DefaultRootComponentMetadataBuilder.MetadataHolder#tryCached(ComponentIdentifier)
     */
    @Override
    public void reevaluate(ConfigurationsProvider configurations) {
        // Un-realize all configurations.
        for (String name : ImmutableList.copyOf(allConfigurations.keySet())) {
            registerConfiguration(configurations.findByName(name), Actions.doNothing());
        }
    }

    /**
     * Creates a copy of this metadata, transforming the artifacts of this component.
     */
    @Override
    public DefaultLocalComponentMetadata copy(ComponentIdentifier componentIdentifier, Transformer<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> artifacts) {
        DefaultLocalComponentMetadata copy = new DefaultLocalComponentMetadata(moduleVersionId, componentIdentifier, status, attributesSchema, model, calculatedValueContainerFactory, configurationMetadataBuilder);

        // Keep track of transformed artifacts as a given artifact may appear in multiple variants and configurations
        Map<LocalComponentArtifactMetadata, LocalComponentArtifactMetadata> transformedArtifacts = new HashMap<>();

        // TODO: This is extremely costly. This breaks all laziness and realizes each configuration metadata.
        // Is such a strict copy really that necessary here?
        allConfigurations.forEach((key, value) -> {
            DefaultLocalComponentMetadata.DefaultLocalConfigurationMetadata confCopy = value.get().copy(artifacts, transformedArtifacts);
            copy.consumable.put(key, confCopy.isCanBeConsumed());
            copy.allConfigurations.put(key, Lazy.locking().of(() -> confCopy));
        });

        return copy;
    }

    @Override
    public ComponentIdentifier getId() {
        return componentId;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersionId() {
        return moduleVersionId;
    }

    @Override
    public String toString() {
        return componentId.getDisplayName();
    }

    @Override
    public ModuleSources getSources() {
        return moduleSources;
    }

    @Override
    public ComponentResolveMetadata withSources(ModuleSources source) {
        throw new UnsupportedOperationException();
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

    /**
     * For a local project component, the `variantsForGraphTraversal` are any _consumable_ configurations that have attributes defined.
     */
    @Override
    public synchronized Optional<List<? extends VariantGraphResolveMetadata>> getVariantsForGraphTraversal() {
        if (variantsForGraphTraversal == null) {
            ImmutableList.Builder<VariantGraphResolveMetadata> builder = new ImmutableList.Builder<>();
            for (Map.Entry<String, Lazy<? extends T>> entry : allConfigurations.entrySet()) {
                if (consumable.get(entry.getKey())) {
                    T actual = entry.getValue().get();
                    if (!actual.getAttributes().isEmpty()) {
                        builder.add(actual);
                    }
                }
            }

            ImmutableList<VariantGraphResolveMetadata> variants = builder.build();
            variantsForGraphTraversal = !variants.isEmpty() ? Optional.of(variants) : Optional.absent();
        }
        return variantsForGraphTraversal;
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

    private static class NestedVariantIdentifier implements VariantResolveMetadata.Identifier {
        private final VariantResolveMetadata.Identifier parent;
        private final String name;

        public NestedVariantIdentifier(VariantResolveMetadata.Identifier parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        @Override
        public int hashCode() {
            return parent.hashCode() ^ name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            NestedVariantIdentifier other = (NestedVariantIdentifier) obj;
            return parent.equals(other.parent) && name.equals(other.name);
        }
    }
}
