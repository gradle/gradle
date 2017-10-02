/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.component.model.ComponentResolveMetadata.DEFAULT_STATUS_SCHEME;

abstract class AbstractMutableModuleComponentResolveMetadata implements MutableModuleComponentResolveMetadata {
    public static final HashValue EMPTY_CONTENT = HashUtil.createHash("", "MD5");
    private ModuleComponentIdentifier componentId;
    private ModuleVersionIdentifier id;
    private boolean changing;
    private boolean missing;
    private String status = "integration";
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;
    private ModuleSource moduleSource;
    private List<? extends DependencyMetadata> dependencies;
    private HashValue contentHash = EMPTY_CONTENT;
    @Nullable
    private List<? extends ModuleComponentArtifactMetadata> artifactOverrides;
    private ImmutableMap<String, DefaultConfigurationMetadata> configurations;

    protected AbstractMutableModuleComponentResolveMetadata(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier, List<? extends DependencyMetadata> dependencies) {
        this.componentId = componentIdentifier;
        this.id = id;
        this.dependencies = dependencies;
    }

    protected AbstractMutableModuleComponentResolveMetadata(ModuleComponentResolveMetadata metadata) {
        this.componentId = metadata.getComponentId();
        this.id = metadata.getId();
        this.changing = metadata.isChanging();
        this.missing = metadata.isMissing();
        this.status = metadata.getStatus();
        this.statusScheme = metadata.getStatusScheme();
        this.moduleSource = metadata.getSource();
        this.artifactOverrides = metadata.getArtifactOverrides();
        this.dependencies = metadata.getDependencies();
        this.contentHash = metadata.getContentHash();
    }

    @Override
    public ModuleComponentIdentifier getComponentId() {
        return componentId;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return id;
    }

    @Override
    public void setComponentId(ModuleComponentIdentifier componentId) {
        this.componentId = componentId;
        this.id = DefaultModuleVersionIdentifier.newId(componentId);
    }

    @Override
    public String getStatus() {
        return status;
    }

    protected abstract Map<String, Configuration> getConfigurationDefinitions();

    protected abstract List<Exclude> getExcludes();

    protected abstract List<Artifact> getArtifacts();

    @Override
    public ImmutableMap<String, ? extends ConfigurationMetadata> getConfigurations() {
        if (configurations == null) {
            configurations = populateConfigurationsFromDescriptor(getConfigurationDefinitions(), getExcludes());
            if (artifactOverrides != null) {
                populateArtifactsFromOverrides(artifactOverrides);
            } else {
                populateArtifacts(getArtifacts());
            }
        }
        return configurations;
    }

    /**
     * Called when some input to the configurations of this component has changed and the configurations should be recalculated
     */
    protected void resetConfigurations() {
        configurations = null;
    }

    private void populateArtifactsFromOverrides(List<? extends ModuleComponentArtifactMetadata> artifacts) {
        for (DefaultConfigurationMetadata configuration : configurations.values()) {
            configuration.addArtifacts(artifacts);
        }
    }

    private void populateArtifacts(Iterable<Artifact> artifacts) {
        for (Artifact artifact : artifacts) {
            ModuleComponentArtifactMetadata artifactMetadata = new DefaultModuleComponentArtifactMetadata(componentId, artifact.getArtifactName());
            for (String configuration : artifact.getConfigurations()) {
                configurations.get(configuration).addArtifact(artifactMetadata);
            }
        }
        Set<ConfigurationMetadata> visited = new HashSet<ConfigurationMetadata>();
        for (DefaultConfigurationMetadata configuration : configurations.values()) {
            configuration.collectInheritedArtifacts(visited);
        }
    }

    private ImmutableMap<String, DefaultConfigurationMetadata> populateConfigurationsFromDescriptor(Map<String, Configuration> configurationDefinitions, List<Exclude> excludes) {
        Set<String> configurationsNames = configurationDefinitions.keySet();
        Map<String, DefaultConfigurationMetadata> configurations = new HashMap<String, DefaultConfigurationMetadata>(configurationsNames.size());
        for (String configName : configurationsNames) {
            DefaultConfigurationMetadata configuration = populateConfigurationFromDescriptor(configName, configurationDefinitions, configurations, excludes);
            configuration.populateDependencies(dependencies);
        }
        return ImmutableMap.copyOf(configurations);
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
            populated = new DefaultConfigurationMetadata(componentId, name, transitive, visible, excludes);
            configurations.put(name, populated);
            return populated;
        } else if (extendsFrom.size() == 1) {
            populated = new DefaultConfigurationMetadata(
                componentId,
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
            componentId,
            name,
            transitive,
            visible,
            hierarchy,
            excludes
        );

        configurations.put(name, populated);
        return populated;
    }

    @Override
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public List<String> getStatusScheme() {
        return statusScheme;
    }

    @Override
    public void setStatusScheme(List<String> statusScheme) {
        this.statusScheme = statusScheme;
    }

    @Override
    public boolean isMissing() {
        return missing;
    }

    @Override
    public void setMissing(boolean missing) {
        this.missing = missing;
    }

    @Override
    public boolean isChanging() {
        return changing;
    }

    @Override
    public void setChanging(boolean changing) {
        this.changing = changing;
    }

    @Override
    public HashValue getContentHash() {
        return contentHash;
    }

    @Override
    public void setContentHash(HashValue contentHash) {
        this.contentHash = contentHash;
    }

    @Override
    public ModuleSource getSource() {
        return moduleSource;
    }

    @Override
    public void setSource(ModuleSource source) {
        this.moduleSource = source;
    }

    @Override
    public ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier) {
        IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(getId().getName(), type, extension, classifier);
        return new DefaultModuleComponentArtifactMetadata(getComponentId(), ivyArtifactName);
    }

    @Nullable
    @Override
    public List<? extends ModuleComponentArtifactMetadata> getArtifactOverrides() {
        return artifactOverrides;
    }

    @Override
    public void setArtifactOverrides(Iterable<? extends ModuleComponentArtifactMetadata> artifacts) {
        this.artifactOverrides = ImmutableList.copyOf(artifacts);
        resetConfigurations();
    }

    @Override
    public List<? extends DependencyMetadata> getDependencies() {
        return dependencies;
    }

    @Override
    public void setDependencies(Iterable<? extends DependencyMetadata> dependencies) {
        this.dependencies = ImmutableList.copyOf(dependencies);
        resetConfigurations();
    }
}
