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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Adapts a {@link DependencyArtifactsVisitor} to a {@link DependencyGraphVisitor}. Calculates the artifacts contributed by each edge in the graph and forwards the results to the artifact visitor.
 */
public class ResolvedArtifactsGraphVisitor implements DependencyGraphVisitor {

    private final DependencyArtifactsVisitor artifactResults;
    private final ImmutableArtifactTypeRegistry artifactTypeRegistry;
    private final VariantArtifactSetCache artifactSetCache;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final AttributesFactory attributesFactory;

    // State
    private int nextId;

    public ResolvedArtifactsGraphVisitor(
        DependencyArtifactsVisitor artifactsBuilder,
        ImmutableArtifactTypeRegistry artifactTypeRegistry,
        VariantArtifactSetCache artifactSetCache,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        AttributesFactory attributesFactory
    ) {
        this.artifactResults = artifactsBuilder;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.artifactSetCache = artifactSetCache;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        artifactResults.visitNode(node);
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        ArtifactSet artifactSet = getNodeArtifacts(node);
        if (artifactSet != null) {
            int id = nextId++;
            artifactResults.visitArtifacts(node, id, artifactSet);
        }

        if (node.isRoot() || hasTransitiveIncomingEdges(node)) {
            // Since file dependencies are not modeled as actual edges, we need to verify
            // there are edges to this node that would follow this file dependency.
            for (LocalFileDependencyMetadata fileDependency : node.getOutgoingFileEdges()) {
                int id = nextId++;
                artifactResults.visitArtifacts(node, id, new FileDependencyArtifactSet(fileDependency, node.getId(), artifactTypeRegistry, calculatedValueContainerFactory));
            }
        }
    }

    boolean hasTransitiveIncomingEdges(DependencyGraphNode node) {
        for (DependencyGraphEdge edge : node.getIncomingEdges()) {
            if (edge.isTransitive()) {
                return true;
            }
        }
        return false;
    }

    private @Nullable ArtifactSet getNodeArtifacts(DependencyGraphNode node) {
        boolean contributesArtifacts = false;
        boolean requiresVariantArtifacts = false;
        ImmutableList<IvyArtifactName> artifacts = ImmutableList.of();
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        ImmutableSet<CapabilitySelector> capabilitySelectors = ImmutableSet.of();

        for (DependencyGraphEdge edge : node.getIncomingEdges()) {
            if (edge.contributesArtifacts()) {
                contributesArtifacts = true;
                artifactResults.visitEdge(edge.getFrom(), node);

                List<IvyArtifactName> edgeArtifacts = edge.getDependencyMetadata().getArtifacts();
                if (!edgeArtifacts.isEmpty()) {
                    artifacts = ImmutableList.<IvyArtifactName>builderWithExpectedSize(artifacts.size() + edgeArtifacts.size())
                        .addAll(artifacts)
                        .addAll(edgeArtifacts)
                        .build();
                } else {
                    requiresVariantArtifacts = true;
                }

                // TODO: Should be safeConcat, but safeConcat currently fails when a Named attribute
                // and its desugared String attribute are on different edges, but with the same String value.
                attributes = attributesFactory.concat(attributes, edge.getAttributes());

                Set<CapabilitySelector> edgeCapabilitySelectors = edge.getDependencyMetadata().getSelector().getCapabilitySelectors();
                if (!edgeCapabilitySelectors.isEmpty()) {
                    capabilitySelectors = ImmutableSet.<CapabilitySelector>builderWithExpectedSize(capabilitySelectors.size() + edgeCapabilitySelectors.size())
                        .addAll(capabilitySelectors)
                        .addAll(edgeCapabilitySelectors)
                        .build();
                }
            }
        }

        if (!contributesArtifacts) {
            return null;
        }

        ComponentGraphResolveState component = node.getOwner().getResolveState();
        VariantGraphResolveState variant = node.getResolveState();
        ExcludeSpec exclusions = node.getAllExcludes();

        ArtifactSet variantResult = null;
        if (requiresVariantArtifacts) {
            variantResult = doGetNodeArtifacts(attributes, capabilitySelectors, component, variant, exclusions);
        }

        ArtifactSet adhocArtifactsResult = null;
        if (!artifacts.isEmpty()) {
            adhocArtifactsResult = new VariantResolvingArtifactSet(
                component,
                variant,
                attributes,
                artifacts,
                exclusions,
                capabilitySelectors
            );
        }

        return mergeArtifactSets(variantResult, adhocArtifactsResult);
    }

    public static ArtifactSet mergeArtifactSets(
        @Nullable ArtifactSet first,
        @Nullable ArtifactSet second
    ) {
        assert first != null || second != null;

        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }

        return new CompositeArtifactSet(ImmutableList.of(first, second));
    }

    private ArtifactSet doGetNodeArtifacts(
        ImmutableAttributes attributes,
        ImmutableSet<CapabilitySelector> capabilitySelectors,
        ComponentGraphResolveState component,
        VariantGraphResolveState variant,
        ExcludeSpec exclusions
    ) {
        if (attributes.isEmpty() &&
            capabilitySelectors.isEmpty() &&
            !exclusions.mayExcludeArtifacts()
        ) {
            // If none of the edges modify the artifacts, we can use the cached implicit artifact set.
            return artifactSetCache.getImplicitVariant(component, variant);
        } else {
            // Otherwise, we need to create a custom artifact set for this node.
            return new VariantResolvingArtifactSet(
                component,
                variant,
                attributes,
                ImmutableList.of(),
                exclusions,
                capabilitySelectors
            );
        }
    }

    @Override
    public void finish(RootGraphNode root) {
        artifactResults.finishArtifacts(root);
    }
}
