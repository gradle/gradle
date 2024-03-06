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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
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
            try {
                if (spec.getVariantReselectionSpec() != null) {
                    return reselectVariants(variantSelector, spec, spec.getVariantReselectionSpec());
                }

                ImmutableSet<ResolvedVariant> artifactVariants = ownArtifacts.get();
                return selectMatchingArtifactVariant(variantSelector, spec, artifactVariants);
            } catch (Exception e) {
                return new BrokenResolvedArtifactSet(e);
            }
        }
    }

    /**
     * Perform graph variant reselection, optionally selecting a single graph variant or "fanning-out"
     * to select multiple graph variants from all capabilities.
     */
    private ResolvedArtifactSet reselectVariants(
        ArtifactVariantSelector variantSelector,
        ArtifactSelectionSpec spec,
        ArtifactSelectionSpec.VariantReselectionSpec reselectionSpec
    ) {
        if (!artifacts.isEmpty()) {
            // Variants with overridden artifacts cannot be reselected since
            // we do not know the "true" attributes of the requested artifact.
            return ResolvedArtifactSet.EMPTY;
        }

        if (reselectionSpec.getSelectFromAllCapabilities()) {
            return reselectFromAllCapabilities(variantSelector, spec);
        }

        return reselectUsingRequestedCapabilities(variantSelector, spec);
    }

    /**
     * Perform artifact variant selection on a set of artifact variants.
     */
    private ResolvedArtifactSet selectMatchingArtifactVariant(
        ArtifactVariantSelector variantSelector,
        ArtifactSelectionSpec spec,
        ImmutableSet<ResolvedVariant> artifactVariants
    ) {
        ResolvedVariantSet variantSet = new DefaultResolvedVariantSet(componentId, producerSchema, overriddenAttributes, artifactVariants);
        return variantSelector.select(variantSet, spec.getRequestAttributes(), spec.getAllowNoMatchingVariants(), this::asTransformed);
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
     * Selects artifacts from all graph variants using the new attributes, regardless of their capabilities.
     */
    private ResolvedArtifactSet reselectFromAllCapabilities(ArtifactVariantSelector variantSelector, ArtifactSelectionSpec spec) {
        Collection<VariantGraphResolveState> graphVariants = graphVariantSelector.selectAllMatchingVariants(
            spec.getRequestAttributes(),
            component,
            consumerSchema
        );

        List<ResolvedArtifactSet> allArtifacts = new ArrayList<>(graphVariants.size());
        for (VariantGraphResolveState graphVariant : graphVariants) {
            allArtifacts.add(selectMatchingArtifactVariant(variantSelector, spec, getArtifactsForGraphVariant(graphVariant)));
        }
        return CompositeResolvedArtifactSet.of(allArtifacts);
    }

    /**
     * Selects artifacts from a single graph variant using the new attributes.
     */
    private ResolvedArtifactSet reselectUsingRequestedCapabilities(ArtifactVariantSelector variantSelector, ArtifactSelectionSpec spec) {
        VariantGraphResolveState graphVariant = selectNewGraphVariant(spec);
        if (graphVariant == null) {
            return ResolvedArtifactSet.EMPTY;
        }

        ImmutableSet<ResolvedVariant> artifactVariants = getArtifactsForGraphVariant(graphVariant);
        return selectMatchingArtifactVariant(variantSelector, spec, artifactVariants);
    }

    /**
     * Selects a graph variant in the same manner that graph variants are selected during
     * graph resolution.
     *
     * @return null if a graph variant could not be selected and the spec permits no matching variants.
     */
    @Nullable
    private VariantGraphResolveState selectNewGraphVariant(ArtifactSelectionSpec spec) {
        if (spec.getAllowNoMatchingVariants()) {
            return graphVariantSelector.selectByAttributeMatchingLenient(
                spec.getRequestAttributes(),
                capabilities,
                component,
                consumerSchema,
                Collections.emptyList()
            );
        }

        return graphVariantSelector.selectByAttributeMatching(
            spec.getRequestAttributes(),
            capabilities,
            component,
            consumerSchema,
            Collections.emptyList()
        );
    }

    /**
     * Resolve all artifact variants for the given graph variant.
     */
    private ImmutableSet<ResolvedVariant> getArtifactsForGraphVariant(VariantGraphResolveState graphVariant) {
        VariantArtifactResolveState variantState = graphVariant.prepareForArtifactResolution();
        Set<? extends VariantResolveMetadata> artifactVariants = variantState.getArtifactVariants();
        ImmutableSet.Builder<ResolvedVariant> resolved = ImmutableSet.builderWithExpectedSize(artifactVariants.size());

        ComponentArtifactResolveMetadata componentMetadata = component.prepareForArtifactResolution().getResolveMetadata();
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
