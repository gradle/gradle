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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.component.model.VariantMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class AbstractModuleComponentResolveMetadata implements ModuleComponentResolveMetadata {
    private final ModuleDescriptorState descriptor;
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ModuleComponentIdentifier componentIdentifier;
    private final boolean changing;
    private final String status;
    private final List<String> statusScheme;
    @Nullable
    private final ModuleSource moduleSource;
    private final Map<String, Configuration> configurationDefinitions;
    private final Map<String, DefaultConfigurationMetadata> configurations;
    @Nullable
    private final List<ModuleComponentArtifactMetadata> artifacts;
    private final List<? extends DependencyMetadata> dependencies;
    private final List<Exclude> excludes;

    protected AbstractModuleComponentResolveMetadata(MutableModuleComponentResolveMetadata metadata) {
        this.descriptor = metadata.getDescriptor();
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.isChanging();
        status = metadata.getStatus();
        statusScheme = metadata.getStatusScheme();
        moduleSource = metadata.getSource();
        configurationDefinitions = metadata.getConfigurationDefinitions();
        dependencies = metadata.getDependencies();
        excludes = descriptor.getExcludes();
        artifacts = metadata.getArtifacts();
        configurations = populateConfigurationsFromDescriptor();
        if (artifacts != null) {
            populateArtifacts(artifacts);
        } else {
            populateArtifactsFromDescriptor();
        }
    }

    protected AbstractModuleComponentResolveMetadata(AbstractModuleComponentResolveMetadata metadata, @Nullable ModuleSource source) {
        this.descriptor = metadata.getDescriptor();
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.isChanging();
        status = metadata.getStatus();
        statusScheme = metadata.getStatusScheme();
        moduleSource = source;
        configurationDefinitions = metadata.getConfigurationDefinitions();
        dependencies = metadata.getDependencies();
        excludes = metadata.excludes;
        artifacts = metadata.artifacts;
        configurations = metadata.configurations;
    }

    public ModuleDescriptorState getDescriptor() {
        return descriptor;
    }

    public boolean isChanging() {
        return changing;
    }

    public boolean isGenerated() {
        return descriptor.isGenerated();
    }

    public String getStatus() {
        return status;
    }

    public List<String> getStatusScheme() {
        return statusScheme;
    }

    public ModuleComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    public ModuleVersionIdentifier getId() {
        return moduleVersionIdentifier;
    }

    public ModuleSource getSource() {
        return moduleSource;
    }

    public Set<String> getConfigurationNames() {
        return configurations.keySet();
    }

    @Override
    public String toString() {
        return componentIdentifier.getDisplayName();
    }

    public ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier) {
        IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(getId().getName(), type, extension, classifier);
        return new DefaultModuleComponentArtifactMetadata(getComponentId(), ivyArtifactName);
    }

    private void populateArtifacts(List<ModuleComponentArtifactMetadata> artifacts) {
        for (DefaultConfigurationMetadata configuration : configurations.values()) {
            configuration.artifacts.addAll(artifacts);
        }
    }

    private void populateArtifactsFromDescriptor() {
        for (Artifact artifact : descriptor.getArtifacts()) {
            ModuleComponentArtifactMetadata artifactMetadata = new DefaultModuleComponentArtifactMetadata(componentIdentifier, artifact.getArtifactName());
            for (String configuration : artifact.getConfigurations()) {
                configurations.get(configuration).artifacts.add(artifactMetadata);
            }
        }
        Set<ConfigurationMetadata> visited = new HashSet<ConfigurationMetadata>();
        for (DefaultConfigurationMetadata configuration : configurations.values()) {
            configuration.collectInheritedArtifacts(visited);
        }
    }

    @Nullable
    @Override
    public List<ModuleComponentArtifactMetadata> getArtifacts() {
        return artifacts;
    }

    public List<? extends DependencyMetadata> getDependencies() {
        return dependencies;
    }

    @Override
    public Map<String, Configuration> getConfigurationDefinitions() {
        return configurationDefinitions;
    }

    public DefaultConfigurationMetadata getConfiguration(final String name) {
        return configurations.get(name);
    }

    private Map<String, DefaultConfigurationMetadata> populateConfigurationsFromDescriptor() {
        Set<String> configurationsNames = configurationDefinitions.keySet();
        Map<String, DefaultConfigurationMetadata> configurations = new HashMap<String, DefaultConfigurationMetadata>(configurationsNames.size());
        for (String configName : configurationsNames) {
            DefaultConfigurationMetadata configuration = populateConfigurationFromDescriptor(configName, configurationDefinitions, configurations);
            configuration.populateDependencies(dependencies);
        }
        return configurations;
    }

    private DefaultConfigurationMetadata populateConfigurationFromDescriptor(String name, Map<String, Configuration> configurationDefinitions, Map<String, DefaultConfigurationMetadata> configurations) {
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
                Collections.singletonList(populateConfigurationFromDescriptor(extendsFrom.get(0), configurationDefinitions, configurations)),
                excludes
            );
            configurations.put(name, populated);
            return populated;
        }
        List<DefaultConfigurationMetadata> hierarchy = new ArrayList<DefaultConfigurationMetadata>(extendsFrom.size());
        for (String confName : extendsFrom) {
            hierarchy.add(populateConfigurationFromDescriptor(confName, configurationDefinitions, configurations));
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

    private static class DefaultConfigurationMetadata implements ConfigurationMetadata {
        private final ModuleComponentIdentifier componentId;
        private final String name;
        private final List<DefaultConfigurationMetadata> parents;
        private final List<DependencyMetadata> configDependencies = new ArrayList<DependencyMetadata>();
        private final Set<ComponentArtifactMetadata> artifacts = new LinkedHashSet<ComponentArtifactMetadata>();
        private final ModuleExclusion exclusions;
        private final boolean transitive;
        private final boolean visible;
        private final Set<String> hierarchy;

        private DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, List<DefaultConfigurationMetadata> parents, List<Exclude> excludes) {
            this.componentId = componentId;
            this.name = name;
            this.parents = parents;
            this.transitive = transitive;
            this.visible = visible;
            this.hierarchy = calculateHierarchy();
            this.exclusions = filterExcludes(excludes);
        }

        private DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, List<Exclude> excludes) {
            this(componentId, name, transitive, visible, null, excludes);
        }

        @Override
        public String toString() {
            return componentId + ":" + name;
        }

        public String getName() {
            return name;
        }

        @Override
        public Set<String> getHierarchy() {
            return hierarchy;
        }

        private Set<String> calculateHierarchy() {
            if (parents == null) {
                return Collections.singleton(name);
            }
            Set<String> hierarchy = new LinkedHashSet<String>(1+parents.size());
            populateHierarchy(hierarchy);
            return hierarchy;
        }

        private void populateHierarchy(Set<String> accumulator) {
            accumulator.add(name);
            if (parents != null) {
                for (DefaultConfigurationMetadata parent : parents) {
                    parent.populateHierarchy(accumulator);
                }
            }
        }

        public boolean isTransitive() {
            return transitive;
        }

        public boolean isVisible() {
            return visible;
        }

        @Override
        public AttributeContainerInternal getAttributes() {
            return AttributeContainerInternal.EMPTY;
        }

        @Override
        public boolean isCanBeConsumed() {
            return true;
        }

        @Override
        public boolean isCanBeResolved() {
            return false;
        }

        public List<DependencyMetadata> getDependencies() {
            return configDependencies;
        }

        private void populateDependencies(Iterable<? extends DependencyMetadata> dependencies) {
            for (DependencyMetadata dependency : dependencies) {
                if (include(dependency)) {
                    this.configDependencies.add(dependency);
                }
            }
        }

        private boolean include(DependencyMetadata dependency) {
            Set<String> hierarchy = getHierarchy();
            for (String moduleConfiguration : dependency.getModuleConfigurations()) {
                if (moduleConfiguration.equals("%") || hierarchy.contains(moduleConfiguration)) {
                    return true;
                }
                if (moduleConfiguration.equals("*")) {
                    boolean include = true;
                    for (String conf2 : dependency.getModuleConfigurations()) {
                        if (conf2.startsWith("!") && conf2.substring(1).equals(getName())) {
                            include = false;
                            break;
                        }
                    }
                    if (include) {
                        return true;
                    }
                }
            }
            return false;
        }

        public ModuleExclusion getExclusions() {
            return exclusions;
        }

        private ModuleExclusion filterExcludes(Iterable<Exclude> excludes) {
            Set<String> hierarchy = getHierarchy();
            List<Exclude> filtered = Lists.newArrayList();
            for (Exclude exclude : excludes) {
                for (String config : exclude.getConfigurations()) {
                    if (hierarchy.contains(config)) {
                        filtered.add(exclude);
                        break;
                    }
                }
            }
            return ModuleExclusions.excludeAny(filtered);
        }

        public Set<ComponentArtifactMetadata> getArtifacts() {
            return artifacts;
        }

        @Override
        public Set<? extends VariantMetadata> getVariants() {
            return ImmutableSet.of(new DefaultVariantMetadata(getAttributes(), getArtifacts()));
        }

        public ModuleComponentArtifactMetadata artifact(IvyArtifactName artifact) {
            return new DefaultModuleComponentArtifactMetadata(componentId, artifact);
        }

        public void collectInheritedArtifacts(Set<ConfigurationMetadata> visited) {
            if (!visited.add(this)) {
                return;
            }
            if (parents == null) {
                return;
            }

            for (DefaultConfigurationMetadata parent : parents) {
                parent.collectInheritedArtifacts(visited);
                artifacts.addAll(parent.artifacts);
            }
        }
    }

}
