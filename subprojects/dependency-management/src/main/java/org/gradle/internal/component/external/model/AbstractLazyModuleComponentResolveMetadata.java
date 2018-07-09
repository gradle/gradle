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
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ModuleSource;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private Optional<ImmutableList<? extends ConfigurationMetadata>> graphVariants;
    // Configurations are built on-demand, but only once.
    private final Map<String, ConfigurationMetadata> configurations = Maps.newHashMap();

    protected AbstractLazyModuleComponentResolveMetadata(AbstractMutableModuleComponentResolveMetadata metadata) {
        super(metadata);
        configurationDefinitions = metadata.getConfigurationDefinitions();
        variantMetadataRules = metadata.getVariantMetadataRules();
    }

    /**
     * Creates a copy of the given metadata
     */
    protected AbstractLazyModuleComponentResolveMetadata(AbstractLazyModuleComponentResolveMetadata metadata, @Nullable ModuleSource source) {
        super(metadata, source);
        this.configurationDefinitions = metadata.configurationDefinitions;
        variantMetadataRules = metadata.variantMetadataRules;
    }

    /**
     * Clear any cached state, for the case where the inputs are invalidated.
     * This only happens when constructing a copy
     */
    protected void copyCachedState(AbstractLazyModuleComponentResolveMetadata metadata) {
        // Copy built-on-demand state
        configurations.putAll(metadata.configurations);
        this.graphVariants = metadata.graphVariants;
    }

    protected VariantMetadataRules getVariantMetadataRules() {
        return variantMetadataRules;
    }

    public ImmutableMap<String, Configuration> getConfigurationDefinitions() {
        return configurationDefinitions;
    }

    private Optional<ImmutableList<? extends ConfigurationMetadata>> buildVariantsForGraphTraversal(List<? extends ComponentVariant> variants) {
        if (variants.isEmpty()) {
            return maybeDeriveVariants();
        }
        ImmutableList.Builder<ConfigurationMetadata> configurations = new ImmutableList.Builder<ConfigurationMetadata>();
        for (ComponentVariant variant : variants) {
            configurations.add(new LazyVariantBackedConfigurationMetadata(getId(), variant, getAttributes(), getAttributesFactory(), variantMetadataRules));
        }
        return Optional.<ImmutableList<? extends ConfigurationMetadata>>of(configurations.build());
    }

    @Override
    public synchronized Optional<ImmutableList<? extends ConfigurationMetadata>> getVariantsForGraphTraversal() {
        if (graphVariants == null) {
            graphVariants = buildVariantsForGraphTraversal(getVariants());
        }
        return graphVariants;
    }

    @Override
    public Set<String> getConfigurationNames() {
        return configurationDefinitions.keySet();
    }

    @Override
    public synchronized ConfigurationMetadata getConfiguration(final String name) {
        return populateConfigurationFromDescriptor(name, configurationDefinitions, configurations);
    }

    private ConfigurationMetadata populateConfigurationFromDescriptor(String name, Map<String, Configuration> configurationDefinitions, Map<String, ConfigurationMetadata> configurations) {
        ConfigurationMetadata populated = configurations.get(name);
        if (populated != null) {
            return populated;
        }

        Configuration descriptorConfiguration = configurationDefinitions.get(name);
        if (descriptorConfiguration == null) {
            return null;
        }

        ImmutableList<String> hierarchy = constructHierarchy(descriptorConfiguration);
        boolean transitive = descriptorConfiguration.isTransitive();
        boolean visible = descriptorConfiguration.isVisible();
        populated = createConfiguration(getId(), name, transitive, visible, hierarchy, variantMetadataRules);
        configurations.put(name, populated);
        return populated;
    }

    private ImmutableList<String> constructHierarchy(Configuration descriptorConfiguration) {
        if (descriptorConfiguration.getExtendsFrom().isEmpty()) {
            return ImmutableList.of(descriptorConfiguration.getName());
        }
        Set<String> accumulator = new LinkedHashSet<String>();
        populateHierarchy(descriptorConfiguration, accumulator);
        return ImmutableList.copyOf(accumulator);
    }

    private void populateHierarchy(Configuration metadata, Set<String> accumulator) {
        accumulator.add(metadata.getName());
        for (String parentName : metadata.getExtendsFrom()) {
            Configuration parent = configurationDefinitions.get(parentName);
            populateHierarchy(parent, accumulator);
        }
    }

    /**
     * Creates a {@link org.gradle.internal.component.model.ConfigurationMetadata} implementation for this component.
     */
    protected abstract DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> hierarchy, VariantMetadataRules componentMetadataRules);

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
        return Objects.hashCode(super.hashCode(),
            configurationDefinitions);
    }
}
