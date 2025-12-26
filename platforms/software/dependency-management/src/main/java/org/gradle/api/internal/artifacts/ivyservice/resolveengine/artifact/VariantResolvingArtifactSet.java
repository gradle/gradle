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
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.ResolvedVariantTransformer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.resolve.resolver.ExcludingVariantArtifactSet;
import org.gradle.internal.resolve.resolver.VariantArtifactResolver;

import javax.annotation.Nullable;
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
        }

        if (!requestedArtifacts.isEmpty()) {
            if (spec.getSelectFromAllVariants()) {
                // Variants with overridden artifacts cannot be reselected since
                // we do not know the "true" attributes of the requested artifact.
                return ResolvedArtifactSet.EMPTY;
            } else {
                ImmutableList<DefaultVariantMetadata> adhocArtifacts = getAdhocArtifacts(requestedArtifacts);
                return selectAndResolveArtifactSet(adhocArtifacts, consumerServices, spec);
            }
        }

        VariantGraphResolveState targetVariant;
        if (spec.getSelectFromAllVariants()) {
            // If reselection is enabled, choose a new graph variant to select artifacts from.
            try {
                targetVariant = reselectGraphVariant(spec.getRequestAttributes(), consumerServices);
            } catch (Exception e) {
                return new BrokenResolvedArtifactSet(e);
            }

            if (targetVariant == null) {
                if (spec.getAllowNoMatchingVariants()) {
                    return ResolvedArtifactSet.EMPTY;
                } else {
                    return new BrokenResolvedArtifactSet(consumerServices.getFailureHandler().noAvailableArtifactFailure(componentId, spec.getRequestAttributes()));
                }
            }
        } else {
            // Otherwise, we select artifacts from the variant in the graph that produced this artifact set.
            targetVariant = variant;
        }

        ImmutableList<? extends VariantResolveMetadata> artifactSets = targetVariant.prepareForArtifactResolution().getArtifactVariants();
        return selectAndResolveArtifactSet(artifactSets, consumerServices, spec);
    }

    /**
     * The user requested artifacts on the dependency, skipping normal artifact resolution.
     * Resolve those artifacts directly from the component.
     */
    private ImmutableList<DefaultVariantMetadata> getAdhocArtifacts(List<IvyArtifactName> requestedArtifacts) {
        ComponentIdentifier componentId = component.getId();
        ImmutableList<ComponentArtifactMetadata> adhocArtifacts = variant.prepareForArtifactResolution().getAdhocArtifacts(requestedArtifacts);
        ExplicitArtifactsId artifactSetId = new ExplicitArtifactsId(componentId, getArtifactIds(adhocArtifacts));
        String name = componentId.getDisplayName() + " artifacts " + adhocArtifacts;
        ImmutableAttributes attributes = component.prepareForArtifactResolution().getArtifactMetadata().getAttributes();

        return ImmutableList.of(new DefaultVariantMetadata(
            name,
            artifactSetId,
            Describables.of(name),
            attributes,
            adhocArtifacts,
            ImmutableCapabilities.EMPTY
        ));
    }

    private static ImmutableList<ComponentArtifactIdentifier> getArtifactIds(ImmutableList<ComponentArtifactMetadata> adhocArtifacts) {
        ImmutableList.Builder<ComponentArtifactIdentifier> allArtifactIds = ImmutableList.builderWithExpectedSize(adhocArtifacts.size());
        for (ComponentArtifactMetadata artifact : adhocArtifacts) {
            allArtifactIds.add(artifact.getId());
        }
        return allArtifactIds.build();
    }

    /**
     * Given a set of request attributes, select a new variant from the component to be
     * used for artifact selection.
     */
    private @Nullable VariantGraphResolveState reselectGraphVariant(
        ImmutableAttributes requestAttributes,
        ArtifactSelectionServices artifactSelectionServices
    ) {
        return artifactSelectionServices.getGraphVariantSelector().selectByAttributeMatchingLenient(
            requestAttributes,
            capabilitySelectors,
            component,
            artifactSelectionServices.getConsumerSchema(),
            Collections.emptyList()
        );
    }

    /**
     * Given a set of artifact sets to select from, choose one and resolve it.
     */
    private ResolvedArtifactSet selectAndResolveArtifactSet(
        ImmutableList<? extends VariantResolveMetadata> artifactSetMetadata,
        ArtifactSelectionServices consumerServices,
        ArtifactSelectionSpec spec
    ) {
        VariantIdentifier sourceVariantId = variant.getMetadata().getId();

        // TODO: We should only resolve the selected artifact set instead of resolving all artifact sets.
        // We cannot do this now, as the artifact type registry is applied during artifact set resolution,
        // and the attributes from the registry are required during artifact selection.
        ImmutableList<ResolvedVariant> resolved = resolveArtifactSets(artifactSetMetadata, sourceVariantId, consumerServices);

        ArtifactVariantSelector artifactVariantSelector = consumerServices.getArtifactVariantSelector();
        ResolvedVariantTransformer resolvedVariantTransformer = consumerServices.getResolvedVariantTransformer();

        ImmutableAttributesSchema producerSchema = component.getMetadata().getAttributesSchema();
        ResolvedVariantSet variantSet = new DefaultResolvedVariantSet(component.getId(), producerSchema, overriddenAttributes, resolved, resolvedVariantTransformer);
        return artifactVariantSelector.select(variantSet, spec.getRequestAttributes(), spec.getAllowNoMatchingVariants());
    }

    /**
     * Given a set of artifact sets, resolve those artifact sets and apply any
     * requested exclusions to them.
     */
    private ImmutableList<ResolvedVariant> resolveArtifactSets(
        ImmutableList<? extends VariantResolveMetadata> artifactSetMetadata,
        VariantIdentifier sourceVariantId,
        ArtifactSelectionServices consumerServices
    ) {
        ComponentArtifactResolveMetadata componentMetadata = component.prepareForArtifactResolution().getArtifactMetadata();
        VariantArtifactResolver variantArtifactResolver = consumerServices.getVariantArtifactResolver();
        boolean applyExclusions = exclusions.mayExcludeArtifacts();

        ImmutableList.Builder<ResolvedVariant> result = ImmutableList.builderWithExpectedSize(artifactSetMetadata.size());
        ModuleIdentifier moduleId = componentMetadata.getModuleVersionId().getModule();
        for (VariantResolveMetadata artifactSet : artifactSetMetadata) {
            ResolvedVariant resolved = variantArtifactResolver.resolveVariantArtifactSet(componentMetadata, sourceVariantId, artifactSet);
            if (!applyExclusions) {
                result.add(resolved);
            } else {
                result.add(new ExcludingVariantArtifactSet(resolved, moduleId, exclusions));
            }
        }
        return result.build();
    }

}
