/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructureBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Dependency graph visitor that will build a {@link ResolutionResult} eagerly.
 * It is designed to be used during resolution for build dependencies.
 */
public class InMemoryResolutionResultBuilder implements DependencyGraphVisitor {

    private final LongSet visitedComponents = new LongOpenHashSet();
    private final GraphStructureBuilder builder = new GraphStructureBuilder();

    private @Nullable ImmutableAttributes requestAttributes;

    @Override
    public void start(RootGraphNode root) {
        this.requestAttributes = root.getResolveState().getAttributes();
        builder.start(root.getNodeId());
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        DependencyGraphComponent component = node.getOwner();
        if (visitedComponents.add(component.getResultId())) {
            builder.addComponent(
                component.getResultId(),
                component.getSelectionReason(),
                component.getRepositoryName(),
                component.getComponentId(),
                component.getModuleVersion()
            );
        }

        ResolvedGraphVariant externalVariant = node.getExternalVariant();
        long externalVariantId = externalVariant != null ? externalVariant.getNodeId() : -1;

        builder.addNode(
            node.getNodeId(),
            component.getResultId(),
            node.getMetadata().getAttributes(),
            node.getMetadata().getCapabilities(),
            node.getMetadata().getName(),
            externalVariantId
        );

        for (DependencyGraphEdge edge : node.getOutgoingEdges()) {
            ModuleVersionResolveException failure = edge.getFailure();
            if (failure == null) {
                List<? extends DependencyGraphNode> targetNodes = edge.getTargetNodes();
                if (targetNodes.isEmpty()) {
                    throw new IllegalStateException("Edge " + edge + " has no target nodes.");
                }
                if (edge.isConstraint()) {
                    // Only write the first target node for constraints, as this is historical
                    // behavior. Eventually, we should model constraints differently in the public
                    // API so they do not report a target node at all, as constraints conceptually
                    // only target components.
                    DependencyGraphNode firstTargetNode = targetNodes.get(0);
                    if (!firstTargetNode.getComponent().getModule().isVirtualPlatform()) {
                        builder.addSuccessfulEdge(
                            edge.getRequested(),
                            true,
                            firstTargetNode.getNodeId()
                        );
                    }
                } else {
                    for (DependencyGraphNode targetNode : targetNodes) {
                        if (!targetNode.getComponent().getModule().isVirtualPlatform()) {
                            builder.addSuccessfulEdge(
                                edge.getRequested(),
                                false,
                                targetNode.getNodeId()
                            );
                        }
                    }
                }
            } else {
                builder.addFailedEdge(
                    edge.getRequested(),
                    edge.isConstraint(),
                    edge.getReason(),
                    failure
                );
            }
        }
    }

    public ResolvedDependencyGraph getResolvedDependencyGraph() {
        if (requestAttributes == null) {
            throw new IllegalStateException("Resolution result not computed yet");
        }

        GraphStructure structure = builder.build();
        return new ResolvedDependencyGraph(
            requestAttributes,
            () -> structure,
            null
        );
    }

}
