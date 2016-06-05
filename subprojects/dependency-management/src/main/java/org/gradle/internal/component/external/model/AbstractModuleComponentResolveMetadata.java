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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.Dependency;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultDependencyMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class AbstractModuleComponentResolveMetadata implements MutableModuleComponentResolveMetadata {
    private final ModuleDescriptorState descriptor;
    private ModuleVersionIdentifier moduleVersionIdentifier;
    private ModuleComponentIdentifier componentIdentifier;
    private boolean changing;
    private boolean generated;
    private String status;
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;
    private ModuleSource moduleSource;
    private Map<String, DefaultConfigurationMetadata> configurations;
    private Multimap<String, ModuleComponentArtifactMetadata> artifactsByConfig;
    private List<DependencyMetadata> dependencies;
    private List<Exclude> excludes;

    public AbstractModuleComponentResolveMetadata(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier, ModuleDescriptorState moduleDescriptor) {
        this.descriptor = moduleDescriptor;
        this.componentIdentifier = componentIdentifier;
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        generated = moduleDescriptor.isGenerated();
        status = moduleDescriptor.getStatus();
        configurations = populateConfigurationsFromDescriptor(moduleDescriptor);
        artifactsByConfig = populateArtifactsFromDescriptor(componentIdentifier, moduleDescriptor);
        dependencies = populateDependenciesFromDescriptor(moduleDescriptor);
        excludes = moduleDescriptor.getExcludes();
    }

    protected static ModuleDescriptorState createModuleDescriptor(ModuleComponentIdentifier componentIdentifier, Set<IvyArtifactName> componentArtifacts) {
        MutableModuleDescriptorState moduleDescriptorState = new MutableModuleDescriptorState(componentIdentifier);
        moduleDescriptorState.addConfiguration(org.gradle.api.artifacts.Dependency.DEFAULT_CONFIGURATION, true, true, Collections.<String>emptySet());

        for (IvyArtifactName artifactName : componentArtifacts) {
            moduleDescriptorState.addArtifact(artifactName, Collections.singleton(org.gradle.api.artifacts.Dependency.DEFAULT_CONFIGURATION));
        }

        if (componentArtifacts.isEmpty()) {
            IvyArtifactName defaultArtifact = new DefaultIvyArtifactName(componentIdentifier.getModule(), "jar", "jar");
            moduleDescriptorState.addArtifact(defaultArtifact, Collections.singleton(org.gradle.api.artifacts.Dependency.DEFAULT_CONFIGURATION));
        }

        return moduleDescriptorState;
    }

    protected void copyTo(AbstractModuleComponentResolveMetadata copy) {
        copy.changing = changing;
        copy.status = status;
        copy.statusScheme = statusScheme;
        copy.moduleSource = moduleSource;
        copy.artifactsByConfig = artifactsByConfig;
        copy.dependencies = dependencies;
        copy.excludes = excludes;
    }

    public ModuleDescriptorState getDescriptor() {
        return descriptor;
    }

    public abstract AbstractModuleComponentResolveMetadata copy();

    public MutableModuleComponentResolveMetadata withSource(ModuleSource source) {
        AbstractModuleComponentResolveMetadata copy = copy();
        copy.setModuleSource(source);
        return copy;
    }

    public boolean isChanging() {
        return changing;
    }

    public boolean isGenerated() {
        return generated;
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

    public void setComponentId(ModuleComponentIdentifier componentId) {
        this.componentIdentifier = componentId;
        setId(DefaultModuleVersionIdentifier.newId(componentId));
    }

    public ModuleVersionIdentifier getId() {
        return moduleVersionIdentifier;
    }

    public void setId(ModuleVersionIdentifier moduleVersionIdentifier) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
    }

    public void setChanging(boolean changing) {
        this.changing = changing;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setStatusScheme(List<String> statusScheme) {
        this.statusScheme = statusScheme;
    }

    public ModuleSource getSource() {
        return moduleSource;
    }

    public void setSource(ModuleSource source) {
        this.moduleSource = source;
    }

    public void setModuleSource(ModuleSource moduleSource) {
        this.moduleSource = moduleSource;
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

    public void setArtifacts(Iterable<? extends ModuleComponentArtifactMetadata> artifacts) {
        this.artifactsByConfig = LinkedHashMultimap.create();
        for (String config : getConfigurationNames()) {
            artifactsByConfig.putAll(config, artifacts);
        }
    }

    private static Multimap<String, ModuleComponentArtifactMetadata> populateArtifactsFromDescriptor(ModuleComponentIdentifier componentId, ModuleDescriptorState descriptor) {
        Multimap<String, ModuleComponentArtifactMetadata> artifactsByConfig = LinkedHashMultimap.create();
        for (Artifact artifact : descriptor.getArtifacts()) {
            ModuleComponentArtifactMetadata artifactMetadata = new DefaultModuleComponentArtifactMetadata(componentId, artifact.getArtifactName());
            for (String configuration : artifact.getConfigurations()) {
                artifactsByConfig.put(configuration, artifactMetadata);
            }
        }
        return artifactsByConfig;
    }

    private static List<DependencyMetadata> populateDependenciesFromDescriptor(ModuleDescriptorState moduleDescriptor) {
        List<Dependency> dependencies = moduleDescriptor.getDependencies();
        List<DependencyMetadata> result = new ArrayList<DependencyMetadata>(dependencies.size());
        for (Dependency dependency : dependencies) {
            result.add(new DefaultDependencyMetadata(dependency));
        }
        return result;
    }


    public List<DependencyMetadata> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Iterable<? extends DependencyMetadata> dependencies) {
        this.dependencies = CollectionUtils.toList(dependencies);
        for (DefaultConfigurationMetadata configuration : configurations.values()) {
            configuration.configDependencies = null;
        }
    }

    public DefaultConfigurationMetadata getConfiguration(final String name) {
        return configurations.get(name);
    }

    private Map<String, DefaultConfigurationMetadata> populateConfigurationsFromDescriptor(ModuleDescriptorState moduleDescriptor) {
        Set<String> configurationsNames = moduleDescriptor.getConfigurationsNames();
        Map<String, DefaultConfigurationMetadata> configurations = new HashMap<String, DefaultConfigurationMetadata>(configurationsNames.size());
        for (String configName : configurationsNames) {
            populateConfigurationFromDescriptor(configName, moduleDescriptor, configurations);
        }
        return configurations;
    }

    private DefaultConfigurationMetadata populateConfigurationFromDescriptor(String name, ModuleDescriptorState moduleDescriptor, Map<String, DefaultConfigurationMetadata> configurations) {
        DefaultConfigurationMetadata populated = configurations.get(name);
        if (populated != null) {
            return populated;
        }

        Configuration descriptorConfiguration = moduleDescriptor.getConfiguration(name);
        List<String> extendsFrom = descriptorConfiguration.getExtendsFrom();
        boolean transitive = descriptorConfiguration.isTransitive();
        boolean visible = descriptorConfiguration.isVisible();
        if (extendsFrom.isEmpty()) {
            // tail
            populated = new DefaultConfigurationMetadata(name, transitive, visible);
            configurations.put(name, populated);
            return populated;
        } else if (extendsFrom.size() == 1) {
            populated = new DefaultConfigurationMetadata(
                name,
                transitive,
                visible,
                Collections.singletonList(populateConfigurationFromDescriptor(extendsFrom.get(0), moduleDescriptor, configurations))
            );
            configurations.put(name, populated);
            return populated;
        }
        List<DefaultConfigurationMetadata> hierarchy = new ArrayList<DefaultConfigurationMetadata>(extendsFrom.size());
        for (String confName : extendsFrom) {
            hierarchy.add(populateConfigurationFromDescriptor(confName, moduleDescriptor, configurations));
        }
        populated = new DefaultConfigurationMetadata(
            name,
            transitive,
            visible,
            hierarchy
        );

        configurations.put(name, populated);
        return populated;
    }

    private class DefaultConfigurationMetadata implements ConfigurationMetadata {
        private final String name;
        private final List<DefaultConfigurationMetadata> parents;
        private List<DependencyMetadata> configDependencies;
        private Set<ComponentArtifactMetadata> artifacts;
        private LinkedHashSet<Exclude> configExcludes;
        private final boolean transitive;
        private final boolean visible;

        private DefaultConfigurationMetadata(String name, boolean transitive, boolean visible, List<DefaultConfigurationMetadata> parents) {
            this.name = name;
            this.parents = parents;
            this.transitive = transitive;
            this.visible = visible;
        }

        private DefaultConfigurationMetadata(String name, boolean transitive, boolean visible) {
            this(name, transitive, visible, null);
        }


        @Override
        public String toString() {
            return getComponent().getComponentId() + ":" + name;
        }

        public ComponentResolveMetadata getComponent() {
            return AbstractModuleComponentResolveMetadata.this;
        }

        public String getName() {
            return name;
        }

        public Set<String> getHierarchy() {
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

        public List<DependencyMetadata> getDependencies() {
            if (configDependencies == null) {
                configDependencies = new ArrayList<DependencyMetadata>();
                for (DependencyMetadata dependency : dependencies) {
                    if (include(dependency)) {
                        configDependencies.add(dependency);
                    }
                }
            }
            return configDependencies;
        }

        private boolean include(DependencyMetadata dependency) {
            String[] moduleConfigurations = dependency.getModuleConfigurations();
            Set<String> hierarchy = getHierarchy();
            for (int i = 0; i < moduleConfigurations.length; i++) {
                String moduleConfiguration = moduleConfigurations[i];
                if (moduleConfiguration.equals("%") || hierarchy.contains(moduleConfiguration)) {
                    return true;
                }
                if (moduleConfiguration.equals("*")) {
                    boolean include = true;
                    for (int j = i + 1; j < moduleConfigurations.length && moduleConfigurations[j].startsWith("!"); j++) {
                        if (moduleConfigurations[j].substring(1).equals(getName())) {
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

        public Set<Exclude> getExcludes() {
            if (configExcludes == null) {
                populateExcludeRulesFromDescriptor();
            }
            return configExcludes;
        }

        private void populateExcludeRulesFromDescriptor() {
            configExcludes = new LinkedHashSet<Exclude>();
            Set<String> hierarchy = getHierarchy();
            for (Exclude exclude : excludes) {
                for (String config : exclude.getConfigurations()) {
                    if (hierarchy.contains(config)) {
                        configExcludes.add(exclude);
                        break;
                    }
                }
            }
        }

        public Set<ComponentArtifactMetadata> getArtifacts() {
            if (artifacts == null) {
                artifacts = getArtifactsForConfiguration(this, new LinkedHashSet<ComponentArtifactMetadata>(), new HashSet<String>());
            }
            return artifacts;
        }

        protected Set<ComponentArtifactMetadata> getArtifactsForConfiguration(ConfigurationMetadata configurationMetadata, Set<ComponentArtifactMetadata> accumulator, HashSet<String> visited) {
            String name = configurationMetadata.getName();
            if (visited.contains(name)) {
                return accumulator;
            }
            visited.add(name);
            accumulator.addAll(artifactsByConfig.get(name));
            if (parents!= null) {
                for (DefaultConfigurationMetadata parent : parents) {
                    getArtifactsForConfiguration(parent, accumulator, visited);
                }
            }
            return accumulator;
        }

        public ModuleComponentArtifactMetadata artifact(IvyArtifactName artifact) {
            return new DefaultModuleComponentArtifactMetadata(getComponentId(), artifact);
        }
    }

}
