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
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import java.util.Collections;
import java.util.HashSet;
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
        boolean hasTransitiveIncomingEdge = visitNonFileEdges(node);

        if (node.isRoot() || hasTransitiveIncomingEdge) {
            // Since file dependencies are not modeled as actual edges, we need to verify
            // there are edges to this node that would follow this file dependency.
            for (LocalFileDependencyMetadata fileDependency : node.getOutgoingFileEdges()) {
                int id = nextId++;
                artifactResults.visitArtifacts(node, id, new FileDependencyArtifactSet(fileDependency, node.getId(), artifactTypeRegistry, calculatedValueContainerFactory));
            }
        }
    }

    /**
     * Visit all the non-file edges for the given node.
     *
     * @return true if there is a transitive incoming edge.
     */
    private boolean visitNonFileEdges(DependencyGraphNode node) {
        boolean contributesArtifacts = false;
        boolean hasTransitiveIncomingEdge = false;
        ImmutableAttributes attributes = ImmutableAttributes.EMPTY;
        Set<CapabilitySelector> capabilitySelectors = Collections.emptySet();

        for (DependencyGraphEdge edge : node.getIncomingEdges()) {
            hasTransitiveIncomingEdge |= edge.isTransitive();

            if (edge.contributesArtifacts()) {
                contributesArtifacts = true;
                artifactResults.visitEdge(edge.getFrom(), node);

                // TODO: Should be safeConcat, but safeConcat currently fails when a Named attribute
                // and its desugared String attribute are on different edges, but with the same String value.
                attributes = attributesFactory.concat(attributes, edge.getAttributes());

                Set<CapabilitySelector> edgeCapabilitySelectors = edge.getDependencyMetadata().getSelector().getCapabilitySelectors();
                if (!edgeCapabilitySelectors.isEmpty()) {
                    if (capabilitySelectors == Collections.EMPTY_SET) {
                        capabilitySelectors = new HashSet<>(edgeCapabilitySelectors);
                    } else {
                        capabilitySelectors.addAll(edgeCapabilitySelectors);
                    }
                }
            }
        }

        if (contributesArtifacts) {
            int artifactSetId = nextId++;
            ArtifactSet artifactSet = getArtifactSetForNode(node, attributes, ImmutableSet.copyOf(capabilitySelectors));
            artifactResults.visitArtifacts(node, artifactSetId, artifactSet);
        }

        return hasTransitiveIncomingEdge;
    }

    private ArtifactSet getArtifactSetForNode(
        DependencyGraphNode node,
        ImmutableAttributes attributes,
        ImmutableSet<CapabilitySelector> capabilitySelectors
    ) {
        ExcludeSpec excludes = node.getAllExcludes();

        if (attributes.isEmpty() && capabilitySelectors.isEmpty() && !excludes.mayExcludeArtifacts()) {
            // If the edges do not modify the artifacts, we can use the cached artifact set.
            return artifactSetCache.getImplicitVariant(node.getOwner().getResolveState(), node.getResolveState());
        } else {
            // Otherwise, we need to create a new artifact set for this node.
            ComponentGraphResolveState component = node.getOwner().getResolveState();
            VariantGraphResolveState variant = node.getResolveState();

            return new VariantResolvingArtifactSet(
                component,
                variant,
                attributes,
                excludes,
                capabilitySelectors
            );
        }
    }

    @Override
    public void finish(RootGraphNode root) {
        artifactResults.finishArtifacts(root);
    }
}
