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
import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.EmptySchema;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.hash.HashValue;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

abstract class AbstractModuleComponentResolveMetadata<T extends DefaultConfigurationMetadata> implements ModuleComponentResolveMetadata {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final ModuleComponentIdentifier componentIdentifier;
    private final boolean changing;
    private final boolean missing;
    private final String status;
    private final List<String> statusScheme;
    @Nullable
    private final ModuleSource moduleSource;
    private final ImmutableMap<String, T> configurations;
    private final List<? extends ModuleDependencyMetadata> dependencies;
    private final HashValue contentHash;

    protected AbstractModuleComponentResolveMetadata(AbstractMutableModuleComponentResolveMetadata<T> metadata) {
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.isChanging();
        missing = metadata.isMissing();
        status = metadata.getStatus();
        statusScheme = metadata.getStatusScheme();
        moduleSource = metadata.getSource();
        dependencies = metadata.getDependencies();
        configurations = metadata.getConfigurations();
        contentHash = metadata.getContentHash();
    }

    /**
     * Creates a copy of the given metadata
     */
    protected AbstractModuleComponentResolveMetadata(AbstractModuleComponentResolveMetadata<T> metadata, @Nullable ModuleSource source) {
        this.componentIdentifier = metadata.getComponentId();
        this.moduleVersionIdentifier = metadata.getId();
        changing = metadata.isChanging();
        missing = metadata.isMissing();
        status = metadata.getStatus();
        statusScheme = metadata.getStatusScheme();
        moduleSource = source;
        dependencies = metadata.getDependencies();
        configurations = metadata.getConfigurations();
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
    public ImmutableList<? extends ConfigurationMetadata> getVariantsForGraphTraversal() {
        return ImmutableList.of();
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
    public List<? extends ModuleDependencyMetadata> getDependencies() {
        return dependencies;
    }

    @Override
    public ImmutableMap<String, T> getConfigurations() {
        return configurations;
    }

    @Override
    public ConfigurationMetadata getConfiguration(final String name) {
        return configurations.get(name);
    }
}
