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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.ResolvedVariantTransformer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantArtifactResolveState;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.resolve.resolver.ExcludingVariantArtifactSet;
import org.gradle.internal.resolve.resolver.VariantArtifactResolver;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * An {@link ArtifactSet} representing the artifacts contributed by a single variant in a dependency
 * graph, in the context of the dependency referencing it.
 */
public class VariantResolvingArtifactSet implements ArtifactSet {

    private final ComponentGraphResolveState component;
    private final VariantGraphResolveState variant;
    private final ImmutableAttributes overriddenAttributes;

    // TODO: Create a separate implementation of ArtifactSet for when !requestedArtifacts.isEmpty()
    private final List<IvyArtifactName> requestedArtifacts;

    private final ExcludeSpec exclusions;
    private final Set<CapabilitySelector> capabilitySelectors;

    public VariantResolvingArtifactSet(
        ComponentGraphResolveState component,
        VariantGraphResolveState variant,
        ImmutableAttributes overriddenAttributes,
        List<IvyArtifactName> requestedArtifacts,
        ExcludeSpec exclusions,
        Set<CapabilitySelector> capabilitySelectors
    ) {
        this.component = component;
        this.variant = variant;
        this.overriddenAttributes = overriddenAttributes;
        this.requestedArtifacts = requestedArtifacts;
        this.exclusions = exclusions;
        this.capabilitySelectors = capabilitySelectors;
    }

    @Override
    public ResolvedArtifactSet select(
        ArtifactSelectionServices consumerServices,
        ArtifactSelectionSpec spec
    ) {
        ComponentIdentifier componentId = component.getId();
        if (!spec.getComponentFilter().isSatisfiedBy(componentId)) {
            return ResolvedArtifactSet.EMPTY;
        } else {

            if (spec.getSelectFromAllVariants() && !requestedArtifacts.isEmpty()) {
                // Variants with overridden artifacts cannot be reselected since
                // we do not know the "true" attributes of the requested artifact.
                return ResolvedArtifactSet.EMPTY;
            }

            ImmutableList<ResolvedVariant> variants;
            try {
                if (!spec.getSelectFromAllVariants()) {
                    variants = getOwnArtifacts(consumerServices);
                } else {
                    variants = getArtifactVariantsForReselection(spec.getRequestAttributes(), consumerServices);
                }
            } catch (Exception e) {
                return new BrokenResolvedArtifactSet(e);
            }

            if (variants.isEmpty() && spec.getAllowNoMatchingVariants()) {
                return ResolvedArtifactSet.EMPTY;
            }

            ArtifactVariantSelector artifactVariantSelector = consumerServices.getArtifactVariantSelector();
            ResolvedVariantTransformer resolvedVariantTransformer = consumerServices.getResolvedVariantTransformer();

            ImmutableAttributesSchema producerSchema = component.getMetadata().getAttributesSchema();
            ResolvedVariantSet variantSet = new DefaultResolvedVariantSet(componentId, producerSchema, overriddenAttributes, variants, resolvedVariantTransformer);
            return artifactVariantSelector.select(variantSet, spec.getRequestAttributes(), spec.getAllowNoMatchingVariants());
        }
    }

    /**
     * Get all artifact sets corresponding to the graph node that this artifact set is derived from.
     */
    public ImmutableList<ResolvedVariant> getOwnArtifacts(ArtifactSelectionServices artifactSelectionServices) {
        VariantArtifactResolver variantArtifactResolver = artifactSelectionServices.getVariantArtifactResolver();
        if (requestedArtifacts.isEmpty()) {
            return getArtifactsForGraphVariant(variant, variantArtifactResolver);
        }

        // The user requested artifacts on the dependency.
        // Resolve an adhoc variant with those artifacts.
        ComponentArtifactResolveMetadata componentArtifactMetadata = component.prepareForArtifactResolution().getArtifactMetadata();
        VariantArtifactResolveState artifactState = variant.prepareForArtifactResolution();
        ImmutableList<ComponentArtifactMetadata> adhocArtifacts = artifactState.getAdhocArtifacts(requestedArtifacts);
        return ImmutableList.of(variantArtifactResolver.resolveAdhocVariant(componentArtifactMetadata, adhocArtifacts));
    }

    /**
     * Gets all artifact variants that should be considered for artifact selection.
     *
     * <p>This emulates the normal variant selection process where graph variants are first
     * considered, then artifact variants. We first consider graph variants, which leverages the
     * same algorithm used during graph variant selection. This considers requested and declared
     * capabilities.</p>
     */
    private ImmutableList<ResolvedVariant> getArtifactVariantsForReselection(
        ImmutableAttributes requestAttributes,
        ArtifactSelectionServices artifactSelectionServices
    ) {
        // First, find the graph variant containing the artifact variants to select among.
        VariantGraphResolveState graphVariant = artifactSelectionServices.getGraphVariantSelector().selectByAttributeMatchingLenient(
            requestAttributes,
            capabilitySelectors,
            component,
            artifactSelectionServices.getConsumerSchema(),
            Collections.emptyList()
        );

        // It is fine if no graph variants satisfy our request.
        // Variant reselection allows no target variants to be found.
        if (graphVariant == null) {
            return ImmutableList.of();
        }

        // Next, return all artifact variants for the selected graph variant.
        return getArtifactsForGraphVariant(graphVariant, artifactSelectionServices.getVariantArtifactResolver());
    }

    /**
     * Resolve all artifact variants for the given graph variant.
     */
    private ImmutableList<ResolvedVariant> getArtifactsForGraphVariant(
        VariantGraphResolveState graphVariant,
        VariantArtifactResolver variantArtifactResolver
    ) {
        ComponentArtifactResolveMetadata componentArtifacts = component.prepareForArtifactResolution().getArtifactMetadata();
        ImmutableList<ResolvedVariant> variantArtifactSets = resolveVariantArtifactSets(graphVariant, componentArtifacts, variantArtifactResolver);

        // Only apply exclusions to the resolved variant artifact sets if necessary.
        if (!exclusions.mayExcludeArtifacts()) {
            return variantArtifactSets;
        }

        ImmutableList.Builder<ResolvedVariant> excluded = ImmutableList.builderWithExpectedSize(variantArtifactSets.size());
        ModuleIdentifier moduleId = componentArtifacts.getModuleVersionId().getModule();
        for (ResolvedVariant artifactSet : variantArtifactSets) {
            excluded.add(new ExcludingVariantArtifactSet(artifactSet, moduleId, exclusions));
        }
        return excluded.build();
    }

    /**
     * Resolves all artifact sets for the given graph variant.
     */
    private static ImmutableList<ResolvedVariant> resolveVariantArtifactSets(
        VariantGraphResolveState variant,
        ComponentArtifactResolveMetadata component,
        VariantArtifactResolver variantArtifactResolver
    ) {
        Set<? extends VariantResolveMetadata> unresolved = variant.prepareForArtifactResolution().getArtifactVariants();
        ImmutableList.Builder<ResolvedVariant> resolved = ImmutableList.builderWithExpectedSize(unresolved.size());

        for (VariantResolveMetadata artifactSet : unresolved) {
            ResolvedVariant resolvedArtifactSet = variantArtifactResolver.resolveVariantArtifactSet(component, artifactSet);
            resolved.add(resolvedArtifactSet);
        }

        return resolved.build();
    }

}
