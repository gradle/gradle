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

import org.gradle.api.Describable;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.artifacts.transform.VariantDefinition;
import org.gradle.api.internal.artifacts.transform.VariantSelector;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.ComponentArtifactResolveVariantState;

import java.util.Set;

/**
 * Contains zero or more variants of a particular component.
 */
public class DefaultArtifactSet implements ArtifactSet, ResolvedVariantSet, VariantSelector.Factory {
    private final ComponentIdentifier componentIdentifier;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributes selectionAttributes;
    private final ComponentArtifactResolveVariantState allVariants;
    private final Set<ResolvedVariant> legacyVariants;

    DefaultArtifactSet(ComponentIdentifier componentIdentifier, AttributesSchemaInternal schema, ImmutableAttributes selectionAttributes, ComponentArtifactResolveVariantState allVariants, Set<ResolvedVariant> legacyVariants) {
        this.componentIdentifier = componentIdentifier;
        this.schema = schema;
        this.selectionAttributes = selectionAttributes;
        this.allVariants = allVariants;
        this.legacyVariants = legacyVariants;
    }

    @Override
    public ImmutableAttributes getOverriddenAttributes() {
        return selectionAttributes;
    }

    @Override
    public String toString() {
        return asDescribable().getDisplayName();
    }

    @Override
    public Describable asDescribable() {
        return Describables.of(componentIdentifier);
    }

    @Override
    public AttributesSchemaInternal getSchema() {
        return schema;
    }

    @Override
    public ResolvedArtifactSet asTransformed(ResolvedVariant sourceVariant, VariantDefinition variantDefinition, ExtraExecutionGraphDependenciesResolverFactory dependenciesResolver, TransformedVariantFactory transformedVariantFactory) {
        if (componentIdentifier instanceof ProjectComponentIdentifier) {
            return transformedVariantFactory.transformedProjectArtifacts(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver);
        } else {
            return transformedVariantFactory.transformedExternalArtifacts(componentIdentifier, sourceVariant, variantDefinition, dependenciesResolver);
        }
    }

    @Override
    public ResolvedArtifactSet select(Spec<? super ComponentIdentifier> componentFilter, VariantSelector selector) {
        if (!componentFilter.isSatisfiedBy(componentIdentifier)) {
            return ResolvedArtifactSet.EMPTY;
        } else {
            return selector.select(this, this);
        }
    }

    @Override
    public Set<ResolvedVariant> getVariants() {
        return legacyVariants;
    }

    @Override
    public Set<ResolvedVariant> getAllVariants() {
        return allVariants.getAllVariants();
    }
}
