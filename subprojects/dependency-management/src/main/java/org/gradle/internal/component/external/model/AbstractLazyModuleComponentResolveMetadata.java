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

package org.gradle.internal.component.external.model;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSources;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Common base class for the lazy versions of {@link ModuleComponentResolveMetadata} implementations.
 *
 * The lazy part is about the application of {@link VariantMetadataRules} which are applied lazily
 * when configuration or variant data is required by consumers.
 *
 * This type hierarchy is used whenever the {@code ModuleComponentResolveMetadata} does not need to outlive
 * the build execution.
 */
public abstract class AbstractLazyModuleComponentResolveMetadata extends AbstractModuleComponentResolveMetadata {
    private final VariantMetadataRules variantMetadataRules;
    private final ImmutableMap<String, Configuration> configurationDefinitions;

    // Configurations are built on-demand, but only once.
    private final Map<String, ConfigurationMetadata> configurations = Maps.newHashMap();

    private Optional<ImmutableList<? extends ConfigurationMetadata>> graphVariants;

    protected AbstractLazyModuleComponentResolveMetadata(AbstractMutableModuleComponentResolveMetadata metadata) {
        super(metadata);
        this.configurationDefinitions = metadata.getConfigurationDefinitions();
        this.variantMetadataRules = metadata.getVariantMetadataRules();
    }

    /**
     * Creates a copy of the given metadata
     */
    protected AbstractLazyModuleComponentResolveMetadata(AbstractLazyModuleComponentResolveMetadata metadata, ModuleSources sources, VariantDerivationStrategy variantDerivationStrategy) {
        super(metadata, sources, variantDerivationStrategy);
        this.configurationDefinitions = metadata.configurationDefinitions;
        this.variantMetadataRules = metadata.variantMetadataRules;
    }

    /**
     * Clear any cached state, for the case where the inputs are invalidated.
     * This only happens when constructing a copy
     */
    protected void copyCachedState(AbstractLazyModuleComponentResolveMetadata metadata, boolean copyGraphVariants) {
        // Copy built-on-demand state
        metadata.copyCachedConfigurations(this.configurations);
        if (copyGraphVariants) {
            this.graphVariants = metadata.graphVariants;
        }
    }

    private synchronized void copyCachedConfigurations(Map<String, ConfigurationMetadata> target) {
        target.putAll(configurations);
    }

    @Override
    public VariantMetadataRules getVariantMetadataRules() {
        return variantMetadataRules;
    }

    public ImmutableMap<String, Configuration> getConfigurationDefinitions() {
        return configurationDefinitions;
    }

    private Optional<ImmutableList<? extends ConfigurationMetadata>> buildVariantsForGraphTraversal() {
        ImmutableList<? extends ComponentVariant> variants = getVariants();
        if (variants.isEmpty()) {
            return addVariantsByRule(maybeDeriveVariants());
        }
        ImmutableList.Builder<ConfigurationMetadata> configurations = new ImmutableList.Builder<>();
        for (ComponentVariant variant : variants) {
            configurations.add(new LazyVariantBackedConfigurationMetadata(getId(), variant, getAttributes(), getAttributesFactory(), variantMetadataRules));
        }
        return addVariantsByRule(Optional.of(configurations.build()));

    }

    private Optional<ImmutableList<? extends ConfigurationMetadata>> addVariantsByRule(Optional<ImmutableList<? extends ConfigurationMetadata>> variants) {
        if (variantMetadataRules.getAdditionalVariants().isEmpty()) {
            return variants;
        }
        Map<String, ConfigurationMetadata> variantsByName = variants.or(ImmutableList.of()).stream().collect(Collectors.toMap(ConfigurationMetadata::getName, Function.identity()));
        ImmutableList.Builder<ConfigurationMetadata> builder = new ImmutableList.Builder<>();
        if (variants.isPresent()) {
            builder.addAll(variants.get());
        }
        for (AdditionalVariant additionalVariant : variantMetadataRules.getAdditionalVariants()) {
            String baseName = additionalVariant.getBase();
            ConfigurationMetadata base = null;
            if (baseName != null) {
                if (variants.isPresent()) {
                    base = variantsByName.get(baseName);
                    if (!additionalVariant.isLenient() && !(base instanceof ModuleConfigurationMetadata)) {
                        throw new InvalidUserDataException("Variant '" + baseName + "' not defined in module " + getId().getDisplayName());
                    }
                } else {
                    base = getConfiguration(baseName);
                    if (!additionalVariant.isLenient() && !(base instanceof ModuleConfigurationMetadata)) {
                        throw new InvalidUserDataException("Configuration '" + baseName + "' not defined in module " + getId().getDisplayName());
                    }
                }
            }
            if (baseName == null || base instanceof ModuleConfigurationMetadata) {
                ConfigurationMetadata configurationMetadata = new LazyRuleAwareWithBaseConfigurationMetadata(additionalVariant.getName(), (ModuleConfigurationMetadata) base, getId(), getAttributes(), variantMetadataRules, constructVariantExcludes(base), false);
                builder.add(configurationMetadata);
            }
        }
        return Optional.of(builder.build());
    }

    @Override
    public synchronized Optional<ImmutableList<? extends ConfigurationMetadata>> getVariantsForGraphTraversal() {
        if (graphVariants == null) {
            graphVariants = buildVariantsForGraphTraversal();
        }
        return graphVariants;
    }

    @Override
    public Set<String> getConfigurationNames() {
        return configurationDefinitions.keySet();
    }

    @Override
    public synchronized ConfigurationMetadata getConfiguration(final String name) {
        ConfigurationMetadata populated = configurations.get(name);
        if (populated != null) {
            return populated;
        }
        ConfigurationMetadata md = populateConfigurationFromDescriptor(name, configurationDefinitions);
        configurations.put(name, md);
        return md;
    }

    protected ConfigurationMetadata populateConfigurationFromDescriptor(String name, Map<String, Configuration> configurationDefinitions) {
        Configuration descriptorConfiguration = configurationDefinitions.get(name);
        if (descriptorConfiguration == null) {
            return null;
        }

        ImmutableSet<String> hierarchy = constructHierarchy(descriptorConfiguration);
        boolean transitive = descriptorConfiguration.isTransitive();
        boolean visible = descriptorConfiguration.isVisible();
        return createConfiguration(getId(), name, transitive, visible, hierarchy, variantMetadataRules);
    }

    private ImmutableSet<String> constructHierarchy(Configuration descriptorConfiguration) {
        if (descriptorConfiguration.getExtendsFrom().isEmpty()) {
            return ImmutableSet.of(descriptorConfiguration.getName());
        }
        ImmutableSet.Builder<String> accumulator = new ImmutableSet.Builder<>();
        populateHierarchy(descriptorConfiguration, accumulator);
        return accumulator.build();
    }

    private void populateHierarchy(Configuration metadata, ImmutableSet.Builder<String> accumulator) {
        accumulator.add(metadata.getName());
        for (String parentName : metadata.getExtendsFrom()) {
            Configuration parent = configurationDefinitions.get(parentName);
            populateHierarchy(parent, accumulator);
        }
    }

    private ImmutableList<ExcludeMetadata> constructVariantExcludes(ConfigurationMetadata base) {
        if(base == null) {
            return ImmutableList.of();
        }
        return base.getExcludes();
    }

    /**
     * Creates a {@link org.gradle.internal.component.model.ConfigurationMetadata} implementation for this component.
     */
    protected abstract DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableSet<String> hierarchy, VariantMetadataRules componentMetadataRules);

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        AbstractLazyModuleComponentResolveMetadata that = (AbstractLazyModuleComponentResolveMetadata) o;
        return Objects.equal(configurationDefinitions, that.configurationDefinitions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), configurationDefinitions);
    }
}
