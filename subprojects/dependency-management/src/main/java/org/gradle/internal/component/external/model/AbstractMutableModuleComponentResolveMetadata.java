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
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.internal.component.external.descriptor.Configuration;
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;

import java.util.List;
import java.util.Map;

import static org.gradle.internal.component.model.ComponentResolveMetadata.DEFAULT_STATUS_SCHEME;

abstract class AbstractMutableModuleComponentResolveMetadata implements MutableModuleComponentResolveMetadata {
    private final ModuleDescriptorState descriptor;
    private ModuleComponentIdentifier componentId;
    private ModuleVersionIdentifier id;
    private boolean changing;
    private String status;
    private List<String> statusScheme = DEFAULT_STATUS_SCHEME;
    private ModuleSource moduleSource;
    private List<? extends DependencyMetadata> dependencies;
    private Map<String, Configuration> configurationDefinitions;
    @Nullable
    private List<ModuleComponentArtifactMetadata> artifacts;

    protected AbstractMutableModuleComponentResolveMetadata(ModuleComponentIdentifier componentIdentifier, ModuleDescriptorState moduleDescriptor, Map<String, Configuration> configurations, List<? extends DependencyMetadata> dependencies) {
        this.descriptor = moduleDescriptor;
        this.componentId = componentIdentifier;
        this.id = DefaultModuleVersionIdentifier.newId(componentIdentifier);
        this.status = moduleDescriptor.getStatus();
        this.dependencies = dependencies;
        this.configurationDefinitions = configurations;
    }

    protected AbstractMutableModuleComponentResolveMetadata(ModuleComponentResolveMetadata metadata) {
        this.descriptor = metadata.getDescriptor();
        this.componentId = metadata.getComponentId();
        this.id = metadata.getId();
        this.changing = metadata.isChanging();
        this.status = metadata.getStatus();
        this.statusScheme = metadata.getStatusScheme();
        this.moduleSource = metadata.getSource();
        this.configurationDefinitions = metadata.getConfigurationDefinitions();
        this.artifacts = metadata.getArtifacts();
        this.dependencies = metadata.getDependencies();
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
    public ModuleDescriptorState getDescriptor() {
        return descriptor;
    }

    @Override
    public String getStatus() {
        return status;
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
    public boolean isChanging() {
        return changing;
    }

    @Override
    public void setChanging(boolean changing) {
        this.changing = changing;
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
    public Map<String, Configuration> getConfigurationDefinitions() {
        return configurationDefinitions;
    }

    @Override
    public ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier) {
        IvyArtifactName ivyArtifactName = new DefaultIvyArtifactName(getId().getName(), type, extension, classifier);
        return new DefaultModuleComponentArtifactMetadata(getComponentId(), ivyArtifactName);
    }

    @Nullable
    @Override
    public List<ModuleComponentArtifactMetadata> getArtifacts() {
        return artifacts;
    }

    @Override
    public void setArtifacts(Iterable<? extends ModuleComponentArtifactMetadata> artifacts) {
        this.artifacts = ImmutableList.copyOf(artifacts);
    }

    @Override
    public List<? extends DependencyMetadata> getDependencies() {
        return dependencies;
    }

    @Override
    public void setDependencies(Iterable<? extends DependencyMetadata> dependencies) {
        this.dependencies = ImmutableList.copyOf(dependencies);
    }
}
