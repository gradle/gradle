/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.artifacts.transform.VariantDefinition;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.model.ComponentArtifactResolveVariantState;

/**
 * An {@link ArtifactSet} which selects from {@link ResolvedVariantSet}s.
 */
public class ResolvedVariantArtifactSet implements ArtifactSet {

    private final ComponentIdentifier componentIdentifier;
    private final ComponentArtifactResolveVariantState allVariants;
    private final ResolvedVariantSet legacyVariants;

    public ResolvedVariantArtifactSet(ComponentIdentifier componentIdentifier, ComponentArtifactResolveVariantState allVariants, ResolvedVariantSet legacyVariants) {
        this.componentIdentifier = componentIdentifier;
        this.allVariants = allVariants;
        this.legacyVariants = legacyVariants;
    }

    @Override
    public ResolvedArtifactSet select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector, boolean selectFromAllVariants) {
        if (!componentFilter.isSatisfiedBy(componentIdentifier)) {
            return ResolvedArtifactSet.EMPTY;
        } else {
            ResolvedVariantSet variants = selectFromAllVariants ? allVariants.getAllVariants() : legacyVariants;
            return selector.select(variants, new VariantTransformer(componentIdentifier));
        }
    }

    private static class VariantTransformer implements VariantSelector.Factory {
        private final ComponentIdentifier componentIdentifier;

        public VariantTransformer(ComponentIdentifier componentIdentifier) {
            this.componentIdentifier = componentIdentifier;
        }

        @Override
        public ResolvedArtifactSet asTransformed(
            ResolvedVariant sourceVariant,
            VariantDefinition variantDefinition,
            TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory,
            TransformedVariantFactory transformedVariantFactory
        ) {
            if (componentIdentifier instanceof ProjectComponentIdentifier) {
                return transformedVariantFactory.transformedProjectArtifacts(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolverFactory);
            } else {
                return transformedVariantFactory.transformedExternalArtifacts(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolverFactory);
            }
        }
    }
}
