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
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadataRules;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantMetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * This should be made immutable. It is currently effectively immutable. Should also be specialized for Maven, Ivy and Gradle metadata as much of this state is required only for Ivy.
 */
abstract class DefaultConfigurationMetadata implements ConfigurationMetadata {
    private final ModuleComponentIdentifier componentId;
    private final String name;
    private final ImmutableList<? extends DefaultConfigurationMetadata> parents;
    private final List<ModuleDependencyMetadata> configDependencies = Lists.newArrayList();
    private final ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts;
    private final boolean transitive;
    private final boolean visible;
    private final List<String> hierarchy;

    private DependencyMetadataRules dependencyMetadataRules;
    private List<ModuleDependencyMetadata> calculatedDependencies;

    DefaultConfigurationMetadata(ModuleComponentIdentifier componentId, String name, boolean transitive, boolean visible, ImmutableList<? extends DefaultConfigurationMetadata> parents, ImmutableList<? extends ModuleComponentArtifactMetadata> artifacts) {
        this.componentId = componentId;
        this.name = name;
        this.parents = parents;
        this.transitive = transitive;
        this.visible = visible;
        this.artifacts = artifacts;
        this.hierarchy = calculateHierarchy();
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

    public ImmutableList<? extends DefaultConfigurationMetadata> getParents() {
        return parents;
    }

    @Override
    public Collection<String> getHierarchy() {
        return hierarchy;
    }

    private List<String> calculateHierarchy() {
        List<? extends DefaultConfigurationMetadata> parents = getParents();
        if (parents.isEmpty()) {
            return Collections.singletonList(name);
        }
        Set<String> hierarchy = new LinkedHashSet<String>(1 + parents.size());
        populateHierarchy(hierarchy);
        return ImmutableList.copyOf(hierarchy);
    }

    private void populateHierarchy(Set<String> accumulator) {
            accumulator.add(name);
        for (DefaultConfigurationMetadata parent : getParents()) {
            parent.populateHierarchy(accumulator);
        }
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
        return ImmutableAttributes.EMPTY;
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
            if (dependencyMetadataRules == null) {
                calculatedDependencies = configDependencies;
            } else {
                calculatedDependencies = dependencyMetadataRules.execute(configDependencies);
            }
        }
        return calculatedDependencies;
    }

    void populateDependencies(Iterable<? extends ModuleDependencyMetadata> dependencies, DependencyMetadataRules dependencyMetadataRules) {
        for (ModuleDependencyMetadata dependency : dependencies) {
            if (include(dependency)) {
                this.configDependencies.add(dependency);
            }
        }
        this.calculatedDependencies = null;
        this.dependencyMetadataRules = dependencyMetadataRules;
    }

    private boolean include(DependencyMetadata dependency) {
        Collection<String> hierarchy = getHierarchy();
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

    @Override
    public List<? extends ModuleComponentArtifactMetadata> getArtifacts() {
        return artifacts;
    }

    @Override
    public Set<? extends VariantMetadata> getVariants() {
        return ImmutableSet.of(new DefaultVariantMetadata(asDescribable(), getAttributes(), getArtifacts()));
    }

    @Override
    public ModuleComponentArtifactMetadata artifact(IvyArtifactName artifact) {
        return new DefaultModuleComponentArtifactMetadata(componentId, artifact);
    }
}
