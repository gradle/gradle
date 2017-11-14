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
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependenciesMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadataRules;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.component.model.ComponentResolveMetadata.DEFAULT_STATUS_SCHEME;

abstract class AbstractMutableModuleComponentResolveMetadata<T extends DefaultConfigurationMetadata> implements MutableModuleComponentResolveMetadata {
    public static final HashValue EMPTY_CONTENT = HashUtil.createHash("", "MD5");
    private ModuleComponentIdentifier componentId;
    private ModuleVersionIdentifier id;
    private boolean changing;
    private boolean missing;
    private String status = "integration";
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;
    private ModuleSource moduleSource;
    private List<? extends ModuleDependencyMetadata> dependencies;
    private HashValue contentHash = EMPTY_CONTENT;
    @Nullable
    private ImmutableList<? extends ModuleComponentArtifactMetadata> artifactOverrides;
    private ImmutableMap<String, T> configurations;

    protected final Map<String, DependencyMetadataRules> dependencyMetadataRules = Maps.newHashMap();

    protected AbstractMutableModuleComponentResolveMetadata(ModuleVersionIdentifier id, ModuleComponentIdentifier componentIdentifier, List<? extends ModuleDependencyMetadata> dependencies) {
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

    @Override
    public ImmutableMap<String, T> getConfigurations() {
        if (configurations == null) {
            configurations = populateConfigurationsFromDescriptor(getConfigurationDefinitions());
        }
        return configurations;
    }

    /**
     * Called when some input to the configurations of this component has changed and the configurations should be recalculated
     */
    protected void resetConfigurations() {
        configurations = null;
    }

    private ImmutableMap<String, T> populateConfigurationsFromDescriptor(Map<String, Configuration> configurationDefinitions) {
        Set<String> configurationsNames = configurationDefinitions.keySet();
        Map<String, T> configurations = new HashMap<String, T>(configurationsNames.size());
        for (String configName : configurationsNames) {
            DefaultConfigurationMetadata configuration = populateConfigurationFromDescriptor(configName, configurationDefinitions, configurations);
            configuration.populateDependencies(dependencies, dependencyMetadataRules.get(configName));
        }
        return ImmutableMap.copyOf(configurations);
    }

    private T populateConfigurationFromDescriptor(String name, Map<String, Configuration> configurationDefinitions, Map<String, T> configurations) {
        T populated = configurations.get(name);
        if (populated != null) {
            return populated;
        }

        Configuration descriptorConfiguration = configurationDefinitions.get(name);
        List<String> extendsFrom = descriptorConfiguration.getExtendsFrom();
        boolean transitive = descriptorConfiguration.isTransitive();
        boolean visible = descriptorConfiguration.isVisible();
        if (extendsFrom.isEmpty()) {
            // tail
            populated = createConfiguration(componentId, name, transitive, visible, ImmutableList.<T>of(), artifactOverrides);
            configurations.put(name, populated);
            return populated;
        } else if (extendsFrom.size() == 1) {
            populated = createConfiguration(componentId, name, transitive, visible, ImmutableList.of(populateConfigurationFromDescriptor(extendsFrom.get(0), configurationDefinitions, configurations)), artifactOverrides);
            configurations.put(name, populated);
            return populated;
        }
        List<T> hierarchy = new ArrayList<T>(extendsFrom.size());
        for (String confName : extendsFrom) {
            hierarchy.add(populateConfigurationFromDescriptor(confName, configurationDefinitions, configurations));
        }
        populated = createConfiguration(componentId, name, transitive, visible, ImmutableList.copyOf(hierarchy), artifactOverrides);

        configurations.put(name, populated);
        return populated;
    }

    /**
     * Creates a {@link org.gradle.internal.component.model.ConfigurationMetadata} implementation for this component.
     */
    protected abstract T createConfiguration(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<T> parents, ImmutableList<? extends ModuleComponentArtifactMetadata> artifactOverrides);

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

    @Override
    public void addDependencyMetadataRule(String variantName, Action<DependenciesMetadata> action,
                                          Instantiator instantiator, NotationParser<Object, org.gradle.api.artifacts.DependencyMetadata> dependencyNotationParser) {
        DependencyMetadataRules rulesForVariant = dependencyMetadataRules.get(variantName);
        if (rulesForVariant == null) {
            dependencyMetadataRules.put(variantName, new DependencyMetadataRules(instantiator, dependencyNotationParser));
        }
        dependencyMetadataRules.get(variantName).addAction(action);
        resetConfigurations();
    }

    @Nullable
    @Override
    public ImmutableList<? extends ModuleComponentArtifactMetadata> getArtifactOverrides() {
        return artifactOverrides;
    }

    @Override
    public void setArtifactOverrides(Iterable<? extends ModuleComponentArtifactMetadata> artifacts) {
        this.artifactOverrides = ImmutableList.copyOf(artifacts);
        resetConfigurations();
    }

    @Override
    public List<? extends ModuleDependencyMetadata> getDependencies() {
        return dependencies;
    }

    @Override
    public void setDependencies(Iterable<? extends ModuleDependencyMetadata> dependencies) {
        this.dependencies = ImmutableList.copyOf(dependencies);
        resetConfigurations();
    }
}
