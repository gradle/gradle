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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.ComponentArtifactMetadata;

public class RealisedVariantBackedConfigurationMetadata extends AbstractVariantBackedConfigurationMetadata {

    public RealisedVariantBackedConfigurationMetadata(ModuleComponentIdentifier id, ComponentVariant variant, ImmutableAttributes componentLevelAttributes, ImmutableAttributesFactory attributesFactory) {
        super(id, new ComponentAttributesAwareVariant(variant, attributesFactory, componentLevelAttributes), ((AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl)variant).getDependencyMetadata());
    }

    /**
     * This class wraps the component variant so that the set of attributes returned by ${@link #getAttributes()} includes both
     * the variant and the component attributes.
     * See also ${@link LazyVariantBackedConfigurationMetadata}.RuleAwareVariant which serves a similar purpose.
     */
    private static class ComponentAttributesAwareVariant implements ComponentVariant {
        private final ImmutableAttributesFactory attributesFactory;
        private final ComponentVariant delegate;
        private final ImmutableAttributes componentLevelAttributes;

        private ImmutableAttributes computedAttributes;

        ComponentAttributesAwareVariant(ComponentVariant delegate, ImmutableAttributesFactory attributesFactory, ImmutableAttributes componentLevelAttributes) {
            this.attributesFactory = attributesFactory;
            this.delegate = delegate;
            this.componentLevelAttributes = componentLevelAttributes;
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
         * of the component level attributes and the variant attributes as found in metadata.
         *
         * @return the updated variant attributes
         */
        @Override
        public ImmutableAttributes getAttributes() {
            if (computedAttributes == null) {
                computedAttributes = mergeComponentAndVariantAttributes(delegate.getAttributes());
            }
            return computedAttributes;
        }

        @Override
        public ImmutableList<? extends ComponentArtifactMetadata> getArtifacts() {
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

        @Override
        public CapabilitiesMetadata getCapabilities() {
            return delegate.getCapabilities();
        }

        @Override
        public boolean isExternalVariant() {
            return delegate.isExternalVariant();
        }

        @Override
        public boolean isEligibleForCaching() {
            return delegate.isEligibleForCaching();
        }

        private ImmutableAttributes mergeComponentAndVariantAttributes(AttributeContainerInternal variantAttributes) {
            return attributesFactory.concat(componentLevelAttributes, variantAttributes.asImmutable());
        }
    }
}
