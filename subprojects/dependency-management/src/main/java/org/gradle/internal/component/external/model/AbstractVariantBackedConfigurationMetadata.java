/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.ModuleConfigurationMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * An immutable {@link ConfigurationMetadata} wrapper around a {@link ComponentVariant}.
 */
class AbstractVariantBackedConfigurationMetadata implements ModuleConfigurationMetadata {
    private final ModuleComponentIdentifier componentId;
    private final ComponentVariant variant;
    private final List<? extends ModuleDependencyMetadata> dependencies;

    AbstractVariantBackedConfigurationMetadata(ModuleComponentIdentifier componentId, ComponentVariant variant) {
        this.componentId = componentId;
        this.variant = variant;
        List<GradleDependencyMetadata> dependencies = new ArrayList<>(variant.getDependencies().size());
        // Forced dependencies are only supported for enforced platforms, so it is currently hardcoded.
        // Should we want to add this as a first class concept to Gradle metadata, then it should be available on the component variant
        // metadata as well.
        boolean forcedDependencies = PlatformSupport.hasForcedDependencies(variant);
        for (ComponentVariant.Dependency dependency : variant.getDependencies()) {
            ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dependency.getGroup(), dependency.getModule()), dependency.getVersionConstraint(), dependency.getAttributes(), dependency.getRequestedCapabilities());
            List<ExcludeMetadata> excludes = dependency.getExcludes();
            IvyArtifactName dependencyArtifact = dependency.getDependencyArtifact();
            dependencies.add(new GradleDependencyMetadata(selector, excludes, false, dependency.isEndorsingStrictVersions(), dependency.getReason(), forcedDependencies, dependencyArtifact));
        }
        for (ComponentVariant.DependencyConstraint dependencyConstraint : variant.getDependencyConstraints()) {
            dependencies.add(new GradleDependencyMetadata(
                DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(dependencyConstraint.getGroup(), dependencyConstraint.getModule()), dependencyConstraint.getVersionConstraint(), dependencyConstraint.getAttributes(), ImmutableList.of()),
                Collections.emptyList(),
                true,
                false,
                dependencyConstraint.getReason(),
                forcedDependencies,
                null
            ));
        }
        this.dependencies = ImmutableList.copyOf(dependencies);
    }

    AbstractVariantBackedConfigurationMetadata(ModuleComponentIdentifier componentId, ComponentVariant variant, List<? extends ModuleDependencyMetadata> dependencies) {
        this.componentId = componentId;
        this.variant = variant;
        this.dependencies = dependencies;
    }

    @Override
    public String getName() {
        return variant.getName();
    }

    @Override
    public Identifier getIdentifier() {
        return variant.getIdentifier();
    }

    @Override
    public ImmutableSet<String> getHierarchy() {
        return ImmutableSet.of(variant.getName());
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
    public Set<? extends VariantResolveMetadata> getVariants() {
        return ImmutableSet.of(variant);
    }

    @Override
    public boolean isCanBeConsumed() {
        return true;
    }

    @Override
    public DeprecationMessageBuilder.WithDocumentation getConsumptionDeprecation() {
        return null;
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
    public ImmutableList<ExcludeMetadata> getExcludes() {
        return ImmutableList.of();
    }

    @Override
    public ComponentArtifactMetadata artifact(IvyArtifactName artifact) {
        return new DefaultModuleComponentArtifactMetadata(componentId, artifact);
    }

    @Override
    public CapabilitiesMetadata getCapabilities() {
        return variant.getCapabilities();
    }

    @Override
    public ImmutableList<? extends ComponentArtifactMetadata> getArtifacts() {
        return variant.getArtifacts();
    }

    @Override
    public List<? extends ModuleDependencyMetadata> getDependencies() {
        return dependencies;
    }

    protected ComponentVariant getVariant() {
        return variant;
    }

    @Override
    public boolean isExternalVariant() {
        return variant.isExternalVariant();
    }

    @Override
    public boolean isEligibleForCaching() {
        return variant.isEligibleForCaching();
    }
}
