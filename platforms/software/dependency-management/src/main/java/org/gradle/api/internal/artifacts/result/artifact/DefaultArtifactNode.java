/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.result.artifact;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure;
import org.gradle.internal.component.model.VariantIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class DefaultArtifactNode implements ArtifactNode {

    private final int index;
    private final DefaultArtifactGraph graph;
    private final GraphStructure structure;

    private @Nullable ImmutableList<ArtifactEdge> edges;
    private @Nullable ArtifactCollection artifactCollection;

    public DefaultArtifactNode(
        int index,
        DefaultArtifactGraph graph,
        GraphStructure structure
    ) {
        this.index = index;
        this.graph = graph;
        this.structure = structure;
    }

    public int getNodeIndex() {
        return index;
    }

    @Override
    public VariantIdentifier getId() {
        return structure.nodes().id(index);
    }

    @Override
    public AttributeContainer getAttributes() {
        return structure.nodes().attributes(index);
    }

    @Override
    public Collection<? extends Capability> getCapabilities() {
        return structure.nodes().capabilities(index).asSet();
    }

    @Override
    public List<? extends ArtifactEdge> getDependencies() {
        if (edges == null) {
            GraphStructure.Edges edgeStructure = structure.edges();
            int start = edgeStructure.start(index);
            int end = edgeStructure.end(index);

            ImmutableList.Builder<ArtifactEdge> builder = ImmutableList.builderWithExpectedSize(end - start);
            for (int i = start; i < end; i++) {
                // We only expose non-constraint edges in this API.
                // Constraint edges are of little value when analyzing artifact relationships.
                if (!edgeStructure.constraint(i)) {
                    int targetNode = edgeStructure.targetNode(i);
                    if (targetNode == -1) {
                        GraphStructure.Edges.EdgeFailure failure = edgeStructure.failure(i);
                        builder.add(new DefaultUnresolvedArtifactEdge(edgeStructure.selector(i), failure.failure()));
                    } else {
                        builder.add(new DefaultResolvedArtifactEdge(targetNode, edgeStructure.selector(i), graph));
                    }
                }
            }

            edges = builder.build();
        }
        return edges;
    }

    @Override
    public ArtifactCollection getArtifacts() {
        if (artifactCollection == null) {
            artifactCollection = graph.artifactsFor(index);
        }
        return artifactCollection;
    }

    @Override
    public FileCollection getFiles() {
        return getArtifacts().getArtifactFiles();
    }

    @Override
    public String toString() {
        return getId().getDisplayName();
    }

}
