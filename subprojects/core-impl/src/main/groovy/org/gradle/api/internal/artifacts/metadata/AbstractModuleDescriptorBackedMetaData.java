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

package org.gradle.api.internal.artifacts.metadata;

import org.apache.ivy.core.module.descriptor.*;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.util.CollectionUtils;

import java.util.*;

public abstract class AbstractModuleDescriptorBackedMetaData implements ComponentMetaData {
    private static final List<String> DEFAULT_STATUS_SCHEME = Arrays.asList("integration", "milestone", "release");

    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ModuleDescriptor moduleDescriptor;
    private final ComponentIdentifier componentIdentifier;
    private ModuleSource moduleSource;
    private boolean changing;
    private String status;
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;
    private List<DependencyMetaData> dependencies;
    private Map<String, DefaultConfigurationMetaData> configurations = new HashMap<String, DefaultConfigurationMetaData>();

    public AbstractModuleDescriptorBackedMetaData(ModuleVersionIdentifier moduleVersionIdentifier, ModuleDescriptor moduleDescriptor, ComponentIdentifier componentIdentifier) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.moduleDescriptor = moduleDescriptor;
        this.componentIdentifier = componentIdentifier;
        status = moduleDescriptor.getStatus();
    }

    protected void copyTo(AbstractModuleDescriptorBackedMetaData copy) {
        copy.dependencies = dependencies;
        copy.changing = changing;
        copy.status = status;
        copy.statusScheme = statusScheme;
        copy.moduleSource = moduleSource;
    }

    @Override
    public String toString() {
        return moduleVersionIdentifier.toString();
    }

    public ModuleVersionIdentifier getId() {
        return moduleVersionIdentifier;
    }

    public ModuleSource getSource() {
        return moduleSource;
    }

    public void setModuleSource(ModuleSource moduleSource) {
        this.moduleSource = moduleSource;
    }

    public ModuleDescriptor getDescriptor() {
        return moduleDescriptor;
    }

    public boolean isChanging() {
        return changing;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getStatusScheme() {
        return statusScheme;
    }

    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
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

    public List<DependencyMetaData> getDependencies() {
        if (dependencies == null) {
            dependencies = new ArrayList<DependencyMetaData>();
            for (final DependencyDescriptor dependencyDescriptor : moduleDescriptor.getDependencies()) {
                dependencies.add(new DefaultDependencyMetaData(dependencyDescriptor));
            }
        }
        return dependencies;
    }

    public void setDependencies(Iterable<? extends DependencyMetaData> dependencies) {
        this.dependencies = CollectionUtils.toList(dependencies);
        for (DefaultConfigurationMetaData configuration : configurations.values()) {
            configuration.dependencies = null;
        }
    }

    public DefaultConfigurationMetaData getConfiguration(final String name) {
        DefaultConfigurationMetaData configuration = configurations.get(name);
        if (configuration == null) {
            Configuration descriptor = moduleDescriptor.getConfiguration(name);
            if (descriptor == null) {
                return null;
            }
            Set<String> hierarchy = new LinkedHashSet<String>();
            hierarchy.add(name);
            for (String parent : descriptor.getExtends()) {
                hierarchy.addAll(getConfiguration(parent).hierarchy);
            }
            configuration = new DefaultConfigurationMetaData(name, descriptor, hierarchy);
            configurations.put(name, configuration);
        }
        return configuration;
    }

    protected abstract Set<ComponentArtifactMetaData> getArtifactsForConfiguration(ConfigurationMetaData configuration);

    private class DefaultConfigurationMetaData implements ConfigurationMetaData {
        private final String name;
        private final Configuration descriptor;
        private final Set<String> hierarchy;
        private List<DependencyMetaData> dependencies;
        private Set<ComponentArtifactMetaData> artifacts;
        private LinkedHashSet<ExcludeRule> excludeRules;

        private DefaultConfigurationMetaData(String name, Configuration descriptor, Set<String> hierarchy) {
            this.name = name;
            this.descriptor = descriptor;
            this.hierarchy = hierarchy;
        }

        @Override
        public String toString() {
            return String.format("%s:%s", moduleVersionIdentifier, name);
        }

        public ComponentMetaData getComponent() {
            return AbstractModuleDescriptorBackedMetaData.this;
        }

        public String getName() {
            return name;
        }

        public Set<String> getHierarchy() {
            return hierarchy;
        }

        public boolean isTransitive() {
            return descriptor.isTransitive();
        }

        public List<DependencyMetaData> getDependencies() {
            if (dependencies == null) {
                dependencies = new ArrayList<DependencyMetaData>();
                for (DependencyMetaData dependency : AbstractModuleDescriptorBackedMetaData.this.getDependencies()) {
                    if (include(dependency)) {
                        dependencies.add(dependency);
                    }
                }
            }
            return dependencies;
        }

        private boolean include(DependencyMetaData dependency) {
            String[] moduleConfigurations = dependency.getDescriptor().getModuleConfigurations();
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
            if (excludeRules == null) {
                excludeRules = new LinkedHashSet<ExcludeRule>();
                for (ExcludeRule excludeRule : moduleDescriptor.getAllExcludeRules()) {
                    for (String config : excludeRule.getConfigurations()) {
                        if (hierarchy.contains(config)) {
                            excludeRules.add(excludeRule);
                            break;
                        }
                    }
                }
            }
            return excludeRules;
        }

        public Set<ComponentArtifactMetaData> getArtifacts() {
            if (artifacts == null) {
                artifacts = getArtifactsForConfiguration(this);
            }
            return artifacts;
        }
    }
}
