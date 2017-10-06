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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.hash.HashValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    private final Map<String, DefaultConfigurationMetadata> configurations;
    // This should live in a decorator rather than here
    @Nullable
    private final List<? extends ModuleComponentArtifactMetadata> artifactOverrides;
    private final List<? extends DependencyMetadata> dependencies;
    private final HashValue contentHash;

    protected AbstractModuleComponentResolveMetadata(MutableModuleComponentResolveMetadata metadata, Map<String, Configuration> configurationDefinitions, Iterable<Artifact> artifacts, ImmutableList<Exclude> excludes) {
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.isChanging();
        missing = metadata.isMissing();
        status = metadata.getStatus();
        statusScheme = metadata.getStatusScheme();
        moduleSource = metadata.getSource();
        dependencies = metadata.getDependencies();
        artifactOverrides = metadata.getArtifactOverrides();
        configurations = populateConfigurationsFromDescriptor(configurationDefinitions, excludes);
        if (artifactOverrides != null) {
            populateArtifactsFromOverrides(artifactOverrides);
        } else {
            populateArtifacts(artifacts);
        }
        contentHash = metadata.getContentHash();
    }

    /**
     * Creates a copy of the given metadata
     */
    protected AbstractModuleComponentResolveMetadata(AbstractModuleComponentResolveMetadata metadata, @Nullable ModuleSource source) {
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.isChanging();
        missing = metadata.isMissing();
        status = metadata.getStatus();
        statusScheme = metadata.getStatusScheme();
        moduleSource = source;
        dependencies = metadata.getDependencies();
        artifactOverrides = metadata.artifactOverrides;
        configurations = metadata.configurations;
        contentHash = metadata.getContentHash();
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
    public Set<String> getConfigurationNames() {
        return configurations.keySet();
    }

    @Override
    public List<? extends ConfigurationMetadata> getConsumableConfigurationsHavingAttributes() {
        return Collections.emptyList();
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

    private void populateArtifactsFromOverrides(List<? extends ModuleComponentArtifactMetadata> artifacts) {
        for (DefaultConfigurationMetadata configuration : configurations.values()) {
            configuration.addArtifacts(artifacts);
        }
    }

    private void populateArtifacts(Iterable<Artifact> artifacts) {
        for (Artifact artifact : artifacts) {
            ModuleComponentArtifactMetadata artifactMetadata = new DefaultModuleComponentArtifactMetadata(componentIdentifier, artifact.getArtifactName());
            for (String configuration : artifact.getConfigurations()) {
                configurations.get(configuration).addArtifact(artifactMetadata);
            }
        }
        Set<ConfigurationMetadata> visited = new HashSet<ConfigurationMetadata>();
        for (DefaultConfigurationMetadata configuration : configurations.values()) {
            configuration.collectInheritedArtifacts(visited);
        }
    }

    @Nullable
    @Override
    public List<? extends ModuleComponentArtifactMetadata> getArtifactOverrides() {
        return artifactOverrides;
    }

    @Override
    public List<? extends DependencyMetadata> getDependencies() {
        return dependencies;
    }

    @Override
    public ConfigurationMetadata getConfiguration(final String name) {
        return configurations.get(name);
    }

    private Map<String, DefaultConfigurationMetadata> populateConfigurationsFromDescriptor(Map<String, Configuration> configurationDefinitions, List<Exclude> excludes) {
        Set<String> configurationsNames = configurationDefinitions.keySet();
        Map<String, DefaultConfigurationMetadata> configurations = new HashMap<String, DefaultConfigurationMetadata>(configurationsNames.size());
        for (String configName : configurationsNames) {
            DefaultConfigurationMetadata configuration = populateConfigurationFromDescriptor(configName, configurationDefinitions, configurations, excludes);
            configuration.populateDependencies(dependencies);
        }
        return configurations;
    }

    private DefaultConfigurationMetadata populateConfigurationFromDescriptor(String name, Map<String, Configuration> configurationDefinitions, Map<String, DefaultConfigurationMetadata> configurations, List<Exclude> excludes) {
        DefaultConfigurationMetadata populated = configurations.get(name);
        if (populated != null) {
            return populated;
        }

        Configuration descriptorConfiguration = configurationDefinitions.get(name);
        List<String> extendsFrom = descriptorConfiguration.getExtendsFrom();
        boolean transitive = descriptorConfiguration.isTransitive();
        boolean visible = descriptorConfiguration.isVisible();
        if (extendsFrom.isEmpty()) {
            // tail
            populated = new DefaultConfigurationMetadata(componentIdentifier, name, transitive, visible, excludes);
            configurations.put(name, populated);
            return populated;
        } else if (extendsFrom.size() == 1) {
            populated = new DefaultConfigurationMetadata(
                componentIdentifier,
                name,
                transitive,
                visible,
                Collections.singletonList(populateConfigurationFromDescriptor(extendsFrom.get(0), configurationDefinitions, configurations, excludes)),
                excludes
            );
            configurations.put(name, populated);
            return populated;
        }
        List<DefaultConfigurationMetadata> hierarchy = new ArrayList<DefaultConfigurationMetadata>(extendsFrom.size());
        for (String confName : extendsFrom) {
            hierarchy.add(populateConfigurationFromDescriptor(confName, configurationDefinitions, configurations, excludes));
        }
        populated = new DefaultConfigurationMetadata(
            componentIdentifier,
            name,
            transitive,
            visible,
            hierarchy,
            excludes
        );

        configurations.put(name, populated);
        return populated;
    }

}
