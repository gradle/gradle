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

import org.gradle.api.artifacts.component.ComponentSelector;

/**
 * Default implementation of {@link ResolvedArtifactEdge}.
 */
public class DefaultResolvedArtifactEdge implements ResolvedArtifactEdge {

    private final int targetNodeIndex;
    private final ComponentSelector requested;
    private final ArtifactGraphInternal graph;

    private ArtifactNode targetNode;

    public DefaultResolvedArtifactEdge(
        int targetNodeIndex,
        ComponentSelector requested,
        ArtifactGraphInternal graph
    ) {
        this.targetNodeIndex = targetNodeIndex;
        this.requested = requested;
        this.graph = graph;
    }

    @Override
    public ArtifactNode getTargetNode() {
        if (targetNode == null) {
            targetNode = graph.getNode(targetNodeIndex);
        }
        return targetNode;
    }

    @Override
    public ComponentSelector getRequested() {
        return requested;
    }

    @Override
    public String toString() {
        return requested.getDisplayName() + " -> " + getTargetNode();
    }

}
