/*
 * Copyright 2017 the original author or authors.
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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Effectively immutable implementation of ConfigurationMetadata.
 * Used to represent Ivy and Maven modules in the dependency graph.
 */
public class DefaultConfigurationMetadata implements ConfigurationMetadata, VariantResolveMetadata {
    private final ModuleComponentIdentifier componentId;
    private final String name;
    private final ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts;
    private final boolean transitive;
    private final boolean visible;
    private final ImmutableList<String> hierarchy;
    private final VariantMetadataRules componentMetadataRules;
    private final ImmutableList<ExcludeMetadata> excludes;
    private final ImmutableAttributes attributes;

    // Should be final, and set in constructor
    private ImmutableList<ModuleDependencyMetadata> configDependencies;
    private List<ModuleDependencyMetadata> calculatedDependencies;

    // Could be precomputed, but we avoid doing so if attributes are never requested
    private ImmutableAttributes computedAttributes;

    protected DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible,
                                           ImmutableList<String> hierarchy, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
                                           VariantMetadataRules componentMetadataRules,
                                           ImmutableList<ExcludeMetadata> excludes) {
        this(componentId, name, transitive, visible, hierarchy, artifacts, componentMetadataRules, excludes, ImmutableAttributes.EMPTY, null);
    }

    private DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible,
                                         ImmutableList<String> hierarchy, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts,
                                         VariantMetadataRules componentMetadataRules,
                                         ImmutableList<ExcludeMetadata> excludes,
                                         ImmutableAttributes attributes,
                                         ImmutableList<ModuleDependencyMetadata> configDependencies) {
        this.componentId = componentId;
        this.name = name;
        this.transitive = transitive;
        this.visible = visible;
        this.artifacts = artifacts;
        this.hierarchy = hierarchy;
        this.componentMetadataRules = componentMetadataRules;
        this.excludes = excludes;
        this.attributes = attributes;
        this.configDependencies = configDependencies;
    }

    @Override
    public DisplayName asDescribable() {
        return Describables.of(componentId, "configuration", name);
    }

    @Override
    public String toString() {
        return asDescribable().getDisplayName();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<String> getHierarchy() {
        return hierarchy;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        if (computedAttributes == null) {
            computedAttributes = componentMetadataRules.applyVariantAttributeRules(this, attributes);
        }
        return computedAttributes;
    }

    @Override
    public boolean isCanBeConsumed() {
        return true;
    }

    @Override
    public boolean isCanBeResolved() {
        return false;
    }

    @Override
    public List<? extends DependencyMetadata> getDependencies() {
        if (calculatedDependencies == null) {
            calculatedDependencies = componentMetadataRules.applyDependencyMetadataRules(this, configDependencies);
        }
        return calculatedDependencies;
    }

    protected void setDependencies(List<ModuleDependencyMetadata> dependencies) {
        assert this.configDependencies == null; // Can only set once: should really be part of the constructor
        this.configDependencies = ImmutableList.copyOf(dependencies);
    }

    @Override
    public List<? extends ModuleComponentArtifactMetadata> getArtifacts() {
        return artifacts;
    }

    @Override
    public Set<? extends VariantResolveMetadata> getVariants() {
        return ImmutableSet.of(new DefaultVariantMetadata(asDescribable(), getAttributes(), getArtifacts()));
    }

    @Override
    public ImmutableList<ExcludeMetadata> getExcludes() {
        return excludes;
    }

    @Override
    public ModuleComponentArtifactMetadata artifact(IvyArtifactName artifact) {
        return new DefaultModuleComponentArtifactMetadata(componentId, artifact);
    }

    protected DefaultConfigurationMetadata withAttributes(ImmutableAttributes attributes) {
        return new DefaultConfigurationMetadata(componentId, name, transitive, visible, hierarchy, artifacts, componentMetadataRules, excludes, attributes, configDependencies);
    }

}
