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

abstract class AbstractModuleComponentResolveMetadata implements ModuleComponentResolveMetadata {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ModuleComponentIdentifier componentIdentifier;
    private final boolean changing;
    private final boolean missing;
    private final String status;
    private final List<String> statusScheme;
    @Nullable
    private final ModuleSource moduleSource;
    private final ImmutableMap<String, Configuration> configurationDefinitions;
    private final Map<String, DependencyMetadataRules> dependencyMetadataRules;
    private final ImmutableList<? extends ComponentVariant> variants;
    private final HashValue contentHash;

    // Configurations are built on-demand, but only once.
    private final Map<String, DefaultConfigurationMetadata> configurations = Maps.newHashMap();
    private ImmutableList<? extends ConfigurationMetadata> graphVariants;

    protected AbstractModuleComponentResolveMetadata(AbstractMutableModuleComponentResolveMetadata metadata) {
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.isChanging();
        missing = metadata.isMissing();
        status = metadata.getStatus();
        statusScheme = metadata.getStatusScheme();
        moduleSource = metadata.getSource();
        configurationDefinitions = metadata.getConfigurationDefinitions();
        dependencyMetadataRules = metadata.dependencyMetadataRules;
        contentHash = metadata.getContentHash();

        variants = metadata.getVariants();
    }

    /**
     * Creates a copy of the given metadata
     */
    protected AbstractModuleComponentResolveMetadata(AbstractModuleComponentResolveMetadata metadata, @Nullable ModuleSource source) {
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.changing;
        missing = metadata.missing;
        status = metadata.status;
        statusScheme = metadata.statusScheme;
        moduleSource = source;
        configurationDefinitions = metadata.configurationDefinitions;
        dependencyMetadataRules = metadata.dependencyMetadataRules;
        contentHash = metadata.contentHash;

        variants = metadata.variants;
    }

    /**
     * Clear any cached state, for the case where the inputs are invalidated.
     * This only happens when constructing a copy
     */
    protected void copyCachedState(AbstractModuleComponentResolveMetadata metadata) {
        // Copy built-on-demand state
        configurations.putAll(metadata.configurations);
        this.graphVariants = metadata.graphVariants;
    }

    private DefaultConfigurationMetadata populateConfigurationFromDescriptor(String name, Map<String, Configuration> configurationDefinitions, Map<String, DefaultConfigurationMetadata> configurations) {
        DefaultConfigurationMetadata populated = configurations.get(name);
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
        populated = createConfiguration(componentIdentifier, name, transitive, visible, hierarchy, dependencyMetadataRules.get(name));
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
    protected abstract DefaultConfigurationMetadata createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<String> hierarchy, DependencyMetadataRules dependencyMetadataRules);

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
    public Set<String> getConfigurationNames() {
        return configurationDefinitions.keySet();
    }

    @Override
    public synchronized ConfigurationMetadata getConfiguration(final String name) {
        return populateConfigurationFromDescriptor(name, configurationDefinitions, configurations);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AbstractModuleComponentResolveMetadata that = (AbstractModuleComponentResolveMetadata) o;
        return changing == that.changing
            && missing == that.missing
            && Objects.equal(moduleVersionIdentifier, that.moduleVersionIdentifier)
            && Objects.equal(componentIdentifier, that.componentIdentifier)
            && Objects.equal(status, that.status)
            && Objects.equal(statusScheme, that.statusScheme)
            && Objects.equal(moduleSource, that.moduleSource)
            && Objects.equal(configurationDefinitions, that.configurationDefinitions)
            && Objects.equal(variants, that.variants)
            && Objects.equal(contentHash, that.contentHash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
            moduleVersionIdentifier,
            componentIdentifier,
            changing,
            missing,
            status,
            statusScheme,
            moduleSource,
            configurationDefinitions,
            variants,
            contentHash);
    }
}
