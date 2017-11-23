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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadataRules;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.hash.HashValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class AbstractModuleComponentResolveMetadata<T extends DefaultConfigurationMetadata> implements ModuleComponentResolveMetadata {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ModuleComponentIdentifier componentIdentifier;
    private final boolean changing;
    private final boolean missing;
    private final String status;
    private final List<String> statusScheme;
    @Nullable
    private final ModuleSource moduleSource;
    private final ImmutableMap<String, Configuration> configurationDefinitions;
    private final List<? extends ModuleDependencyMetadata> dependencies;
    private final Map<String, DependencyMetadataRules> dependencyMetadataRules;
    private final ImmutableList<? extends ComponentVariant> variants;
    private final HashValue contentHash;

    // Configurations are built on-demand, but only once.
    private final Map<String, T> configurations = Maps.newHashMap();
    private ImmutableList<? extends ConfigurationMetadata> graphVariants;

    protected AbstractModuleComponentResolveMetadata(AbstractMutableModuleComponentResolveMetadata<T> metadata) {
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.isChanging();
        missing = metadata.isMissing();
        status = metadata.getStatus();
        statusScheme = metadata.getStatusScheme();
        moduleSource = metadata.getSource();
        configurationDefinitions = metadata.getConfigurationDefinitions();
        dependencies = metadata.getDependencies();
        dependencyMetadataRules = metadata.dependencyMetadataRules;
        contentHash = metadata.getContentHash();

        variants = metadata.getVariants();
    }

    /**
     * Creates a copy of the given metadata
     */
    protected AbstractModuleComponentResolveMetadata(AbstractModuleComponentResolveMetadata<T> metadata, @Nullable ModuleSource source) {
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.changing;
        missing = metadata.missing;
        status = metadata.status;
        statusScheme = metadata.statusScheme;
        moduleSource = source;
        configurationDefinitions = metadata.configurationDefinitions;
        dependencies = metadata.dependencies;
        dependencyMetadataRules = metadata.dependencyMetadataRules;
        contentHash = metadata.contentHash;

        variants = metadata.variants;

        // Copy built-on-demand state
        configurations.putAll(metadata.configurations);
        this.graphVariants = metadata.graphVariants;
    }

    /**
     * Creates a copy of the given metadata with the provided dependencies.
     */
    protected AbstractModuleComponentResolveMetadata(AbstractModuleComponentResolveMetadata<T> metadata, List<? extends ModuleDependencyMetadata> dependencies) {
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.changing;
        missing = metadata.missing;
        status = metadata.status;
        statusScheme = metadata.statusScheme;
        moduleSource = metadata.moduleSource;
        configurationDefinitions = metadata.configurationDefinitions;
        dependencyMetadataRules = metadata.dependencyMetadataRules;
        contentHash = metadata.contentHash;

        variants = metadata.getVariants();

        // Set the dependencies, and do not copy built-on-demand state
        this.dependencies = dependencies;
    }

    private T populateConfigurationFromDescriptor(String name, Map<String, Configuration> configurationDefinitions, Map<String, T> configurations) {
        T populated = configurations.get(name);
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
        populated = createAndPopulateConfiguration(componentIdentifier, name, transitive, visible, hierarchy);
        configurations.put(name, populated);
        return populated;
    }

    // TODO:DAZ We might be better off constructing the tree once
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
    private T createAndPopulateConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> hierarchy) {
        T configuration = createConfiguration(componentId, name, transitive, visible, hierarchy);
        configuration.populateDependencies(dependencies, dependencyMetadataRules.get(name));
        return configuration;
    }

    /**
     * Creates a {@link org.gradle.internal.component.model.ConfigurationMetadata} implementation for this component.
     */
    protected abstract T createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> hierarchy);

    private ImmutableList<? extends ConfigurationMetadata> buildVariantsForGraphTraversal(List<? extends ComponentVariant> variants) {
        if (variants.isEmpty()) {
            return ImmutableList.of();
        }
        List<VariantBackedConfigurationMetadata> configurations = new ArrayList<VariantBackedConfigurationMetadata>(variants.size());
        for (ComponentVariant variant : variants) {
            configurations.add(new VariantBackedConfigurationMetadata(getComponentId(), variant, dependencyMetadataRules.get(variant.getName())));
        }
        return ImmutableList.copyOf(configurations);
    }

    @Nullable
    @Override
    public AttributesSchemaInternal getAttributesSchema() {
        return EmptySchema.INSTANCE;
    }

    @Override
    public HashValue getContentHash() {
        return contentHash;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public boolean isMissing() {
        return missing;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public List<String> getStatusScheme() {
        return statusScheme;
    }

    @Override
    public ModuleComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return moduleVersionIdentifier;
    }

    @Override
    public ModuleSource getSource() {
        return moduleSource;
    }

    @Override
    public ImmutableList<? extends ComponentVariant> getVariants() {
        return variants;
    }

    @Override
    public synchronized ImmutableList<? extends ConfigurationMetadata> getVariantsForGraphTraversal() {
        if (graphVariants == null) {
            graphVariants = buildVariantsForGraphTraversal(variants);
        }
        return graphVariants;
    }

    @Override
    public String toString() {
        return componentIdentifier.getDisplayName();
    }

    @Override
    public ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier) {
        IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(getId().getName(), type, extension, classifier);
        return new DefaultModuleComponentArtifactMetadata(getComponentId(), ivyArtifactName);
    }

    @Override
    public List<? extends ModuleDependencyMetadata> getDependencies() {
        return dependencies;
    }

    @Override
    public Set<String> getConfigurationNames() {
        return configurationDefinitions.keySet();
    }

    @Override
    public synchronized ConfigurationMetadata getConfiguration(final String name) {
        // TODO:DAZ Try `CacheBuilder` or similar
        return populateConfigurationFromDescriptor(name, configurationDefinitions, configurations);
    }
}
