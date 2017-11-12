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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadataRules;
import org.gradle.internal.component.model.GradleDependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * An immutable {@link ConfigurationMetadata} wrapper around a {@link ComponentVariant}.
 */
class VariantBackedConfigurationMetadata implements ConfigurationMetadata {
    private final ModuleComponentIdentifier componentId;
    private final ComponentVariant variant;
    private final ImmutableList<GradleDependencyMetadata> dependencies;
    private final DependencyMetadataRules dependencyMetadataRules;

    private List<GradleDependencyMetadata> calculatedDependencies;

    VariantBackedConfigurationMetadata(ModuleComponentIdentifier componentId, ComponentVariant variant, DependencyMetadataRules dependencyMetadataRules) {
        this.componentId = componentId;
        this.variant = variant;
        List<GradleDependencyMetadata> dependencies = new ArrayList<GradleDependencyMetadata>(variant.getDependencies().size());
        for (ComponentVariant.Dependency dependency : variant.getDependencies()) {
            dependencies.add(new GradleDependencyMetadata(DefaultModuleComponentSelector.newSelector(dependency.getGroup(), dependency.getModule(), dependency.getVersionConstraint())));
        }
        this.dependencies = ImmutableList.copyOf(dependencies);
        this.dependencyMetadataRules = dependencyMetadataRules;
    }

    @Override
    public String getName() {
        return variant.getName();
    }

    @Override
    public Collection<String> getHierarchy() {
        return ImmutableList.of(variant.getName());
    }

    @Override
    public String toString() {
        return asDescribable().getDisplayName();
    }

    @Override
    public DisplayName asDescribable() {
        return Describables.of(componentId, "variant", variant.getName());
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return variant.getAttributes().asImmutable();
    }

    @Override
    public Set<? extends VariantMetadata> getVariants() {
        return ImmutableSet.of(variant);
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
    public boolean isTransitive() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public ModuleExclusion getExclusions(ModuleExclusions moduleExclusions) {
        return ModuleExclusions.excludeNone();
    }

    @Override
    public ComponentArtifactMetadata artifact(IvyArtifactName artifact) {
        return new DefaultModuleComponentArtifactMetadata(componentId, artifact);
    }

    @Override
    public List<? extends ComponentArtifactMetadata> getArtifacts() {
        return ImmutableList.of();
    }

    @Override
    public List<? extends DependencyMetadata> getDependencies() {
        if (calculatedDependencies == null) {
            if (dependencyMetadataRules == null) {
                calculatedDependencies = dependencies;
            } else {
                calculatedDependencies = dependencyMetadataRules.execute(dependencies);
            }
        }
        return calculatedDependencies;
    }
}
