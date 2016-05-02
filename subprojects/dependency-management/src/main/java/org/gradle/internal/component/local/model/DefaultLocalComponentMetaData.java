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

package org.gradle.internal.component.local.model;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultLocalComponentMetaData implements LocalComponentMetaData, BuildableLocalComponentMetaData {
    private final Map<String, DefaultLocalConfigurationMetaData> allConfigurations = Maps.newHashMap();
    private final Multimap<String, ComponentArtifactMetaData> allArtifacts = ArrayListMultimap.create();
    private final List<DependencyMetaData> allDependencies = Lists.newArrayList();
    private final List<ExcludeRule> allExcludeRules = Lists.newArrayList();
    private final ModuleVersionIdentifier id;
    private final ComponentIdentifier componentIdentifier;
    private final String status;

    public DefaultLocalComponentMetaData(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier, String status) {
        this.id = id;
        this.componentIdentifier = componentIdentifier;
        this.status = status;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public void addArtifacts(String configuration, Iterable<? extends PublishArtifact> artifacts) {
        for (PublishArtifact artifact : artifacts) {
            ComponentArtifactMetaData artifactMetaData = new PublishArtifactLocalArtifactMetaData(componentIdentifier, componentIdentifier.getDisplayName(), artifact);
            addArtifact(configuration, artifactMetaData);
        }
    }

    public void addArtifact(String configuration, ComponentArtifactMetaData artifactMetaData) {
        allArtifacts.put(configuration, artifactMetaData);
    }

    public void addConfiguration(String name, String description, Set<String> extendsFrom, Set<String> hierarchy, boolean visible, boolean transitive, TaskDependency buildDependencies) {
        DefaultLocalConfigurationMetaData conf = new DefaultLocalConfigurationMetaData(name, description, visible, transitive, extendsFrom, hierarchy, buildDependencies);
        allConfigurations.put(name, conf);
    }

    public void addDependency(DependencyMetaData dependency) {
        allDependencies.add(dependency);
    }

    public void addExcludeRule(ExcludeRule excludeRule) {
        allExcludeRules.add(excludeRule);
    }

    @Override
    public String toString() {
        return componentIdentifier.getDisplayName();
    }

    public ModuleSource getSource() {
        return null;
    }

    public ComponentResolveMetaData withSource(ModuleSource source) {
        throw new UnsupportedOperationException();
    }

    public boolean isGenerated() {
        return false;
    }

    public boolean isChanging() {
        return false;
    }

    public String getStatus() {
        return status;
    }

    public List<String> getStatusScheme() {
        return DEFAULT_STATUS_SCHEME;
    }

    public ComponentIdentifier getComponentId() {
        return componentIdentifier;
    }

    public List<DependencyMetaData> getDependencies() {
        return allDependencies;
    }

    public List<ExcludeRule> getExcludeRules() {
        return allExcludeRules;
    }

    @Override
    public Set<String> getConfigurationNames() {
        return allConfigurations.keySet();
    }

    public DefaultLocalConfigurationMetaData getConfiguration(final String name) {
        return allConfigurations.get(name);
    }

    private class DefaultLocalConfigurationMetaData implements LocalConfigurationMetaData {
        private final String name;
        private final String description;
        private final boolean transitive;
        private final boolean visible;
        private final Set<String> hierarchy;
        private final Set<String> extendsFrom;
        private final TaskDependency buildDependencies;

        private List<DependencyMetaData> configurationDependencies;
        private LinkedHashSet<ExcludeRule> configurationExcludeRules;

        private DefaultLocalConfigurationMetaData(String name, String description, boolean visible, boolean transitive, Set<String> extendsFrom, Set<String> hierarchy, TaskDependency buildDependencies) {
            this.name = name;
            this.description = description;
            this.transitive = transitive;
            this.visible = visible;
            this.hierarchy = hierarchy;
            this.extendsFrom = extendsFrom;
            this.buildDependencies = buildDependencies;
        }

        @Override
        public String toString() {
            return componentIdentifier.getDisplayName() + ":" + name;
        }

        public ComponentResolveMetaData getComponent() {
            return DefaultLocalComponentMetaData.this;
        }

        @Override
        public TaskDependency getDirectBuildDependencies() {
            return buildDependencies;
        }

        public String getDescription() {
            return description;
        }

        public Set<String> getExtendsFrom() {
            return extendsFrom;
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
            if (configurationDependencies == null) {
                configurationDependencies = new ArrayList<DependencyMetaData>();
                for (DependencyMetaData dependency : allDependencies) {
                    if (include(dependency)) {
                        configurationDependencies.add(dependency);
                    }
                }
            }
            return configurationDependencies;
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
            if (configurationExcludeRules == null) {
                configurationExcludeRules = new LinkedHashSet<ExcludeRule>();
                for (ExcludeRule excludeRule : allExcludeRules) {
                    for (String config : excludeRule.getConfigurations()) {
                        if (hierarchy.contains(config)) {
                            configurationExcludeRules.add(excludeRule);
                            break;
                        }
                    }
                }
            }
            return configurationExcludeRules;
        }

        public Set<ComponentArtifactMetaData> getArtifacts() {
            return DefaultLocalComponentMetaData.getArtifacts(getHierarchy(), allArtifacts);
        }

        public ComponentArtifactMetaData artifact(IvyArtifactName ivyArtifactName) {
            for (ComponentArtifactMetaData candidate : getArtifacts()) {
                if (candidate.getName().equals(ivyArtifactName)) {
                    return candidate;
                }
            }

            return new MissingLocalArtifactMetaData(componentIdentifier, id.toString(), ivyArtifactName);
        }
    }

    static Set<ComponentArtifactMetaData> getArtifacts(Set<String> configurationHierarchy, Multimap<String, ComponentArtifactMetaData> allArtifacts) {
        Set<ComponentArtifactMetaData> artifacts = Sets.newLinkedHashSet();
        for (String config : configurationHierarchy) {
            artifacts.addAll(allArtifacts.get(config));
        }
        return artifacts;
    }
}
