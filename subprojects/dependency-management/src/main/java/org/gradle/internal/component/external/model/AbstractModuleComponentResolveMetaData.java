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
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.Dependency;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.ConfigurationMetaData;
import org.gradle.internal.component.model.DefaultDependencyMetaData;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class AbstractModuleComponentResolveMetaData implements MutableModuleComponentResolveMetaData {
    private final ModuleDescriptorState descriptor;
    private ModuleVersionIdentifier moduleVersionIdentifier;
    private ModuleComponentIdentifier componentIdentifier;
    private boolean changing;
    private boolean generated;
    private String status;
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;
    private ModuleSource moduleSource;
    private Map<String, DefaultConfigurationMetaData> configurations = new HashMap<String, DefaultConfigurationMetaData>();
    private Multimap<String, ModuleComponentArtifactMetaData> artifactsByConfig;
    private List<DependencyMetaData> dependencies;
    private List<ExcludeRule> excludeRules;

    public AbstractModuleComponentResolveMetaData(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier, ModuleDescriptorState moduleDescriptor) {
        this.descriptor = moduleDescriptor;
        this.componentIdentifier = componentIdentifier;
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        generated = moduleDescriptor.isGenerated();
        status = moduleDescriptor.getStatus();
        configurations = populateConfigurationsFromDescriptor(moduleDescriptor);
        artifactsByConfig = populateArtifactsFromDescriptor(componentIdentifier, moduleDescriptor);
        dependencies = populateDependenciesFromDescriptor(moduleDescriptor);
        excludeRules = moduleDescriptor.getExcludeRules();
    }

    protected void copyTo(AbstractModuleComponentResolveMetaData copy) {
        copy.changing = changing;
        copy.status = status;
        copy.statusScheme = statusScheme;
        copy.moduleSource = moduleSource;
        copy.artifactsByConfig = artifactsByConfig;
        copy.dependencies = dependencies;
        copy.excludeRules = excludeRules;
    }

    public ModuleDescriptorState getDescriptor() {
        return descriptor;
    }

    public abstract AbstractModuleComponentResolveMetaData copy();

    public MutableModuleComponentResolveMetaData withSource(ModuleSource source) {
        AbstractModuleComponentResolveMetaData copy = copy();
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

    public ModuleComponentArtifactMetaData artifact(String type, @Nullable String extension, @Nullable String classifier) {
        IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(getId().getName(), type, extension, classifier);
        return new DefaultModuleComponentArtifactMetaData(getComponentId(), ivyArtifactName);
    }

    public void setArtifacts(Iterable<? extends ModuleComponentArtifactMetaData> artifacts) {
        this.artifactsByConfig = LinkedHashMultimap.create();
        for (String config : getConfigurationNames()) {
            artifactsByConfig.putAll(config, artifacts);
        }
    }

    private static Multimap<String, ModuleComponentArtifactMetaData> populateArtifactsFromDescriptor(ModuleComponentIdentifier componentId, ModuleDescriptorState descriptor) {
        Multimap<String, ModuleComponentArtifactMetaData> artifactsByConfig = LinkedHashMultimap.create();
        for (Artifact artifact : descriptor.getArtifacts()) {
            ModuleComponentArtifactMetaData artifactMetadata = new DefaultModuleComponentArtifactMetaData(componentId, artifact.getArtifactName());
            for (String configuration : artifact.getConfigurations()) {
                artifactsByConfig.put(configuration, artifactMetadata);
            }
        }
        return artifactsByConfig;
    }

    private static List<DependencyMetaData> populateDependenciesFromDescriptor(ModuleDescriptorState moduleDescriptor) {
        return CollectionUtils.collect(moduleDescriptor.getDependencies(), new Transformer<DependencyMetaData, Dependency>() {
            @Override
            public DependencyMetaData transform(Dependency dependency) {
                return new DefaultDependencyMetaData(dependency);
            }
        });
    }


    public List<DependencyMetaData> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Iterable<? extends DependencyMetaData> dependencies) {
        this.dependencies = CollectionUtils.toList(dependencies);
        for (DefaultConfigurationMetaData configuration : configurations.values()) {
            configuration.configDependencies = null;
        }
    }

    public DefaultConfigurationMetaData getConfiguration(final String name) {
        return configurations.get(name);
    }

    private Map<String, DefaultConfigurationMetaData> populateConfigurationsFromDescriptor(ModuleDescriptorState moduleDescriptor) {
        Map<String, DefaultConfigurationMetaData> configurations = Maps.newLinkedHashMap();
        for (String configName : moduleDescriptor.getConfigurationsNames()) {
            populateConfigurationFromDescriptor(configName, moduleDescriptor, configurations);
        }
        return configurations;
    }

    private DefaultConfigurationMetaData populateConfigurationFromDescriptor(String name, ModuleDescriptorState moduleDescriptor, Map<String, DefaultConfigurationMetaData> configurations) {
        DefaultConfigurationMetaData populated = configurations.get(name);
        if (populated != null) {
            return populated;
        }

        Configuration descriptorConfiguration = moduleDescriptor.getConfiguration(name);
        Set<String> hierarchy = new LinkedHashSet<String>();
        hierarchy.add(name);
        for (String parent : descriptorConfiguration.getExtendsFrom()) {
            hierarchy.addAll(populateConfigurationFromDescriptor(parent, moduleDescriptor, configurations).hierarchy);
        }
        boolean transitive = descriptorConfiguration.isTransitive();
        boolean visible = descriptorConfiguration.isVisible();
        DefaultConfigurationMetaData configuration = new DefaultConfigurationMetaData(name, hierarchy, transitive, visible);
        configurations.put(name, configuration);
        return configuration;
    }

    private class DefaultConfigurationMetaData implements ConfigurationMetaData {
        private final String name;
        private final Set<String> hierarchy;
        private List<DependencyMetaData> configDependencies;
        private Set<ComponentArtifactMetaData> artifacts;
        private LinkedHashSet<ExcludeRule> configExcludeRules;
        private final boolean transitive;
        private final boolean visible;

        private DefaultConfigurationMetaData(String name, Set<String> hierarchy, boolean transitive, boolean visible) {
            this.name = name;
            this.hierarchy = hierarchy;
            this.transitive = transitive;
            this.visible = visible;
        }

        @Override
        public String toString() {
            return getComponent().getComponentId() + ":" + name;
        }

        public ComponentResolveMetaData getComponent() {
            return AbstractModuleComponentResolveMetaData.this;
        }

        public String getName() {
            return name;
        }

        public Set<String> getHierarchy() {
            return hierarchy;
        }

        public boolean isTransitive() {
            return transitive;
        }

        public boolean isVisible() {
            return visible;
        }

        public List<DependencyMetaData> getDependencies() {
            if (configDependencies == null) {
                configDependencies = new ArrayList<DependencyMetaData>();
                for (DependencyMetaData dependency : dependencies) {
                    if (include(dependency)) {
                        configDependencies.add(dependency);
                    }
                }
            }
            return configDependencies;
        }

        private boolean include(DependencyMetaData dependency) {
            String[] moduleConfigurations = dependency.getModuleConfigurations();
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

        public Set<ExcludeRule> getExcludeRules() {
            if (configExcludeRules == null) {
                populateExcludeRulesFromDescriptor();
            }
            return configExcludeRules;
        }

        private void populateExcludeRulesFromDescriptor() {
            configExcludeRules = new LinkedHashSet<ExcludeRule>();
            for (ExcludeRule excludeRule : excludeRules) {
                for (String config : excludeRule.getConfigurations()) {
                    if (hierarchy.contains(config)) {
                        configExcludeRules.add(excludeRule);
                        break;
                    }
                }
            }
        }

        public Set<ComponentArtifactMetaData> getArtifacts() {
            if (artifacts == null) {
                artifacts = getArtifactsForConfiguration(this);
            }
            return artifacts;
        }

        protected Set<ComponentArtifactMetaData> getArtifactsForConfiguration(ConfigurationMetaData configurationMetaData) {
            Set<ComponentArtifactMetaData> artifactMetaData = new LinkedHashSet<ComponentArtifactMetaData>();
            for (String ancestor : configurationMetaData.getHierarchy()) {
                artifactMetaData.addAll(artifactsByConfig.get(ancestor));
            }
            return artifactMetaData;
        }

        public ModuleComponentArtifactMetaData artifact(IvyArtifactName artifact) {
            return new DefaultModuleComponentArtifactMetaData(getComponentId(), artifact);
        }
    }

}
