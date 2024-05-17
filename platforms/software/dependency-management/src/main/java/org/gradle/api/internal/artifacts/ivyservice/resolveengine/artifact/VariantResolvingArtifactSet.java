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
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolverFactory;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.artifacts.transform.VariantDefinition;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.resolve.resolver.VariantArtifactResolver;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * An {@link ArtifactSet} representing the artifacts contributed by a single variant in a dependency
 * graph, in the context of the dependency referencing it.
 */
public class VariantResolvingArtifactSet implements ArtifactSet {

    private final VariantArtifactResolver variantResolver;
    private final ComponentGraphResolveState component;
    private final VariantGraphResolveState variant;
    private final ComponentIdentifier componentId;
    private final AttributesSchemaInternal producerSchema;
    private final ImmutableAttributes overriddenAttributes;
    private final List<IvyArtifactName> artifacts;
    private final ExcludeSpec exclusions;
    private final List<Capability> capabilities;
    private final GraphVariantSelector graphVariantSelector;
    private final AttributesSchemaInternal consumerSchema;

    private final Lazy<ImmutableSet<ResolvedVariant>> ownArtifacts = Lazy.locking().of(this::calculateOwnArtifacts);

    public VariantResolvingArtifactSet(
        VariantArtifactResolver variantResolver,
        ComponentGraphResolveState component,
        VariantGraphResolveState variant,
        DependencyGraphEdge dependency,
        GraphVariantSelector graphVariantSelector,
        AttributesSchemaInternal consumerSchema
    ) {
        this.variantResolver = variantResolver;
        this.component = component;
        this.variant = variant;
        this.componentId = component.getId();
        this.producerSchema = component.getMetadata().getAttributesSchema();
        this.overriddenAttributes = dependency.getAttributes();
        this.artifacts = dependency.getDependencyMetadata().getArtifacts();
        this.exclusions = dependency.getExclusions();
        this.capabilities = dependency.getSelector().getRequested().getRequestedCapabilities();
        this.graphVariantSelector = graphVariantSelector;
        this.consumerSchema = consumerSchema;
    }

    @Override
    public ResolvedArtifactSet select(
        ArtifactVariantSelector variantSelector,
        ArtifactSelectionSpec spec
    ) {
        if (!spec.getComponentFilter().isSatisfiedBy(componentId)) {
            return ResolvedArtifactSet.EMPTY;
        } else {

            if (spec.getSelectFromAllVariants() && !artifacts.isEmpty()) {
                // Variants with overridden artifacts cannot be reselected since
                // we do not know the "true" attributes of the requested artifact.
                return ResolvedArtifactSet.EMPTY;
            }

            ImmutableSet<ResolvedVariant> variants;
            try {
                if (!spec.getSelectFromAllVariants()) {
                    variants = ownArtifacts.get();
                } else {
                    variants = getArtifactVariantsForReselection(spec.getRequestAttributes());
                }
            } catch (Exception e) {
                return new BrokenResolvedArtifactSet(e);
            }

            if (variants.isEmpty() && spec.getAllowNoMatchingVariants()) {
                return ResolvedArtifactSet.EMPTY;
            }

            ResolvedVariantSet variantSet = new DefaultResolvedVariantSet(componentId, producerSchema, overriddenAttributes, variants);
            return variantSelector.select(variantSet, spec.getRequestAttributes(), spec.getAllowNoMatchingVariants(), this::asTransformed);
        }
    }

    private ResolvedArtifactSet asTransformed(ResolvedVariant sourceVariant, VariantDefinition variantDefinition, TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory, TransformedVariantFactory transformedVariantFactory) {
        if (componentId instanceof ProjectComponentIdentifier) {
            return transformedVariantFactory.transformedProjectArtifacts(componentId, sourceVariant, variantDefinition, dependenciesResolverFactory);
        } else {
            return transformedVariantFactory.transformedExternalArtifacts(componentId, sourceVariant, variantDefinition, dependenciesResolverFactory);
        }
    }

    public ImmutableSet<ResolvedVariant> calculateOwnArtifacts() {
        if (artifacts.isEmpty()) {
            return getArtifactsForGraphVariant(variant);
        } else {
            return ImmutableSet.of(variant.prepareForArtifactResolution().resolveAdhocVariant(variantResolver, artifacts));
        }
    }

    /**
     * Gets all artifact variants that should be considered for artifact selection.
     *
     * <p>This emulates the normal variant selection process where graph variants are first
     * considered, then artifact variants. We first consider graph variants, which leverages the
     * same algorithm used during graph variant selection. This considers requested and declared
     * capabilities.</p>
     */
    private ImmutableSet<ResolvedVariant> getArtifactVariantsForReselection(ImmutableAttributes requestAttributes) {
        // First, find the graph variant containing the artifact variants to select among.
        VariantGraphResolveState graphVariant = graphVariantSelector.selectByAttributeMatchingLenient(
            requestAttributes,
            capabilities,
            component,
            consumerSchema,
            Collections.emptyList()
        );

        // It is fine if no graph variants satisfy our request.
        // Variant reselection allows no target variants to be found.
        if (graphVariant == null) {
            return ImmutableSet.of();
        }

        // Next, return all artifact variants for the selected graph variant.
        return getArtifactsForGraphVariant(graphVariant);
    }

    /**
     * Resolve all artifact variants for the given graph variant.
     */
    private ImmutableSet<ResolvedVariant> getArtifactsForGraphVariant(VariantGraphResolveState graphVariant) {
        VariantArtifactResolveState variantState = graphVariant.prepareForArtifactResolution();
        Set<? extends VariantResolveMetadata> artifactVariants = variantState.getArtifactVariants();
        ImmutableSet.Builder<ResolvedVariant> resolved = ImmutableSet.builderWithExpectedSize(artifactVariants.size());

        ComponentArtifactResolveMetadata componentMetadata = component.prepareForArtifactResolution().getArtifactMetadata();
        if (exclusions.mayExcludeArtifacts()) {
            for (VariantResolveMetadata artifactVariant : artifactVariants) {
                resolved.add(variantResolver.resolveVariant(componentMetadata, artifactVariant, exclusions));
            }
        } else {
            for (VariantResolveMetadata artifactVariant : artifactVariants) {
                resolved.add(variantResolver.resolveVariant(componentMetadata, artifactVariant));
            }
        }

        return resolved.build();
    }
}
