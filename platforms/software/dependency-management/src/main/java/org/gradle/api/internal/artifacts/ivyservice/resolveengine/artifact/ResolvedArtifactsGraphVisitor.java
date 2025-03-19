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

import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.model.CalculatedValueContainerFactory;

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

    // State
    private int nextId;

    public ResolvedArtifactsGraphVisitor(
        DependencyArtifactsVisitor artifactsBuilder,
        ImmutableArtifactTypeRegistry artifactTypeRegistry,
        VariantArtifactSetCache artifactSetCache,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.artifactResults = artifactsBuilder;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.artifactSetCache = artifactSetCache;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
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
                artifactResults.visitArtifacts(node, fileDependency, id, new FileDependencyArtifactSet(fileDependency, artifactTypeRegistry, calculatedValueContainerFactory));
            }
        }
    }

    /**
     * Visit all the non-file edges for the given node.
     *
     * @return true if there is a transitive incoming edge.
     */
    private boolean visitNonFileEdges(DependencyGraphNode node) {
        boolean hasTransitiveIncomingEdge = false;

        int implicitArtifactSetId = -1;
        ArtifactSet implicitArtifactSet = null;

        for (DependencyGraphEdge edge : node.getIncomingEdges()) {
            hasTransitiveIncomingEdge |= edge.isTransitive();

            if (edge.contributesArtifacts()) {
                if (maybeVisitAdhocEdge(node, edge)) {
                    // The artifacts for this node were modified by the dependency.
                    // Do not use the implicit artifact set.
                    continue;
                }

                // Since the dependency does not modify the artifacts, we can use the same
                // artifact set as other dependencies that do not modify the artifacts. We call
                // this the implicit artifact set.
                if (implicitArtifactSet == null) {
                    // We have not visited the implicit artifacts yet.
                    implicitArtifactSetId = nextId++;
                    implicitArtifactSet = artifactSetCache.getImplicitVariant(node.getOwner().getResolveState(), node.getResolveState());
                }

                artifactResults.visitArtifacts(edge.getFrom(), node, implicitArtifactSetId, implicitArtifactSet);
            }
        }

        return hasTransitiveIncomingEdge;
    }

    /**
     * Process the given edge, and if it modifies the artifacts, visit the artifacts.
     *
     * @return true if this edge modifies the artifacts, meaning it is adhoc, and should
     * not also contribute to the implicit artifact set.
     */
    private boolean maybeVisitAdhocEdge(DependencyGraphNode node, DependencyGraphEdge dependency) {
        ComponentGraphResolveState component = node.getOwner().getResolveState();
        VariantGraphResolveState variant = node.getResolveState();

        ImmutableAttributes attributes = dependency.getAttributes();
        List<IvyArtifactName> artifacts = dependency.getDependencyMetadata().getArtifacts();
        ExcludeSpec exclusions = dependency.getExclusions();
        Set<CapabilitySelector> capabilitySelectors = dependency.getDependencyMetadata().getSelector().getCapabilitySelectors();

        // If all dependency modifiers are empty, this edge does not produce an adhoc artifact set.
        if (artifacts.isEmpty() &&
            attributes.isEmpty() &&
            capabilitySelectors.isEmpty() &&
            !exclusions.mayExcludeArtifacts()
        ) {
            return false;
        }

        int id = nextId++;
        VariantResolvingArtifactSet artifactSet = new VariantResolvingArtifactSet(component, variant, attributes, artifacts, exclusions, capabilitySelectors);
        artifactResults.visitArtifacts(dependency.getFrom(), node, id, artifactSet);

        return true;
    }

    @Override
    public void finish(RootGraphNode root) {
        artifactResults.finishArtifacts(root);
    }
}
