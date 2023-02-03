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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.List;

/**
 * An immutable {@link ConfigurationMetadata} wrapper around a {@link ComponentVariant}.
 */
class LazyVariantBackedConfigurationMetadata extends AbstractVariantBackedConfigurationMetadata {
    private final VariantMetadataRules variantMetadataRules;

    private List<? extends ModuleDependencyMetadata> calculatedDependencies;

    LazyVariantBackedConfigurationMetadata(ModuleComponentIdentifier componentId, ComponentVariant variant, ImmutableAttributes componentLevelAttributes, ImmutableAttributesFactory attributesFactory, VariantMetadataRules variantMetadataRules) {
        super(componentId, new RuleAwareVariant(componentId, variant, attributesFactory, componentLevelAttributes, variantMetadataRules));
        this.variantMetadataRules = variantMetadataRules;
    }

    @Override
    public List<? extends ModuleDependencyMetadata> getDependencies() {
        if (calculatedDependencies == null) {
            calculatedDependencies = variantMetadataRules.applyDependencyMetadataRules(getVariant(), super.getDependencies());
        }
        return calculatedDependencies;
    }

    /**
     * This class wraps the component variant so that attribute rules are executed once
     * for all, and passed correctly to the various consumers. In particular, we need to make sure
     * that the attributes are the same whenever we resolve the graph for dependencies and artifacts.
     */
    private static class RuleAwareVariant implements ComponentVariant {
        private final ModuleComponentIdentifier componentId;
        private final ImmutableAttributesFactory attributesFactory;
        private final ComponentVariant delegate;
        private final ImmutableAttributes componentLevelAttributes;
        private final VariantMetadataRules variantMetadataRules;

        private ImmutableAttributes computedAttributes;
        private CapabilitiesMetadata computedCapabilities;
        private ImmutableList<? extends ComponentArtifactMetadata> computedArtifacts;

        RuleAwareVariant(ModuleComponentIdentifier componentId, ComponentVariant delegate, ImmutableAttributesFactory attributesFactory, ImmutableAttributes componentLevelAttributes, VariantMetadataRules variantMetadataRules) {
            this.componentId = componentId;
            this.attributesFactory = attributesFactory;
            this.delegate = delegate;
            this.componentLevelAttributes = componentLevelAttributes;
            this.variantMetadataRules = variantMetadataRules;
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Identifier getIdentifier() {
            return delegate.getIdentifier();
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
        public ImmutableList<? extends ComponentArtifactMetadata> getArtifacts() {
            if (computedArtifacts == null) {
                computedArtifacts = variantMetadataRules.applyVariantFilesMetadataRulesToArtifacts(delegate, delegate.getArtifacts(), componentId);
            }
            return computedArtifacts;
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

        @Override
        public CapabilitiesMetadata getCapabilities() {
            if (computedCapabilities == null) {
                computedCapabilities = variantMetadataRules.applyCapabilitiesRules(delegate, delegate.getCapabilities());
            }
            return computedCapabilities;
        }

        @Override
        public boolean isExternalVariant() {
            return delegate.isExternalVariant();
        }

        @Override
        public boolean isEligibleForCaching() {
            return delegate.isEligibleForCaching();
        }

        private AttributeContainerInternal mergeComponentAndVariantAttributes(AttributeContainerInternal variantAttributes) {
            return attributesFactory.concat(componentLevelAttributes, variantAttributes.asImmutable());
        }

    }
}
