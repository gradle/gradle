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
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantResolveMetadata;

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
    private final VariantMetadataRules variantMetadataRules;
    private final ImmutableAttributes componentLevelAttributes;

    private List<GradleDependencyMetadata> calculatedDependencies;
    private ImmutableAttributesFactory attributesFactory;

    VariantBackedConfigurationMetadata(ModuleComponentIdentifier componentId, ComponentVariant variant, ImmutableAttributes componentLevelAttributes, ImmutableAttributesFactory attributesFactory, VariantMetadataRules variantMetadataRules) {
        this.componentId = componentId;
        this.variant = new RuleAwareVariant(variant);
        this.attributesFactory = attributesFactory;
        this.componentLevelAttributes = componentLevelAttributes;
        this.variantMetadataRules = variantMetadataRules;
        List<GradleDependencyMetadata> dependencies = new ArrayList<GradleDependencyMetadata>(variant.getDependencies().size());
        for (ComponentVariant.Dependency dependency : variant.getDependencies()) {
            ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(dependency.getGroup(), dependency.getModule(), dependency.getVersionConstraint());
            List<ExcludeMetadata> excludes = dependency.getExcludes();
            dependencies.add(new GradleDependencyMetadata(selector, excludes));
        }
        for (ComponentVariant.DependencyConstraint dependencyConstraint : variant.getDependencyConstraints()) {
            dependencies.add(new GradleDependencyMetadata(DefaultModuleComponentSelector.newSelector(dependencyConstraint.getGroup(), dependencyConstraint.getModule(), dependencyConstraint.getVersionConstraint()), true));
        }
        this.dependencies = ImmutableList.copyOf(dependencies);
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
    public Set<? extends VariantResolveMetadata> getVariants() {
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
    public ImmutableList<ExcludeMetadata> getExcludes() {
        return ImmutableList.of();
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
            calculatedDependencies = variantMetadataRules.applyDependencyMetadataRules(variant, dependencies);
        }
        return calculatedDependencies;
    }

    private AttributeContainerInternal mergeComponentAndVariantAttributes(AttributeContainerInternal variantAttributes) {
        return attributesFactory.concat(componentLevelAttributes, variantAttributes.asImmutable());
    }

    /**
     * This class wraps the component variant so that attribute rules are executed once
     * for all, and passed correctly to the various consumers. In particular, we need to make sure
     * that the attributes are the same whenever we resolve the graph for dependencies and artifacts.
     */
    private class RuleAwareVariant implements ComponentVariant {
        private final ComponentVariant delegate;

        private ImmutableAttributes computedAttributes;

        private RuleAwareVariant(ComponentVariant delegate) {
            this.delegate = delegate;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public DisplayName asDescribable() {
            return delegate.asDescribable();
        }

        /**
         * Returns the complete set of attributes of this variant, which consists of a view of the union
         * of the component level attributes and the variant attributes as found in metadata, potentially
         * modified by rules.
         *
         * @return the updated variant attributes
         */
        @Override
        public ImmutableAttributes getAttributes() {
            if (computedAttributes == null) {
                computedAttributes = variantMetadataRules.applyVariantAttributeRules(delegate, mergeComponentAndVariantAttributes(delegate.getAttributes()));
            }
            return computedAttributes;
        }

        @Override
        public List<? extends ComponentArtifactMetadata> getArtifacts() {
            return delegate.getArtifacts();
        }

        @Override
        public ImmutableList<? extends Dependency> getDependencies() {
            return delegate.getDependencies();
        }

        @Override
        public ImmutableList<? extends DependencyConstraint> getDependencyConstraints() {
            return delegate.getDependencyConstraints();
        }

        @Override
        public ImmutableList<? extends File> getFiles() {
            return delegate.getFiles();
        }
    }
}
