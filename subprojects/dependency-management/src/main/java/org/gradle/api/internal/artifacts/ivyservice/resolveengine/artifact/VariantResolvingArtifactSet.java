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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.artifacts.transform.VariantDefinition;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveState;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.resolve.resolver.VariantArtifactResolver;

import java.util.List;
import java.util.function.Consumer;

/**
 * An {@link ArtifactSet} representing the artifacts contributed by a single variant in a dependency
 * graph, in the context of the dependency referencing it.
 */
public class VariantResolvingArtifactSet implements ArtifactSet, ArtifactVariantSelector.ResolvedArtifactTransformer {

    private final VariantArtifactResolver variantResolver;
    private final ComponentGraphResolveState component;
    private final VariantGraphResolveState variant;
    private final ComponentIdentifier componentId;
    private final AttributesSchemaInternal schema;
    private final ImmutableAttributes overriddenAttributes;
    private final List<IvyArtifactName> artifacts;
    private final ExcludeSpec exclusions;

    private final Lazy<ImmutableSet<ResolvedVariant>> ownArtifacts = Lazy.locking().of(this::calculateOwnArtifacts);

    public VariantResolvingArtifactSet(
        VariantArtifactResolver variantResolver,
        ComponentGraphResolveState component,
        VariantGraphResolveState variant,
        DependencyGraphEdge dependency
    ) {
        this.variantResolver = variantResolver;
        this.component = component;
        this.variant = variant;
        this.componentId = component.getId();
        this.schema = component.getMetadata().getAttributesSchema();
        this.overriddenAttributes = dependency.getAttributes();
        this.artifacts = dependency.getDependencyMetadata().getArtifacts();
        this.exclusions = dependency.getExclusions();
    }

    @Override
    public ResolvedArtifactSet select(Spec<? super ComponentIdentifier> componentFilter, ArtifactVariantSelector selector, boolean selectFromAllVariants) {
        if (!componentFilter.isSatisfiedBy(componentId)) {
            return ResolvedArtifactSet.EMPTY;
        } else {

            if (selectFromAllVariants && !artifacts.isEmpty()) {
                // Variants with overridden artifacts cannot be reselected since
                // we do not know the "true" attributes of the requested artifact.
                return ResolvedArtifactSet.EMPTY;
            }

            ResolvedVariantSet variants;
            try {
                variants = getVariants(selectFromAllVariants);
            } catch (Exception e) {
                return new BrokenResolvedArtifactSet(e);
            }
            return selector.select(variants, this);
        }
    }

    @Override
    public ResolvedArtifactSet asTransformed(ResolvedVariant sourceVariant, VariantDefinition variantDefinition, TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory, TransformedVariantFactory transformedVariantFactory) {
        if (componentId instanceof ProjectComponentIdentifier) {
            return transformedVariantFactory.transformedProjectArtifacts(componentId, sourceVariant, variantDefinition, dependenciesResolverFactory);
        } else {
            return transformedVariantFactory.transformedExternalArtifacts(componentId, sourceVariant, variantDefinition, dependenciesResolverFactory);
        }
    }

    public ResolvedVariantSet getVariants(boolean selectFromAllVariants) {
        ImmutableSet<ResolvedVariant> variants;
        if (!selectFromAllVariants) {
            variants = ownArtifacts.get();
        } else {
            variants = getComponentVariants();
        }

        return new DefaultResolvedVariantSet(
            componentId,
            schema,
            overriddenAttributes,
            variants
        );
    }

    public ImmutableSet<ResolvedVariant> calculateOwnArtifacts() {
        VariantArtifactResolveState variantState = variant.prepareForArtifactResolution();

        if (artifacts.isEmpty()) {
            ComponentArtifactResolveMetadata componentMetadata = component.prepareForArtifactResolution().getResolveMetadata();

            ImmutableSet.Builder<ResolvedVariant> ownArtifacts = ImmutableSet.builder();
            visitResolvedArtifacts(componentMetadata, variantState, ownArtifacts::add, exclusions.mayExcludeArtifacts());
            return ownArtifacts.build();
        } else {
            return ImmutableSet.of(variantState.resolveAdhocVariant(variantResolver, artifacts));
        }
    }

    /**
     * Gets all artifact variants ("sub-variants") for the component. This is used when
     * artifact view variant-reselection is enabled.
     *
     * TODO: Currently, this contains all variants in the entire component,
     * however in practice when using withVariantReselection the user likely
     * does not want to select from variants with a different capability than
     * the current variant.
     */
    private ImmutableSet<ResolvedVariant> getComponentVariants() {
        ComponentArtifactResolveState componentState = component.prepareForArtifactResolution();
        List<VariantArtifactResolveState> componentVariants = componentState.getVariantsForArtifactSelection().orElse(null);

        if (componentVariants == null) {
            return ownArtifacts.get();
        }

        boolean applyExclusions = exclusions.mayExcludeArtifacts();
        ImmutableSet.Builder<ResolvedVariant> builder = ImmutableSet.builder();
        ComponentArtifactResolveMetadata componentMetadata = componentState.getResolveMetadata();

        for (VariantArtifactResolveState componentVariant : componentVariants) {
            visitResolvedArtifacts(componentMetadata, componentVariant, builder::add, applyExclusions);
        }

        return builder.build();
    }

    private void visitResolvedArtifacts(
        ComponentArtifactResolveMetadata component,
        VariantArtifactResolveState variant,
        Consumer<ResolvedVariant> visitor,
        boolean applyExclusions
    ) {
        if (applyExclusions) {
            for (VariantResolveMetadata subvariant : variant.getArtifactVariants()) {
                visitor.accept(variantResolver.resolveVariant(component, subvariant, exclusions));
            }
        } else {
            for (VariantResolveMetadata subvariant : variant.getArtifactVariants()) {
                visitor.accept(variantResolver.resolveVariant(component, subvariant));
            }
        }
    }
}
